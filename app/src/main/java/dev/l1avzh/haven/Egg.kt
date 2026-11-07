package dev.l1avzh.haven

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The hidden signature. Drawn by hand in physical coordinates so it never mirrors in RTL locales.
 * One time-driven draw loop: a light sweep reveals the name, then the credit settles in. No allocations per frame.
 */
class EggView(ctx: Context, private val title: String, private val credit: String, private val animated: Boolean) : View(ctx) {
    private val t0 = SystemClock.uptimeMillis()
    private var closed = false
    private val n = 64
    private val px = FloatArray(n); private val py = FloatArray(n); private val ps = FloatArray(n)
    private val pr = FloatArray(n); private val pp = FloatArray(n)
    private val bg = Paint(); private val glow = Paint(); private val beam = Paint()
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG); private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint()
    private val widths = FloatArray(16)

    init {
        val r = java.util.Random(1129)
        for (i in 0 until n) {
            px[i] = r.nextFloat(); py[i] = r.nextFloat(); ps[i] = 0.000012f + r.nextFloat() * 0.000035f
            pr[i] = 0.6f + r.nextFloat() * 2.2f; pp[i] = r.nextFloat() * 6.28f
        }
        big.typeface = Fonts.thin; big.color = Color.WHITE; big.textAlign = Paint.Align.LEFT
        small.typeface = Fonts.light; small.textAlign = Paint.Align.LEFT
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val u = w / 1920f
        bg.shader = RadialGradient(w / 2f, h * 0.46f, w * 0.75f, intArrayOf(0xFF0E1426.toInt(), 0xFF05070D.toInt(), 0xFF000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(w / 2f, h * 0.44f, w * 0.34f, intArrayOf(0x556C8CFF, 0x00000000), null, Shader.TileMode.CLAMP)
        beam.shader = LinearGradient(-150 * u, 0f, 150 * u, 0f, intArrayOf(0x00FFFFFF, 0x66B8C8FF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        big.textSize = 250 * u; small.textSize = 54 * u
        rule.shader = LinearGradient(w / 2f - 260 * u, 0f, w / 2f + 260 * u, 0f, intArrayOf(0x006C8CFF, 0xFF6C8CFF.toInt(), 0x006C8CFF), null, Shader.TileMode.CLAMP)
        small.shader = LinearGradient(w / 2f - 380 * u, 0f, w / 2f + 380 * u, 0f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFA9BBFF.toInt()), null, Shader.TileMode.CLAMP)
    }

    private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) * (1f - c) }
    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val u = w / 1920f
        val ms = if (animated) SystemClock.uptimeMillis() - t0 else 9000L
        val t = (ms / 6400f).coerceIn(0f, 1f)
        c.drawRect(0f, 0f, w, h, bg)

        // Drifting particles, fading in with the scene.
        val vis = smooth(ms / 1200f)
        for (i in 0 until n) {
            val y = ((py[i] - ms * ps[i]) % 1f + 1f) % 1f
            val x = px[i] + sin(ms * 0.0004f + pp[i]) * 0.012f
            val tw = 0.35f + 0.65f * abs(sin(ms * 0.0011f + pp[i] * 3f))
            dot.color = if (i % 5 == 0) 0xFFA9BBFF.toInt() else 0xFFFFFFFF.toInt()
            dot.alpha = (150 * tw * vis * (0.4f + 0.6f * y)).toInt()
            c.drawCircle(x * w, y * h, pr[i] * u * 1.4f, dot)
        }

        glow.alpha = (255 * smooth((t - 0.1f) / 0.3f) * (0.82f + 0.18f * sin(ms * 0.0018f))).toInt()
        c.drawRect(0f, 0f, w, h, glow)

        // Title: letters appear as the beam crosses them.
        val total = measureLetters(title, big)
        val baseY = h * 0.5f
        val sweep = ease((t - 0.08f) / 0.3f)
        val beamX = -150f * u + sweep * (w + 300f * u)
        var x = (w - total) / 2f
        for (i in title.indices) {
            val cx = x + widths[i] / 2f
            val a = if (t > 0.42f) 1f else smooth((beamX - cx) / (220f * u))
            big.alpha = (255 * a).toInt()
            val rise = (1f - a) * 28f * u
            c.drawText(title, i, i + 1, x, baseY + rise, big)
            x += widths[i] + 6f * u
        }
        if (t in 0.08f..0.46f) { c.save(); c.translate(beamX, 0f); c.drawRect(-150f * u, 0f, 150f * u, h, beam); c.restore() }

        // Accent rule growing from the centre.
        val ruleT = smooth((t - 0.38f) / 0.2f)
        if (ruleT > 0f) { c.save(); c.scale(ruleT, 1f, w / 2f, 0f); rule.alpha = (255 * ruleT).toInt(); c.drawRect(w / 2f - 260f * u, baseY + 34f * u, w / 2f + 260f * u, baseY + 38f * u, rule); c.restore() }

        // Credit: tracking settles from wide to normal while it fades in.
        val ct = smooth((t - 0.5f) / 0.32f)
        if (ct > 0f) {
            small.letterSpacing = 0.55f - 0.43f * ct
            small.alpha = (255 * ct).toInt()
            val cw = small.measureText(credit)
            c.drawText(credit, (w - cw) / 2f, baseY + 130f * u + (1f - ct) * 14f * u, small)
        }
        if (animated && !closed) postInvalidateOnAnimation()
    }

    private fun measureLetters(s: String, p: Paint): Float {
        var sum = 0f
        for (i in s.indices) { widths[i] = p.measureText(s, i, i + 1); sum += widths[i] + 6f * (width / 1920f) }
        return sum - 6f * (width / 1920f)
    }

    fun close(onDone: () -> Unit) {
        if (closed) return
        closed = true
        animate().alpha(0f).setDuration(if (animated) 220 else 0).withEndAction(onDone).start()
    }
}
