package dev.l1avzh.haven

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.view.animation.PathInterpolator

/** Single source of truth for the visual language: colour, type, space, shape and motion. */
object T {
    // Colour
    const val ACCENT = 0xFF6C8CFF.toInt()      // brand accent; per-app accents override it on the home screen
    const val SURFACE = 0xF0121827.toInt()     // menus and dialogs
    const val CARD = 0xFF1A2030.toInt()
    const val TEXT = 0xFFF4F6FA.toInt()
    const val TEXT_DIM = 0xFFA3ACBE.toInt()
    const val ON_ACCENT = 0xFF0C1020.toInt()

    // Layout, in units of a 1920x1080 canvas (see Scale)
    const val MARGIN = 96
    const val FAV_W = 400
    const val FAV_GAP = 28
    const val LIB_W = 256
    const val LIB_GAP = 22

    // Shape
    const val R_CARD = 20f
    const val R_MENU = 28f
    const val R_ROW = 16f

    // Type sizes
    const val HERO = 88f
    const val TITLE = 34f
    const val BODY = 30f
    const val LABEL = 22f

    // Motion: three durations, two curves, used everywhere.
    const val FAST = 120L
    const val MED = 220L
    const val SLOW = 380L
    val OUT = PathInterpolator(0.2f, 0f, 0f, 1f)     // decelerate: things arriving
    val STD = PathInterpolator(0.4f, 0f, 0.2f, 1f)   // standard: colour and state changes

    /** 1 = full motion, 0 = reduced (system animator scale 0 or the in-app setting). */
    var motion = 1f
        private set
    val animated: Boolean get() = motion > 0f
    fun d(ms: Long) = (ms * motion).toLong()

    /** [level]: 2 full, 1 subtle, 0 off. Performance mode caps motion at subtle. The system animator scale 0 always wins. */
    fun init(ctx: Context, level: Int, performance: Boolean) {
        val system = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        motion = when {
            system == 0f || level == 0 -> 0f
            level == 1 || performance -> 0.6f
            else -> 1f
        }
    }
}

/** Outfit (SIL Open Font License 1.1, see docs/OFL-Outfit.txt) in four weights. Other scripts fall back to system fonts. */
object Fonts {
    var thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); private set
    var light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); private set
    var regular: Typeface = Typeface.DEFAULT; private set
    var medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set
    /** Wide display face for the clock and the big stage title; falls back per glyph for other scripts. */
    var display: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); private set
    var displayBold: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set

    fun init(ctx: Context) {
        try {
            thin = ctx.resources.getFont(R.font.outfit_thin); light = ctx.resources.getFont(R.font.outfit_light)
            regular = ctx.resources.getFont(R.font.outfit_regular); medium = ctx.resources.getFont(R.font.outfit_medium)
            display = ctx.resources.getFont(R.font.unbounded_light); displayBold = ctx.resources.getFont(R.font.unbounded_regular)
        } catch (_: Exception) { /* keep the system fonts */ }
    }
}
