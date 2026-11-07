package dev.l1avzh.haven

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

fun skyFor(code: Int): Sky = when (code) {
    0 -> Sky.CLEAR
    1, 2 -> Sky.PARTLY
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUDY
}

/** Small animated weather picture: turning sun, twinkling moon, drifting clouds, falling rain and snow, lightning. */
class WeatherIcon(ctx: Context, private val size: Int) : View(ctx) {
    var sky = Sky.CLEAR; var day = true; var hot = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun set(s: Sky, isDay: Boolean, isHot: Boolean) { sky = s; day = isDay; hot = isHot; invalidate() }

    override fun onMeasure(a: Int, b: Int) = setMeasuredDimension(size, size)
    override fun hasOverlappingRendering() = false

    private fun cloud(c: Canvas, dx: Float, dy: Float, tone: Int) {
        p.color = tone; p.style = Paint.Style.FILL
        c.drawCircle(38f + dx, 56f + dy, 15f, p); c.drawCircle(54f + dx, 46f + dy, 19f, p); c.drawCircle(70f + dx, 57f + dy, 14f, p)
        c.drawRoundRect(24f + dx, 56f + dy, 84f + dx, 72f + dy, 8f, 8f, p)
    }

    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        c.save(); c.scale(width / 100f, height / 100f)
        when (sky) {
            Sky.CLEAR -> if (day) sun(c, 50f, 50f, t, 1f) else moon(c, t)
            Sky.PARTLY -> { if (day) sun(c, 40f, 38f, t, 0.8f) else moon(c, t); cloud(c, 4f * sin(t * 0.6f), 8f, 0xFFE6EBF5.toInt()) }
            Sky.CLOUDY -> { cloud(c, -8f + 5f * sin(t * 0.5f), -4f, 0xFF9AA6BC.toInt()); cloud(c, 6f + 4f * cos(t * 0.4f), 8f, 0xFFE6EBF5.toInt()) }
            Sky.FOG -> { for (i in 0..3) { p.color = 0x88DDE4F0.toInt(); p.style = Paint.Style.FILL; c.drawRoundRect(14f + 8f * sin(t * 0.5f + i), 30f + i * 14f, 78f + 8f * sin(t * 0.5f + i), 38f + i * 14f, 4f, 4f, p) } }
            Sky.RAIN -> { cloud(c, 0f, -10f, 0xFFB4BFD3.toInt()); drops(c, t, 0xFF7FB6FF.toInt(), false) }
            Sky.SNOW -> { cloud(c, 0f, -10f, 0xFFD3DBEA.toInt()); drops(c, t, 0xFFFFFFFF.toInt(), true) }
            Sky.STORM -> {
                cloud(c, 0f, -10f, 0xFF7C879E.toInt()); drops(c, t, 0xFF7FB6FF.toInt(), false)
                val flash = (sin(t * 5f) > 0.9f)
                p.color = if (flash) 0xFFFFF3A0.toInt() else 0xFFFFD84D.toInt(); p.style = Paint.Style.FILL
                path.reset(); path.moveTo(54f, 52f); path.lineTo(42f, 74f); path.lineTo(51f, 74f); path.lineTo(45f, 94f); path.lineTo(64f, 68f); path.lineTo(54f, 68f); path.close(); c.drawPath(path, p)
            }
        }
        c.restore()
        if (isShown && T.animated) postInvalidateOnAnimation()
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, t: Float, k: Float) {
        val pulse = 1f + 0.06f * sin(t * 2f)
        p.shader = RadialGradient(cx, cy, 44f * k * pulse, intArrayOf(0x66FFC933, 0x00FFC933), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 44f * k * pulse, p); p.shader = null
        p.color = 0xFFFFC933.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 4f * k; p.strokeCap = Paint.Cap.ROUND
        val spin = t * (if (hot) 0.9f else 0.4f)
        for (i in 0 until 10) {
            val a = spin + i * 2 * PI.toFloat() / 10f
            val r1 = 24f * k; val r2 = (31f + 3f * sin(t * 3f + i)) * k
            c.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, p)
        }
        p.style = Paint.Style.FILL; p.color = 0xFFFFD84D.toInt(); c.drawCircle(cx, cy, 17f * k, p)
    }

    private fun moon(c: Canvas, t: Float) {
        p.style = Paint.Style.FILL; p.color = 0xFFF2F0DC.toInt()
        c.save(); path.reset(); path.addCircle(46f, 52f, 24f, Path.Direction.CW); c.clipPath(path)
        c.drawCircle(46f, 52f, 24f, p); p.color = 0x00000000; p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        c.drawCircle(58f, 44f, 22f, p); p.xfermode = null; c.restore()
        p.color = 0xFFFFFFFF.toInt()
        for (i in 0..3) { p.alpha = (110 + 140 * (0.5f + 0.5f * sin(t * 2f + i * 1.7f))).toInt(); c.drawCircle(68f + i * 6f, 22f + (i % 2) * 18f, 2.2f, p) }
        p.alpha = 255
    }

    private fun drops(c: Canvas, t: Float, color: Int, snow: Boolean) {
        p.color = color; p.style = Paint.Style.FILL; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 6) {
            val ph = (t * (if (snow) 0.35f else 1.1f) + i * 0.17f) % 1f
            val x = 30f + i * 11f + (if (snow) 4f * sin(t + i) else -ph * 4f)
            val y = 60f + ph * 34f
            p.alpha = (255 * (1 - ph)).toInt().coerceIn(0, 255)
            if (snow) c.drawCircle(x, y, 2.4f, p) else { p.style = Paint.Style.STROKE; c.drawLine(x, y, x - 2.5f, y + 7f, p); p.style = Paint.Style.FILL }
        }
        p.alpha = 255
    }
}
