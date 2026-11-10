package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * Screensaver. The home screen's bundled landscape under a darker, calmer wash, one block with the clock, the date, a thin rule and the
 * widgets, and (when "Sleeping character on screensaver" is on) the selected character standing, or sleeping at night, on the other side.
 * Nothing stays lit in one place: the text block hops to a new spot every 25 seconds, the photograph drifts very slowly, and the figure
 * shifts a little each time. No dots, particles or cuts. Any key wakes it; the owner decides when it starts and stops.
 */
class SaverView(ctx: Context, private val s: Scale, timePattern12: CharSequence?, timePattern24: CharSequence?, datePattern: CharSequence?, widgets: List<String>,
                private val look: LookState?) : FrameLayout(ctx) {
    private val photo = PhotoView(ctx).apply { saver = true; scaleX = 1.12f; scaleY = 1.12f }
    private val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val handler = Handler(Looper.getMainLooper())
    private var figure: CharacterView? = null
    private val figureBox = FrameLayout(ctx)
    private val figW = s.px(640)
    private var driftDir = 1
    private val hop = object : Runnable {
        override fun run() { moveClock(); drift(); handler.postDelayed(this, 25_000) }
    }

    init {
        setBackgroundColor(Color.BLACK); isClickable = true; isFocusable = false
        contentDescription = ctx.getString(R.string.screensaver); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(photo, -1, -1)
        if (look != null) {
            // a soft contact shadow, then the character, on the end side (right in LTR, left in RTL)
            figureBox.addView(View(ctx).apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = s.px(300f).toFloat(); colors = intArrayOf(0xAA000000.toInt(), 0x55000000, 0x00000000) }
                scaleY = 0.22f
            }, LayoutParams(s.px(620), s.px(620), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = -s.px(235) })
            addView(figureBox, LayoutParams(figW, s.px(900), Gravity.BOTTOM or Gravity.END).apply { marginEnd = s.px(110); bottomMargin = s.px(40) })
        }
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

    /** Day or night changed: update the photo's wash and the character (standing, or asleep at night when that is on). */
    fun setMood(isNight: Boolean, scene: SceneLook) {
        photo.setNight(isNight, true)
        if (look == null) return
        val f = figure
        if (f == null) {
            figure = CharacterView(context).also {
                it.setLook(look); it.motion = T.motion; it.frameGapMs = 66L           // calm pose, few frames
                it.setScene(scene); it.setEnvironment(isNight, WeatherEffect.NONE)      // no falling rain or snow here
                figureBox.addView(it, figureParams(scene.sleeping))
                it.start()
            }
        } else {
            f.setScene(scene); f.setEnvironment(isNight, WeatherEffect.NONE)
            f.layoutParams = figureParams(scene.sleeping)
        }
    }

    /** Standing needs a tall view; lying on the bed a short wide one, so the bed rests near the ground shadow instead of floating. */
    private fun figureParams(sleeping: Boolean) = FrameLayout.LayoutParams(if (sleeping) s.px(700) else figW, if (sleeping) s.px(440) else s.px(900), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

    private val rtl get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun moveClock() {
        val place = {
            // With a figure on the end side the block keeps to the start side and mostly moves up and down.
            val room = if (look != null) width - s.px(110 + 760) else width
            val mx = (room - box.width).coerceAtLeast(1); val my = (height - box.height).coerceAtLeast(1)
            val x = if (look != null) Random.nextInt(s.px(48), s.px(48) + mx.coerceIn(1, s.px(48))) else Random.nextInt(mx / 10, (mx - mx / 10).coerceAtLeast(mx / 10 + 1))
            box.translationX = (if (rtl) -x else x).toFloat()
            box.translationY = Random.nextInt(my / 6, (my - my / 6).coerceAtLeast(my / 6 + 1)).toFloat()
            box.animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        }
        if (box.alpha > 0f) box.animate().alpha(0f).setDuration(T.d(T.SLOW)).withEndAction(place).start() else place()
    }

    /** The photograph glides a few percent one way, then back the next time: slow enough to read as stillness, enough to keep no pixel fixed. */
    private fun drift() {
        val amp = width * (0.012f + 0.03f * T.motion)
        driftDir = -driftDir
        photo.animate().cancel()
        photo.animate().translationX(driftDir * amp).translationY(-driftDir * amp * 0.4f).setDuration(25_000).setInterpolator(android.view.animation.LinearInterpolator()).start()
        figureBox.animate().cancel()
        figureBox.animate().translationY(-driftDir * s.px(18f) * (0.3f + 0.7f * T.motion)).setDuration(25_000).setInterpolator(android.view.animation.LinearInterpolator()).start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animate().alpha(1f).setDuration(T.d(T.SLOW * 2)).start()
        post { handler.removeCallbacks(hop); hop.run() }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(hop); photo.animate().cancel(); figureBox.animate().cancel(); box.animate().cancel()
        figure?.destroy(); figure = null
        super.onDetachedFromWindow()
    }
}
