package dev.l1avzh.haven

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Character Studio: a panel on the end side of the screen while the character stays visible on the other side.
 * Moving focus over an option previews it on the character (with the equip turn); Apply keeps it, Cancel or Back
 * puts the saved look back. Every control is an ordinary focusable view, reachable with the D-pad.
 */
class CharacterStudio(
    private val act: Activity, private val s: Scale, private val character: CharacterView,
    private val savedOutfit: String, private val savedProp: String,
    private val onApply: (String, String) -> Unit, private val onCancel: () -> Unit,
) : FrameLayout(act) {
    private var outfit = savedOutfit
    private var prop = savedProp
    private val outfitChips = LinkedHashMap<String, TextView>()
    private val propChips = LinkedHashMap<String, TextView>()
    private lateinit var apply: TextView
    private val rtl get() = Lang.isRtl(act)

    private fun outfitName(id: String) = act.getString(when (id) { "runner" -> R.string.outfit_runner; "astral" -> R.string.outfit_astral; else -> R.string.outfit_courier })
    private fun propName(id: String) = act.getString(when (id) { "tablet" -> R.string.prop_tablet; "compass" -> R.string.prop_compass; "none" -> R.string.prop_none; else -> R.string.prop_lantern })

    init {
        isFocusable = false
        // A soft scrim on the panel side only, so the character stays clearly visible.
        val panel = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(s.px(48), s.px(56), s.px(56), s.px(48))
            // The scrim is opaque at the screen edge and fades toward the character. (The view's own layout direction is not resolved yet here.)
            background = GradientDrawable(if (Lang.isRtl(act)) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(0xF2070B18.toInt(), 0xE60A1226.toInt(), 0x00000000))
        }
        panel.addView(TextView(act).apply {
            text = act.getString(R.string.character_studio); sizePx(s.px(46f)); setTextColor(T.TEXT); typeface = Fonts.display
            textAlignment = TEXT_ALIGNMENT_VIEW_START
        })
        panel.addView(section(R.string.studio_outfit, Look.OUTFITS, outfitChips, ::outfitName) { outfit = it; character.equip(outfit, prop); refresh() }, lp(34))
        panel.addView(section(R.string.studio_prop, Look.PROPS, propChips, ::propName) { prop = it; character.equip(outfit, prop); refresh() }, lp(30))
        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        apply = pill(act.getString(R.string.studio_apply), true) { onApply(outfit, prop) }
        val cancel = pill(act.getString(R.string.cancel), false) { onCancel() }
        actions.addView(apply, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = s.px(16) }); actions.addView(cancel)
        panel.addView(actions, lp(44))
        // Down from the last chip row reaches the buttons, up from them returns to the last row.
        addView(panel, LayoutParams(s.px(1100), -1, Gravity.END))
        refresh()
        if (T.animated) { alpha = 0f; panel.translationX = (if (rtl) -1 else 1) * s.px(70f) }
        post {
            if (T.animated) { animate().alpha(1f).setDuration(T.d(T.MED)).start(); panel.animate().translationX(0f).setDuration(T.d(T.MED + 40)).setInterpolator(T.OUT).start() }
            (outfitChips[outfit] ?: apply).requestFocus()
        }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(top) }

    private fun section(title: Int, ids: List<String>, store: MutableMap<String, TextView>, name: (String) -> String, pick: (String) -> Unit): View {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(act).apply {
            text = act.getString(title); sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM); typeface = Fonts.medium; letterSpacing = 0.08f
            textAlignment = TEXT_ALIGNMENT_VIEW_START
        })
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        ids.forEach { id ->
            val chip = chip(name(id)) { pick(id) }
            chip.setOnFocusChangeListener { _, f -> if (f) pick(id); styleChip(chip, store.entries.first { it.value === chip }.key, f) }
            store[id] = chip
            row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = s.px(14) })
        }
        box.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(14) })
        return box
    }

    private fun chip(label: String, onOk: () -> Unit) = TextView(act).apply {
        text = label; sizePx(s.px(30f)); typeface = Fonts.medium; setTextColor(T.TEXT); gravity = Gravity.CENTER; setSingleLine()
        isFocusable = true; isClickable = true; minimumHeight = s.px(76); minimumWidth = s.px(150)
        setPaddingRelative(s.px(34), s.px(14), s.px(34), s.px(14))
        setOnClickListener { onOk() }
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.RadioButton"; info.isCheckable = true
                info.isChecked = outfitChips.entries.any { it.value === host && it.key == outfit } || propChips.entries.any { it.value === host && it.key == prop }
            }
        }
    }

    private fun styleChip(c: TextView, id: String, focused: Boolean) {
        val selected = id == outfit || id == prop
        c.background = when {
            focused -> rounded(Color.WHITE, s.px(T.R_PILL))
            selected -> rounded(0x55FFFFFF, s.px(T.R_PILL), T.RING, s.px(T.RING_PX))
            else -> rounded(T.GLASS_HI, s.px(T.R_PILL), T.HAIRLINE, s.px(1))
        }
        c.setTextColor(if (focused) T.ON_ACCENT else T.TEXT)
        val sc = if (focused && T.animated) 1f + T.FOCUS_LIFT * T.motion else 1f
        c.animate().scaleX(sc).scaleY(sc).setDuration(T.d(T.FOCUS)).setInterpolator(T.OUT).start()
    }

    private fun refresh() {
        outfitChips.forEach { (id, c) -> styleChip(c, id, c.isFocused) }
        propChips.forEach { (id, c) -> styleChip(c, id, c.isFocused) }
    }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit) = stagePill(act, s, primary, label, onClick)

    /** Back means cancel. Returns true when handled. */
    fun back(): Boolean { onCancel(); return true }

    fun close(done: () -> Unit) { if (T.animated) animate().alpha(0f).setDuration(T.d(T.FAST + 40)).withEndAction(done).start() else done() }
}
