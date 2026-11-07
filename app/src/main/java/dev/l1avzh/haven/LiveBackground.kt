package dev.l1avzh.haven

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live background: soft colour fields drifting on slow orbits plus a few rising motes. Colours come from the
 * focused app's accent; changing the app shifts the palette and gives the motion a short burst of energy.
 * Drawn with a handful of cached radial shaders at 30 fps, and paused whenever the view is not visible.
 */
class LiveBackground(ctx: Context) : View(ctx), Choreographer.FrameCallback {
    private class Blob(val fx: Float, val fy: Float, val px: Float, val py: Float, val r: Float, val ax: Float, val ay: Float, val hue: Float, val alpha: Int)

    private val blobs = listOf(
        Blob(0.11f, 0.17f, 0.0f, 1.3f, 0.62f, 0.34f, 0.26f, 0f, 0x88),
        Blob(0.15f, 0.09f, 2.1f, 0.4f, 0.55f, 0.40f, 0.30f, 38f, 0x70),
        Blob(0.08f, 0.13f, 4.0f, 2.7f, 0.50f, 0.36f, 0.34f, -42f, 0x68),
        Blob(0.19f, 0.07f, 5.2f, 3.9f, 0.40f, 0.28f, 0.22f, 12f, 0x50),
    )
    private val paints = blobs.map { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val mote = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private val motes = FloatArray(24 * 4) // x, phase, speed, size
    private var accent = T.ACCENT
    private var energy = 0f
    private var t = 0f
    private var last = 0L
    private var running = false
    private var static = false
    companion object {
        /** Weather mood shared by all backgrounds: rain, snow and storms fall across the whole screen. */
        @Volatile var ambient: Sky? = null
        fun ambientFor(ctx: Context, ids: List<String>): Sky? {
            if ("weather" !in ids) return null
            val c = Weather.cached(Prefs(ctx)) ?: return null
            return skyFor(c.second).takeIf { it == Sky.RAIN || it == Sky.SNOW || it == Sky.STORM }
        }
    }
    private val fall = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val hsv = FloatArray(3)
    private val LW = 192
    private val LH = 108
    private var lo: Bitmap? = null
    private var loCanvas: Canvas? = null
    private val src = Rect()
    private val dst = Rect()
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        var seed = 7
        for (i in motes.indices step 4) {
            seed = seed * 1103515245 + 12345
            val a = ((seed ushr 8) and 0xFFFF) / 65535f
            seed = seed * 1103515245 + 12345
            val b = ((seed ushr 8) and 0xFFFF) / 65535f
            motes[i] = a; motes[i + 1] = b; motes[i + 2] = 0.012f + 0.02f * a; motes[i + 3] = 1.4f + 3.2f * b
        }
    }

    fun setAccent(c: Int) { accent = c; rebuild(); invalidate() }

    /** Called when a different app takes focus. */
    fun pulse() { energy = 1f; ensureRunning() }

    fun setStatic(v: Boolean) { static = v; if (v) stop() else ensureRunning(); invalidate() }

