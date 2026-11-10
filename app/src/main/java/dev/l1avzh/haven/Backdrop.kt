package dev.l1avzh.haven

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.View

/**
 * The photographic backdrop (a bundled, offline landscape; credits in docs/assets/ASSET_CREDITS.md). It is decoded once at
 * 16-bit colour, painted into a hardware layer and only redrawn during a day/night cross-fade, so an idle home screen costs nothing.
 * The wallpaper colour tints it, a night wash darkens it, and three soft scrims sit where text lives: the header, the shelves
 * and the start side behind the character. There is no blur and nothing animates.
 */
class PhotoView(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val wash = Paint(); private val scrim = Paint()
    private var night = 1f
    /** The screensaver look: darker and calmer, with the scrim on the text side and no header scrim. */
    var saver = false
    private var anim: ValueAnimator? = null
    private var top = 0xFF070A12.toInt(); private var bottom = 0xFF141B33.toInt()
    private var washShader: LinearGradient? = null
    private var headShader: LinearGradient? = null; private var shelfShader: LinearGradient? = null; private var sideShader: LinearGradient? = null
    private val src = Rect(); private val dst = Rect()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; isFocusable = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
        Thread {
            val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val b = try { BitmapFactory.decodeResource(resources, R.drawable.bg_valley, o) } catch (_: Throwable) { null }
            post { bmp = b; invalidate() }
        }.start()
    }

    /** The wallpaper's two colours tint the photo (the plain black wallpaper turns the photo off). */
    fun setTint(topColor: Int, bottomColor: Int, photo: Boolean) {
        top = topColor; bottom = bottomColor; visibility = if (photo) VISIBLE else GONE
        washShader = null; invalidate()
    }

    fun setNight(isNight: Boolean, animate: Boolean) {
        val to = if (isNight) 1f else 0f
        anim?.cancel()
        if (!animate || T.motion <= 0f || !isAttachedToWindow || night == to) { night = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(night, to).apply { duration = T.d(1400L); addUpdateListener { night = it.animatedValue as Float; invalidate() }; start() }
    }

    private fun shader(w: Int, h: Int) {
        if (washShader != null && headShader != null) return
        washShader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        headShader = LinearGradient(0f, 0f, 0f, h * 0.24f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        shelfShader = LinearGradient(0f, h * 0.40f, 0f, h.toFloat(), 0x00000000, 0xB0000000.toInt(), Shader.TileMode.CLAMP)
        sideShader = LinearGradient(0f, 0f, w * 0.55f, 0f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { washShader = null; headShader = null }

    override fun onDraw(c: Canvas) {
        val w = width; val h = height
        val b = bmp ?: run { c.drawColor(top); return }
        shader(w, h)
        // centre-crop the photo to the screen
        val k = maxOf(w / b.width.toFloat(), h / b.height.toFloat())
        val sw = (w / k).toInt(); val sh = (h / k).toInt()
        src.set((b.width - sw) / 2, (b.height - sh) / 2, (b.width + sw) / 2, (b.height + sh) / 2); dst.set(0, 0, w, h)
        c.drawBitmap(b, src, dst, paint)
        // day keeps the photo bright and airy; night washes it down to the wallpaper colour
        wash.shader = washShader; wash.alpha = (255 * (if (saver) 0.55f + 0.33f * night else 0.30f + 0.52f * night)).toInt(); c.drawRect(dst, wash)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        if (!saver) { scrim.shader = headShader; scrim.alpha = 255; c.drawRect(0f, 0f, w.toFloat(), h * 0.24f, scrim) }
        scrim.shader = shelfShader; scrim.alpha = (255 * (0.75f + 0.25f * night)).toInt(); c.drawRect(0f, h * 0.40f, w.toFloat(), h.toFloat(), scrim)
        c.save(); if (rtl) { c.translate(w.toFloat(), 0f); c.scale(-1f, 1f) }
        scrim.shader = sideShader; scrim.alpha = 255; c.drawRect(0f, 0f, w * 0.55f, h.toFloat(), scrim); c.restore()
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
}
