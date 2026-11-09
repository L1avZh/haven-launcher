package dev.l1avzh.haven

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Settings content model. Rows are built from these so every setting looks and behaves the same. */
sealed class Item { abstract val title: String; open val desc: String? = null }
class Toggle(override val title: String, override val desc: String?, val get: () -> Boolean, val set: (Boolean) -> Unit) : Item()
class Choice(
    override val title: String, override val desc: String?, val options: List<String>, val index: () -> Int,
    val set: (Int) -> Unit, val swatches: List<Int>? = null,
) : Item()
class Action(override val title: String, override val desc: String?, val value: (() -> String?)? = null, val run: () -> Unit) : Item()
class Info(override val title: String, override val desc: String?, val value: String? = null) : Item()
/**
 * A row that looks like an Info row but counts deliberate OK presses: [taps] of them, each at least a moment apart and
 * all within a short window, so neither a held key nor ordinary browsing can trigger it. [onReveal] runs once.
 */
class TapRow(override val title: String, val value: String, val taps: Int, val done: () -> Boolean, val unlockedText: String, val onReveal: () -> Unit) : Item()
/** The quiet Easter-egg entry: a faint mark with a hint. Three OK presses in a row reveal the signature. */
class Secret(override val title: String, val onReveal: () -> Unit) : Item()

class Category(val icon: Int, val title: String, val desc: String, val items: () -> List<Item>)

