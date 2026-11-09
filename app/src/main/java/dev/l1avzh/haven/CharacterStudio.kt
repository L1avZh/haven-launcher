package dev.l1avzh.haven

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Character Studio: a panel on the end side of the screen while the character stays visible on the other side.
 * Choose a character, then its outfit and prop, and (once unlocked) the hidden pendant. Moving focus over an option
 * previews it on the character with the equip turn; Apply keeps the look, Cancel or Back puts the saved one back.
 * Every control is an ordinary focusable view, reachable with the D-pad.
 */
class CharacterStudio(
    private val act: Activity, private val s: Scale, private val character: CharacterView,
    private val saved: LookState, private val starUnlocked: Boolean,
    private val onApply: (LookState) -> Unit, private val onCancel: () -> Unit,
) : FrameLayout(act) {
    private var look = saved
    private val characterRow = LinearLayout(act); private val outfitRow = LinearLayout(act)
    private val propRow = LinearLayout(act); private val accRow = LinearLayout(act)
    private lateinit var applyBtn: TextView
    private val rtl get() = Lang.isRtl(act)

    private fun nameOf(kind: String, id: String): String = act.getString(when (kind to id) {
        "c" to "wizard" -> R.string.char_wizard; "c" to "robot" -> R.string.char_robot; "c" to "human" -> R.string.char_human
        "o" to "field" -> R.string.outfit_field; "o" to "archmage" -> R.string.outfit_archmage; "o" to "apprentice" -> R.string.outfit_apprentice
        "o" to "plated" -> R.string.outfit_plated; "o" to "stealth" -> R.string.outfit_stealth; "o" to "city" -> R.string.outfit_city
        "p" to "candle" -> R.string.prop_candle; "p" to "book" -> R.string.prop_book; "p" to "sword" -> R.string.prop_sword; "p" to "axe" -> R.string.prop_axe
        "p" to "none" -> R.string.prop_none; "p" to "staff" -> R.string.prop_staff; else -> R.string.prop_candle
    })

    init {
        isFocusable = false
        val panel = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(s.px(48), s.px(44), s.px(56), s.px(36))
            // The scrim is opaque at the screen edge and fades toward the character. (The view's own layout direction is not resolved yet here.)
            background = GradientDrawable(if (Lang.isRtl(act)) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(0xF2070B18.toInt(), 0xE60A1226.toInt(), 0x00000000))
        }
        panel.addView(TextView(act).apply {
            text = act.getString(R.string.character_studio); sizePx(s.px(44f)); setTextColor(T.TEXT); typeface = Fonts.display
            textAlignment = TEXT_ALIGNMENT_VIEW_START
        })
        panel.addView(section(R.string.studio_character, characterRow), lp(22))
        panel.addView(section(R.string.studio_outfit, outfitRow), lp(20))
        panel.addView(section(R.string.studio_prop, propRow), lp(20))
        panel.addView(section(R.string.studio_accessory, accRow), lp(20))
        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        applyBtn = stagePill(act, s, true, act.getString(R.string.studio_apply)) { onApply(look) }
        actions.addView(applyBtn, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = s.px(16) })
        actions.addView(stagePill(act, s, false, act.getString(R.string.cancel)) { onCancel() })
        panel.addView(actions, lp(30))
        addView(panel, LayoutParams(s.px(1100), -1, Gravity.END))
        rebuild()
        if (T.animated) { alpha = 0f; panel.translationX = (if (rtl) -1 else 1) * s.px(70f) }
        post {
            if (T.animated) { animate().alpha(1f).setDuration(T.d(T.MED)).start(); panel.animate().translationX(0f).setDuration(T.d(T.MED + 40)).setInterpolator(T.OUT).start() }
            (characterRow.getChildAt(Look.CHARACTERS.indexOfFirst { it.id == look.character }.coerceAtLeast(0)) ?: applyBtn).requestFocus()
        }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(top) }

    private fun section(title: Int, row: LinearLayout): View = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(act).apply {
            text = act.getString(title); sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM); typeface = Fonts.medium; letterSpacing = 0.08f
            textAlignment = TEXT_ALIGNMENT_VIEW_START
        })
        row.orientation = LinearLayout.HORIZONTAL; row.clipChildren = false
        addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(10) })
    }

    /** The character row is built once; the outfit and prop rows depend on the chosen character and are rebuilt when it changes. */
    private fun rebuild() {
        fill(characterRow, Look.CHARACTERS.map { it.id }, "c", { look.character == it }) { look = LookState.of(it, look.outfit, look.prop, look.star, starUnlocked); changed(dependentRows = true) }
        rebuildDependent()
        accRow.removeAllViews()
        val none = chip(nameOf("p", "none"), "a-none") { selected(!look.star) }
        none.setOnFocusChangeListener { _, f -> if (f) { look = look.copy(star = false); changed() }; style(none, !look.star, f, true) }
        val star = chip(if (starUnlocked) act.getString(R.string.acc_star) else act.getString(R.string.acc_locked), "a-star") { selected(look.star) }
        star.setOnFocusChangeListener { _, f ->
            if (f) { if (starUnlocked) { look = look.copy(star = true); changed() } else character.input(CharInput.Studio(StudioAction.CANCEL)) }
            style(star, look.star && starUnlocked, f, starUnlocked)
        }
        accRow.addView(none, rowParams()); accRow.addView(star, rowParams())
        refresh()
    }

    private fun rebuildDependent() {
        val def = Look.def(look.character)
        fill(outfitRow, def.outfits, "o", { look.outfit == it }) { look = look.copy(outfit = it); changed() }
        fill(propRow, def.props, "p", { look.prop == it }) { look = look.copy(prop = it); changed() }
    }

    private fun selected(@Suppress("UNUSED_PARAMETER") on: Boolean) {}

    private fun rowParams() = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = s.px(14) }

    private fun fill(row: LinearLayout, ids: List<String>, kind: String, isSel: (String) -> Boolean, pick: (String) -> Unit) {
        val hadFocus = row.hasFocus()
        row.removeAllViews()
        ids.forEach { id ->
            val c = chip(nameOf(kind, id), "$kind-$id") { pick(id) }
            c.setOnFocusChangeListener { _, f -> if (f) pick(id); style(c, isSel(id), f, true) }
            row.addView(c, rowParams())
        }
        if (hadFocus) row.getChildAt(ids.indexOfFirst(isSel).coerceAtLeast(0))?.requestFocus()
    }

    private fun changed(dependentRows: Boolean = false) {
        character.equip(look)
        if (dependentRows) rebuildDependent()
        refresh()
    }

    private fun chip(label: String, tag: String, onOk: () -> Unit) = TextView(act).apply {
        text = label; sizePx(s.px(30f)); typeface = Fonts.medium; setTextColor(T.TEXT); gravity = Gravity.CENTER; setSingleLine(); this.tag = tag
        isFocusable = true; isClickable = true; minimumHeight = s.px(70); minimumWidth = s.px(140)
        setPaddingRelative(s.px(30), s.px(12), s.px(30), s.px(12))
        setOnClickListener { onOk() }
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.RadioButton"; info.isCheckable = true
                info.isChecked = isChosen(host.tag as? String); info.isEnabled = !(host.tag == "a-star" && !starUnlocked)
            }
        }
    }

    private fun isChosen(tag: String?) = when (tag) {
        null -> false
        "a-none" -> !look.star
        "a-star" -> look.star
        else -> tag == "c-${look.character}" || tag == "o-${look.outfit}" || tag == "p-${look.prop}"
    }

    private fun style(c: TextView, selected: Boolean, focused: Boolean, enabled: Boolean) {
        c.background = when {
            focused -> rounded(if (enabled) Color.WHITE else 0x66FFFFFF, s.px(T.R_PILL))
            selected -> rounded(0x55FFFFFF, s.px(T.R_PILL), T.RING, s.px(T.RING_PX))
            else -> rounded(T.GLASS_HI, s.px(T.R_PILL), T.HAIRLINE, s.px(1))
        }
        c.setTextColor(if (focused && enabled) T.ON_ACCENT else if (enabled) T.TEXT else T.TEXT_DIM)
        val sc = if (focused && T.animated) 1f + T.FOCUS_LIFT * T.motion else 1f
        c.animate().scaleX(sc).scaleY(sc).setDuration(T.d(T.FOCUS)).setInterpolator(T.OUT).start()
    }

    private fun refresh() {
        for (row in listOf(characterRow, outfitRow, propRow, accRow)) for (i in 0 until row.childCount) {
            val c = row.getChildAt(i) as TextView; val tag = c.tag as? String
            style(c, isChosen(tag), c.isFocused, !(tag == "a-star" && !starUnlocked))
        }
    }

    /** Back means cancel. Returns true when handled. */
    fun back(): Boolean { onCancel(); return true }

    fun close(done: () -> Unit) { if (T.animated) animate().alpha(0f).setDuration(T.d(T.FAST + 40)).withEndAction(done).start() else done() }
}
