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
 * Screensaver: true-black base (kind to OLED panels), the live background in a slowly cycling palette and a
 * clock that drifts to a new spot every half minute so nothing stays lit in one place.
 */
class SaverView(ctx: Context, s: Scale, timePattern12: CharSequence?, timePattern24: CharSequence?, datePattern: CharSequence?, widgets: List<String>) : FrameLayout(ctx) {
    private val live = LiveBackground(ctx)
    private val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val handler = Handler(Looper.getMainLooper())
    private val hsv = floatArrayOf(210f, 0.65f, 0.95f)
    private val hop = object : Runnable {
        override fun run() { moveClock(); hue(); handler.postDelayed(this, 25_000) }
    }

    init {
        setBackgroundColor(Color.BLACK); isClickable = true; isFocusable = false
        live.setAccent(Color.HSVToColor(hsv)); addView(live, -1, -1)
        box.addView(TextClock(ctx).apply {
            if (timePattern12 != null) format12Hour = timePattern12
            if (timePattern24 != null) format24Hour = timePattern24
            sizePx(s.px(150f)); setTextColor(0xEEFFFFFF.toInt()); typeface = Fonts.display; includeFontPadding = false
        })
        box.addView(TextClock(ctx).apply {
            if (datePattern != null) { format12Hour = datePattern; format24Hour = datePattern }
            sizePx(s.px(34f)); setTextColor(0x99FFFFFF.toInt()); typeface = Fonts.light
        })
        if (widgets.isNotEmpty()) box.addView(WidgetStrip(ctx, s, widgets.take(4), true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(36) })
        box.alpha = 0f
        addView(box, LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        alpha = 0f
    }

    private fun hue() { hsv[0] = (hsv[0] + 24f) % 360f; live.setAccent(Color.HSVToColor(hsv)); live.pulse() }

    private fun moveClock() {
        val place = {
            val mx = (width - box.width).coerceAtLeast(1); val my = (height - box.height).coerceAtLeast(1)
            box.translationX = Random.nextInt(mx / 8, mx - mx / 8 + 1).toFloat()
            box.translationY = Random.nextInt(my / 6, my - my / 6 + 1).toFloat()
            box.animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        }
        if (box.alpha > 0f) box.animate().alpha(0f).setDuration(T.d(T.SLOW)).withEndAction(place).start() else place()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        post { handler.removeCallbacks(hop); hop.run() }
    }

    override fun onDetachedFromWindow() { handler.removeCallbacks(hop); super.onDetachedFromWindow() }
}