/** Animated on/off switch. Accent-coloured track when on; the thumb slides in the layout direction. */
class SwitchView(ctx: Context, private val s: Scale) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG); private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var pos = 0f
    private var anim: ValueAnimator? = null
    var checked = false; private set

    init { thumb.color = Color.WHITE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }   // the row reports the switch state

    fun set(on: Boolean, animate: Boolean) {
        checked = on
        anim?.cancel()
        val to = if (on) 1f else 0f
        if (!animate || !T.animated) { pos = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(pos, to).apply {
            duration = T.d(T.MED); interpolator = T.OUT
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(s.px(84), s.px(48))

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        track.color = blend(0x33FFFFFF, T.ACCENT, pos)
        r.set(0f, 0f, w, h); c.drawRoundRect(r, h / 2, h / 2, track)
        val pad = h * 0.14f; val d = h - 2 * pad
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val travel = (w - 2 * pad - d) * (if (rtl) 1f - pos else pos)
        c.drawCircle(pad + travel + d / 2, h / 2, d / 2, thumb)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(ch(Color.alpha(a), 255), ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }
}

/**
 * Full-screen, remote-only settings: categories on the start side, the focused category's rows on the end side.
 * Focus moves toward the content with the arrow that points at it (mirrored in RTL); Back returns to the categories,
 * and Back from the categories closes the screen.
 */
class SettingsScreen(
    private val act: Activity, private val s: Scale, private val title: String,
    private val cats: List<Category>, startAt: Int, private val onSelect: (Int) -> Unit, private val onClose: () -> Unit,
) : FrameLayout(act) {
    private val main = Handler(Looper.getMainLooper())
    private val catViews = mutableListOf<View>()
    private val panelTitle = TextView(act); private val panelDesc = TextView(act)
    private val panelIcon = ImageView(act).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val rows = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val panelScroll = ScrollView(act)
    private var selected = startAt.coerceIn(0, cats.lastIndex)
    private val rtl get() = layoutDirection == LAYOUT_DIRECTION_RTL
    private val rebuild = Runnable { buildPanel(animate = true) }

    init {
        // Obsidian ground with two soft pools of aurora light, painted once (no per-frame work).
        fun glow(cx: Float, cy: Float, color: Int, r: Float) = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; setGradientCenter(cx, cy); gradientRadius = s.px(r).toFloat()
            colors = intArrayOf(color, color and 0xFFFFFF)
        }
        // Day is a brighter blue with a warm sun glow; night is the obsidian ground. Same layout, same contrast for text.
        val night = Mood.resolve(act, Prefs(act)).night
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (night) intArrayOf(T.MIDNIGHT, T.OBSIDIAN) else intArrayOf(0xFF174B86.toInt(), 0xFF07162D.toInt())),
            glow(if (rtl) 0.1f else 0.9f, 0.05f, if (night) 0x553A3FB5 else 0x66FFC46B, 1000f),
            glow(if (rtl) 0.95f else 0.05f, 1.0f, if (night) 0x3338D8F0 else 0x4438B6F0, 900f),
        ))
        isFocusable = false
        val root = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        addView(root, LayoutParams(-1, -1))

        // Start side: title + category cards.
        val left = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPaddingRelative(s.px(88), s.px(52), s.px(40), s.px(28)) }
        left.addView(TextView(act).apply {
            text = title; sizePx(s.px(46f)); setTextColor(T.TEXT); typeface = Fonts.display
            textAlignment = TEXT_ALIGNMENT_VIEW_START; setPaddingRelative(s.px(8), 0, 0, s.px(14))
        })
        val catList = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        cats.forEachIndexed { i, c -> catList.addView(categoryCard(i, c), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = s.px(5) }) }
        left.addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; addView(catList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(left, LinearLayout.LayoutParams(s.px(780), -1))

        // Divider hairline between the two sides.
        root.addView(View(act).apply { setBackgroundColor(0x14FFFFFF) }, LinearLayout.LayoutParams(s.px(1), -1).apply { topMargin = s.px(64); bottomMargin = s.px(64) })

        // End side: the focused category.
        val right = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPaddingRelative(s.px(64), s.px(64), s.px(88), s.px(40)) }
        panelTitle.apply { sizePx(s.px(42f)); setTextColor(T.TEXT); typeface = Fonts.display; textAlignment = TEXT_ALIGNMENT_VIEW_START }
        panelDesc.apply { sizePx(s.px(T.LABEL + 4)); setTextColor(T.TEXT_DIM); textAlignment = TEXT_ALIGNMENT_VIEW_START }
        panelIcon.setColorFilter(T.ACCENT)
        right.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(panelIcon, LinearLayout.LayoutParams(s.px(40), s.px(40)).apply { marginEnd = s.px(18) }); addView(panelTitle) })
        right.addView(panelDesc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(6); bottomMargin = s.px(26) })
        panelScroll.apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; clipChildren = false; addView(rows) }
        right.addView(panelScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))

        buildPanel(animate = false)
        if (T.animated) { alpha = 0f; root.translationY = s.px(24f) }
    }

    fun show(parent: FrameLayout) {
        parent.addView(this, -1, -1)
        if (T.animated) { animate().alpha(1f).setDuration(T.d(T.MED)).start(); getChildAt(0).animate().translationY(0f).setDuration(T.d(T.MED + 40)).setInterpolator(T.OUT).start() }
        post { catViews[selected].requestFocus() }
    }

    fun close(done: () -> Unit) {
        if (T.animated) animate().alpha(0f).setDuration(T.d(T.FAST + 40)).withEndAction(done).start() else done()
    }

    /** Back: from the rows go to the categories; from the categories ask the owner to close. */
    fun back() {
        val f = findFocus()
        if (f != null && !catViews.contains(f) && !isInside(f, catViews)) catViews[selected].requestFocus() else onClose()
    }

    private fun isInside(v: View, roots: List<View>): Boolean = roots.any { r -> var p: View? = v; while (p != null) { if (p === r) return true; p = p.parent as? View }; false }

    /** Rebuild the rows, keeping the same row focused (used after a setting changes what other rows show). */
    fun refresh() {
        val f = findFocus(); val idx = (0 until rows.childCount).firstOrNull { rows.getChildAt(it) === f }
        buildPanel(animate = false)
        idx?.let { rows.getChildAt(it.coerceAtMost(rows.childCount - 1))?.requestFocus() }
    }

    private fun categoryCard(i: Int, c: Category): View {
        val iconBox = FrameLayout(act).apply { background = rounded(0x1AFFFFFF, s.px(26f)) }
        val icon = ImageView(act).apply { setImageResource(c.icon); setColorFilter(T.TEXT_DIM); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        iconBox.addView(icon, FrameLayout.LayoutParams(s.px(30), s.px(30), Gravity.CENTER))
        val tt = TextView(act).apply { text = c.title; sizePx(s.px(T.BODY + 1)); setTextColor(T.TEXT); typeface = Fonts.medium; textAlignment = TEXT_ALIGNMENT_VIEW_START; maxLines = 1 }
        val dd = TextView(act).apply { text = c.desc; sizePx(s.px(T.LABEL)); setTextColor(T.TEXT_DIM); textAlignment = TEXT_ALIGNMENT_VIEW_START; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(tt); addView(dd) }
        val bar = View(act).apply { background = rounded(T.ACCENT, s.px(3f)); alpha = 0f; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPaddingRelative(s.px(14), s.px(9), s.px(18), s.px(9))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x33FFFFFF, s.px(T.R_ROW), 0x99FFFFFF.toInt(), s.px(2)))
                addState(intArrayOf(android.R.attr.state_selected), rounded(T.GLASS, s.px(T.R_ROW), T.HAIRLINE, s.px(1)))
                addState(intArrayOf(), rounded(Color.TRANSPARENT, s.px(T.R_ROW)))
                setEnterFadeDuration(T.d(T.FOCUS).toInt()); setExitFadeDuration(T.d(T.FOCUS).toInt())
            }
        }
        card.addView(bar, LinearLayout.LayoutParams(s.px(4), s.px(34)).apply { marginEnd = s.px(12) })
        card.addView(iconBox, LinearLayout.LayoutParams(s.px(52), s.px(52)).apply { marginEnd = s.px(18) })
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        card.setOnFocusChangeListener { _, f ->
            bar.animate().alpha(if (f || selected == i) 1f else 0f).setDuration(T.d(T.FAST)).start()
            icon.setColorFilter(if (f || selected == i) T.TEXT else T.TEXT_DIM)
            if (f) { selected = i; onSelect(i); main.removeCallbacks(rebuild); main.postDelayed(rebuild, 70) }
            else bar.animate().alpha(if (selected == i && findFocus() != null && !catViews.contains(findFocus())) 1f else 0f).setDuration(T.d(T.FAST)).start()
        }
        card.setOnKeyListener { _, code, e ->
            // The arrow pointing at the content (right in LTR, left in RTL) enters the rows.
            val into = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            if (e.action == KeyEvent.ACTION_DOWN && code == into) { rows.getChildAt(0)?.requestFocus(); true } else false
        }
        card.setOnClickListener { rows.getChildAt(0)?.requestFocus() }
        catViews += card
        return card
    }

    private fun buildPanel(animate: Boolean) {
        val c = cats[selected]
        panelTitle.text = c.title; panelDesc.text = c.desc; panelIcon.setImageResource(c.icon)
        catViews.forEachIndexed { i, v -> v.isSelected = i == selected }
        rows.removeAllViews()
        c.items().forEach { rows.addView(rowFor(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = s.px(8) }) }
        panelScroll.scrollTo(0, 0)
        if (animate && T.animated) {
            rows.animate().cancel()
            rows.alpha = 0f; rows.translationY = s.px(14f)
            rows.animate().alpha(1f).translationY(0f).setDuration(T.d(T.FOCUS + 40)).setInterpolator(T.OUT).start()
        } else { rows.alpha = 1f; rows.translationY = 0f }
    }

    private fun rowFor(item: Item): View {
        if (item is Secret) return secretRow(item)
        val bar = View(act).apply { background = rounded(T.ACCENT, s.px(3f)); alpha = 0f; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        val tt = TextView(act).apply { text = item.title; sizePx(s.px(T.BODY)); setTextColor(T.TEXT); textAlignment = TEXT_ALIGNMENT_VIEW_START }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(tt) }
        item.desc?.let { d -> col.addView(TextView(act).apply { text = d; sizePx(s.px(T.LABEL + 1)); setTextColor(T.TEXT_DIM); textAlignment = TEXT_ALIGNMENT_VIEW_START; setLineSpacing(0f, 1.1f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(4) }) }
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = item !is Info
            minimumHeight = s.px(88)
            setPaddingRelative(s.px(16), s.px(16), s.px(26), s.px(16))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x33FFFFFF, s.px(T.R_ROW), 0x99FFFFFF.toInt(), s.px(2)))
                addState(intArrayOf(), rounded(T.GLASS, s.px(T.R_ROW), 0x14FFFFFF, s.px(1)))
                setEnterFadeDuration(T.d(T.FOCUS).toInt()); setExitFadeDuration(T.d(T.FOCUS).toInt())
            }
        }
        row.addView(bar, LinearLayout.LayoutParams(s.px(4), s.px(36)).apply { marginEnd = s.px(14) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        fun valueText(t: String?) = TextView(act).apply { text = t.orEmpty(); sizePx(s.px(T.BODY - 2)); setTextColor(T.TEXT_DIM); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPaddingRelative(s.px(18), 0, 0, 0) }
        when (item) {
            is Toggle -> {
                val sw = SwitchView(act, s).also { it.set(item.get(), false) }
                row.addView(sw)
                row.setOnClickListener { val n = !item.get(); item.set(n); sw.set(n, true); row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) }
                row.accessibilityDelegate = object : AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Switch"; info.isCheckable = true; info.isChecked = item.get()
                    }
                }
            }
            is Choice -> {
                val v = valueText(item.options[item.index()]).apply { setTextColor(T.TEXT) }
                item.swatches?.let { sw -> row.addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(sw[item.index()]) } }.also { dot -> dot.tag = "dot" }, LinearLayout.LayoutParams(s.px(26), s.px(26)).apply { marginEnd = s.px(4) }) }
                row.addView(v)
                row.setOnClickListener {
                    val n = (item.index() + 1) % item.options.size; item.set(n)
                    v.text = item.options[n]
                    row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
                    item.swatches?.let { sw -> (row.findViewWithTag<View>("dot")?.background as? GradientDrawable)?.setColor(sw[n]) }
                    if (T.animated) v.animate().cancel().also { v.alpha = 0.3f; v.animate().alpha(1f).setDuration(T.d(T.MED)).start() }
                }
            }
            is Action -> {
                item.value?.invoke()?.let { row.addView(valueText(it).apply { setTextColor(T.TEXT) }) }
                row.addView(TextView(act).apply { text = if (rtl) "‹" else "›"; sizePx(s.px(40f)); setTextColor(T.TEXT_DIM); setPaddingRelative(s.px(14), 0, 0, 0) })
                row.setOnClickListener { item.run() }
                row.accessibilityDelegate = object : AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.Button"
                    }
                }
            }
            is Info -> item.value?.let { row.addView(valueText(it).apply { setTextColor(T.TEXT) }) }
            is TapRow -> {
                val v = valueText(item.value).apply { setTextColor(T.TEXT) }; row.addView(v)
                var count = 0; var last = 0L
                row.setOnClickListener {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - last < 220) return@setOnClickListener               // too quick to be deliberate (key repeat, bounce)
                    count = if (now - last > 1800) 1 else count + 1; last = now
                    if (!item.done() && count >= item.taps) {
                        count = 0; item.onReveal()
                        v.text = item.unlockedText; v.setTextColor(T.ACCENT)
                        if (T.animated) v.animate().cancel().also { v.alpha = 0f; v.animate().alpha(1f).setDuration(T.d(T.SLOW)).start() }
                        v.postDelayed({ if (v.isAttachedToWindow) { v.text = item.value; v.setTextColor(T.TEXT) } }, 3500)
                    } else if (T.animated && !item.done()) row.animate().cancel().also { row.scaleX = 0.992f; row.scaleY = 0.992f; row.animate().scaleX(1f).scaleY(1f).setDuration(T.d(T.FAST)).start() }
                }
            }
            is Secret -> {}
        }
        row.setOnFocusChangeListener { v, f ->
            bar.animate().alpha(if (f) 1f else 0f).setDuration(T.d(T.FAST)).start()
            tt.setTextColor(if (f) Color.WHITE else T.TEXT)
        }
        row.setOnKeyListener { _, code, e ->
            val out = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT   // toward the categories
            if (e.action == KeyEvent.ACTION_DOWN && code == out) { catViews[selected].requestFocus(); true } else false
        }
        return row
    }

    /** A faint mark and one sentence. OK three times, quickly, opens the signature. */
    private fun secretRow(item: Secret): View {
        var taps = 0; var last = 0L
        val mark = ImageView(act).apply { setImageResource(R.drawable.ic_home); setColorFilter(0x66FFFFFF); alpha = 0.55f }
        val box = FrameLayout(act).apply { background = rounded(0x0DFFFFFF, s.px(30f)); addView(mark, FrameLayout.LayoutParams(s.px(30), s.px(30), Gravity.CENTER)) }
        val tx = TextView(act).apply { text = item.title; sizePx(s.px(T.LABEL + 2)); setTextColor(0x88A3ACBE.toInt()); textAlignment = TEXT_ALIGNMENT_VIEW_START; typeface = Fonts.light }
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPaddingRelative(s.px(16), s.px(18), s.px(26), s.px(18))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x14FFFFFF, s.px(T.R_ROW)))
                addState(intArrayOf(), rounded(Color.TRANSPARENT, s.px(T.R_ROW)))
            }
            addView(box, LinearLayout.LayoutParams(s.px(60), s.px(60)).apply { marginEnd = s.px(22) })
            addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
        }
        row.setOnFocusChangeListener { _, f ->
            mark.animate().alpha(if (f) 1f else 0.55f).setDuration(T.d(T.MED)).start()
            box.background = rounded(if (f) 0x33FFFFFF else 0x0DFFFFFF, s.px(30f))
            tx.setTextColor(if (f) T.TEXT_DIM else 0x88A3ACBE.toInt())
        }
        row.setOnClickListener {
            val now = System.currentTimeMillis()
            taps = if (now - last > 1800) 1 else taps + 1; last = now
            if (T.animated) mark.animate().scaleX(1.25f).scaleY(1.25f).setDuration(T.d(T.FAST)).withEndAction { mark.animate().scaleX(1f).scaleY(1f).setDuration(T.d(T.FAST)).start() }.start()
            if (taps >= 3) { taps = 0; item.onReveal() }
        }
        row.setOnKeyListener { _, code, e ->
            val out = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            if (e.action == KeyEvent.ACTION_DOWN && code == out) { catViews[selected].requestFocus(); true } else false
        }
        return row
    }
}
