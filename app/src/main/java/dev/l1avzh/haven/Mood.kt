package dev.l1avzh.haven

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import java.util.Calendar

/** Day, night and weather as the whole launcher sees them: one answer, computed in one place. */
object Mood {
    class State(val night: Boolean, val scene: SceneLook, val effect: WeatherEffect)

    /** Debug-only test input ("code|celsius|isDay"); honoured only by debuggable builds, never by a release APK. */
    private fun debuggable(ctx: Context) = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun resolve(ctx: Context, p: Prefs, nowMs: Long = System.currentTimeMillis()): State {
        var code = 0; var celsius = 0.0; var stamp = 0L; var isDay: Boolean? = null; var have = false
        val dbg = if (debuggable(ctx)) p.str("dbg_weather") else ""
        if (dbg.isNotEmpty()) {
            val f = dbg.split('|')
            code = f.getOrNull(0)?.toIntOrNull() ?: 0; celsius = f.getOrNull(1)?.toDoubleOrNull() ?: 20.0
            isDay = when (f.getOrNull(2)) { "1" -> true; "0" -> false; else -> null }; stamp = nowMs; have = true
        } else Weather.entry(p)?.let { code = it.code; celsius = it.temp; stamp = it.stamp; isDay = it.isDay; have = true }
        val fresh = have && WeatherEffects.isFresh(stamp, nowMs)
        val effect = if (have) WeatherEffects.classify(code, celsius, fresh) else WeatherEffect.NONE
        // A stale reading's day/night flag is as untrustworthy as its sky; fall back to the clock.
        val hour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
        val mode = DayNight.mode(p.dayNightMode)
        val dbgMode = if (debuggable(ctx)) p.str("dbg_daynight") else ""
        val night = DayNight.isNight(if (dbgMode.isNotEmpty()) DayNight.mode(dbgMode.toIntOrNull() ?: 0) else mode, if (fresh) isDay else null, hour, p.nightFrom, p.nightTo)
        return State(night, SceneRules.decide(effect, night, p.sleepAtNight, p.weatherReactions), effect)
    }
}

/**
 * The sky behind everything. Day is a brighter blue with a warm sun glow; night is the deep obsidian ground with a
 * cool moon and a few faint stars. [setNight] cross-fades between the two (instantly when motion is reduced).
 * The stars never twinkle and stay dim, so nothing here can burn in.
 */
class MoodView(ctx: Context) : View(ctx) {
    private var p = 1f                     // 0 = day, 1 = night
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stars = FloatArray(36 * 3)
    private var wash: LinearGradient? = null
    private var sun: RadialGradient? = null
    private var moon: RadialGradient? = null
    /** Less than 1 keeps the sky subtle where text sits directly on it (the screensaver). */
    var strength = 1f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; isFocusable = false
        // The sky changes only during a cross-fade, so it is drawn once into a texture and composited as one quad each frame
        // instead of re-running its gradient and star draws behind a background that redraws 30 times a second.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        val r = java.util.Random(7)
        for (i in 0 until 36) { stars[i * 3] = r.nextFloat(); stars[i * 3 + 1] = r.nextFloat() * 0.55f; stars[i * 3 + 2] = 0.6f + r.nextFloat() * 1.4f }
    }

    fun setNight(night: Boolean, animate: Boolean) {
        val to = if (night) 1f else 0f
        anim?.cancel()
        if (!animate || T.motion <= 0f || !isAttachedToWindow || p == to) { p = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(p, to).apply { duration = T.d(1400L); addUpdateListener { p = it.animatedValue as Float; invalidate() }; start() }
    }

    fun isNightNow() = p >= 0.5f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        wash = LinearGradient(0f, 0f, 0f, h * 0.9f, intArrayOf(0xFF2C7BD0.toInt(), 0x662C7BD0, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        sun = RadialGradient(w * 0.82f, h * 0.14f, h * 0.55f, intArrayOf(0xCCFFD07A.toInt(), 0x55FFB45A, 0x00FFB45A), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        moon = RadialGradient(w * 0.84f, h * 0.15f, h * 0.30f, intArrayOf(0x66DCE8FF, 0x22A8C0FF, 0x00A8C0FF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val day = (1f - p) * strength
        if (day > 0.01f) {
            paint.shader = wash; paint.alpha = (0x99 * day).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = sun; paint.alpha = (0xFF * day).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        if (p > 0.01f) {
            paint.shader = moon; paint.alpha = (0xFF * p * strength).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            val d = resources.displayMetrics.density
            for (i in 0 until 36) { star.alpha = (70 * p * strength).toInt(); c.drawCircle(stars[i * 3] * width, stars[i * 3 + 1] * height, stars[i * 3 + 2] * d, star) }
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
}