    private fun shifted(h: Float): Int {
        Color.colorToHSV(accent, hsv)
        hsv[0] = (hsv[0] + h + 360f) % 360f
        hsv[1] = (hsv[1] * 0.95f).coerceIn(0.35f, 1f)
        hsv[2] = hsv[2].coerceIn(0.55f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun rebuild() {
        if (width == 0) return
        val m = maxOf(LW, LH).toFloat()
        blobs.forEachIndexed { i, b ->
            val c = shifted(b.hue)
            val rad = m * b.r
            paints[i].shader = RadialGradient(0f, 0f, rad, intArrayOf((c and 0xFFFFFF) or (b.alpha shl 24), (c and 0xFFFFFF) or ((b.alpha / 3) shl 24), c and 0xFFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        shade.shader = LinearGradient(0f, height * 0.55f, 0f, height.toFloat(), 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        lo?.recycle(); lo = Bitmap.createBitmap(LW, LH, Bitmap.Config.ARGB_8888); loCanvas = Canvas(lo!!)
        src.set(0, 0, LW, LH); dst.set(0, 0, w, h); rebuild()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val m = maxOf(w, h)
        val boost = 1f + 0.18f * energy
        val lc = loCanvas; val bmp = lo
        if (lc != null && bmp != null) {
            bmp.eraseColor(0)
            val lm = maxOf(LW, LH).toFloat()
            blobs.forEachIndexed { i, b ->
                val x = LW * (0.5f + b.ax * sin(t * b.fx * 2f * PI.toFloat() + b.px))
                val y = LH * (0.5f + b.ay * cos(t * b.fy * 2f * PI.toFloat() + b.py))
                lc.save(); lc.translate(x, y); lc.scale(boost, boost)
                lc.drawCircle(0f, 0f, lm * b.r, paints[i]); lc.restore()
            }
            c.drawBitmap(bmp, src, dst, blit)
        }
        val tint = shifted(0f)
        for (i in motes.indices step 4) {
            val prog = (motes[i + 1] + t * motes[i + 2]) % 1f
            val x = w * (motes[i] + 0.03f * sin(t * 0.4f + motes[i + 1] * 9f))
            val y = h * (1.05f - prog * 1.1f)
            val tw = sin(prog * PI.toFloat()).coerceIn(0f, 1f)
            mote.color = (tint and 0xFFFFFF) or (((26 + 70 * energy) * tw).toInt() shl 24)
            c.drawCircle(x, y, motes[i + 3] * resources.displayMetrics.density * 0.9f, mote)
        }
        c.drawRect(0f, 0f, w, h, shade)
        ambient?.let { sky -> weatherFall(c, w, h, sky) }
    }

    private fun weatherFall(c: Canvas, w: Float, h: Float, sky: Sky) {
        val snow = sky == Sky.SNOW
        val d = resources.displayMetrics.density
        fall.color = if (snow) 0xFFFFFFFF.toInt() else 0xFFBFD8FF.toInt()
        fall.strokeWidth = (if (snow) 3.2f else 1.6f) * d
        val n = if (snow) 46 else 70
        for (i in 0 until n) {
            val a = ((i * 7919) % 1000) / 1000f; val b = ((i * 104729) % 1000) / 1000f
            val speed = if (snow) 0.05f + 0.07f * b else 0.55f + 0.5f * b
            val ph = (a + t * speed) % 1f
            val x = w * (a + (if (snow) 0.02f * sin(t * 0.8f + i) else -0.04f * ph)) % w
            val y = h * ph
            fall.alpha = ((if (snow) 150 else 90) * (0.4f + 0.6f * b)).toInt()
            if (snow) c.drawCircle(x, y, 1.5f * d * (0.6f + b), fall) else c.drawLine(x, y, x - 6f * d, y + 26f * d * (0.5f + b), fall)
        }
        if (sky == Sky.STORM && sin(t * 0.9f) > 0.985f) { fall.alpha = 40; c.drawRect(0f, 0f, w, h, fall) }
    }

    private fun ensureRunning() {
        if (running || static || !T.animated || !isShown) return
        running = true; last = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(ns: Long) {
        if (!running) return
        if (last != 0L) {
            val dt = (ns - last) / 1e9f
            if (dt < 0.032f) { Choreographer.getInstance().postFrameCallback(this); return }
            t += dt.coerceAtMost(0.1f) * T.motion * (0.55f + 1.6f * energy)
            energy = (energy - dt * 0.7f).coerceAtLeast(0f)
            invalidate()
        }
        last = ns
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); ensureRunning() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(v: View, vis: Int) { super.onVisibilityChanged(v, vis); if (vis == VISIBLE) ensureRunning() else stop() }
    override fun onWindowVisibilityChanged(vis: Int) { super.onWindowVisibilityChanged(vis); if (vis == VISIBLE) ensureRunning() else stop() }
    override fun hasOverlappingRendering() = false
}
