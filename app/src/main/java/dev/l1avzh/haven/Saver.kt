package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * Screensaver. A true-black base (kind to OLED panels) under a dim version of the live background, the day or night sky at
 * reduced strength, and one block that holds the clock, the date, a thin rule and the widgets. The whole block, including the
 * optional sleeping character that appears only at night, moves to a new place every 25 seconds so nothing stays lit in one spot.
 * Any key wakes it; the owner decides when it starts and stops.
 */
class SaverView(ctx: Context, private val s: Scale, timePattern12: CharSequence?, timePattern24: CharSequence?, datePattern: CharSequence?, widgets: List<String>,
                private val look: LookState?) : FrameLayout(ctx) {
    private val live = LiveBackground(ctx)
    private val sky = MoodView(ctx).apply { strength = 0.45f }
    private val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val handler = Handler(Looper.getMainLooper())
    private val hsv = floatArrayOf(210f, 0.55f, 0.80f)
    private var sleeper: CharacterView? = null
    private var night = false
    private val hop = object : Runnable {
        override fun run() { moveClock(); hue(); handler.postDelayed(this, 25_000) }
    }

    init {
        setBackgroundColor(Color.BLACK); isClickable = true; isFocusable = false
        contentDescription = ctx.getString(R.string.screensaver); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        live.setAccent(Color.HSVToColor(hsv)); live.alpha = 0.7f; addView(live, -1, -1); addView(sky, -1, -1)
        text.addView(TextClock(ctx).apply {
            if (timePattern12 != null) format12Hour = timePattern12
            if (timePattern24 != null) format24Hour = timePattern24
            sizePx(s.px(160f)); setTextColor(T.TEXT); typeface = Fonts.display; includeFontPadding = false
        })
        text.addView(TextClock(ctx).apply {
            if (datePattern != null) { format12Hour = datePattern; format24Hour = datePattern }
            sizePx(s.px(34f)); setTextColor(T.TEXT_DIM); typeface = Fonts.light
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(4) })
        if (widgets.isNotEmpty()) {
            text.addView(View(ctx).apply { setBackgroundColor(T.HAIRLINE) }, LinearLayout.LayoutParams(s.px(120), s.px(2)).apply { topMargin = s.px(32); bottomMargin = s.px(32) })
            // A fixed budget keeps the widget row from growing with the clock text; cards share it evenly.
            text.addView(WidgetStrip(ctx, s, widgets.take(WidgetStrip.MAX_SAVER), true), LinearLayout.LayoutParams(s.px(1080), -2))
        }
        box.addView(text, LinearLayout.LayoutParams(-2, -2))
        box.alpha = 0f
        addView(box, LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        alpha = 0f
    }

    /** Day or night changed: update the sky and, at night, bring in a sleeping character beside the clock (or remove it). */
    fun setMood(isNight: Boolean, scene: SceneLook) {
        night = isNight; sky.setNight(isNight, true)
        val want = isNight && scene.sleeping && look != null
        if (want && sleeper == null) {
            sleeper = CharacterView(context).also {
                it.setLook(look!!); it.motion = T.motion; it.frameGapMs = 66L           // a slow breath needs few frames
                it.setScene(scene); it.setEnvironment(true, WeatherEffect.NONE); box.addView(it, 0, LinearLayout.LayoutParams(s.px(520), s.px(380)).apply { marginEnd = s.px(32) })
                it.start()
            }
        } else if (!want) sleeper?.let { box.removeView(it); it.destroy(); sleeper = null }
    }

    private fun hue() { hsv[0] = (hsv[0] + 24f) % 360f; live.setAccent(Color.HSVToColor(hsv)); live.pulse() }

    private fun moveClock() {
        val place = {
            val mx = (width - box.width).coerceAtLeast(1); val my = (height - box.height).coerceAtLeast(1)
            box.translationX = Random.nextInt(mx / 10, (mx - mx / 10).coerceAtLeast(mx / 10 + 1)).toFloat()
            box.translationY = Random.nextInt(my / 6, (my - my / 6).coerceAtLeast(my / 6 + 1)).toFloat()
            box.animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        }
        if (box.alpha > 0f) box.animate().alpha(0f).setDuration(T.d(T.SLOW)).withEndAction(place).start() else place()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        post { handler.removeCallbacks(hop); hop.run() }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(hop); sleeper?.destroy(); sleeper = null
        super.onDetachedFromWindow()
    }
}
