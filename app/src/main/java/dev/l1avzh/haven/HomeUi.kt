package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The app list on the home screen. Plain RecyclerView; Left/Right are allowed to leave it (toward the stage). */
class RailView(ctx: Context) : RecyclerView(ctx) {
    override fun hasOverlappingRendering() = false
}

/**
 * Keeps the focused row on a fixed slot a few rows from the top: the cursor stays put and the list moves under it.
 */
class RailLayoutManager(ctx: Context, private val anchorRows: Int) : LinearLayoutManager(ctx, VERTICAL, false) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean,
    ): Boolean {
        val target = paddingTop + minOf(getPosition(child), anchorRows) * child.height
        val dy = child.top - target
        if (dy == 0) return false
        if (immediate || !T.animated) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy, T.OUT, T.d(T.MED + 40).toInt())
        return true
    }
}

/**
 * One typographic row per app. The focused name grows and brightens, everything else recedes.
 * No artwork is loaded here; the stage shows the artwork of the focused app only.
 */
class RailAdapter(
    private val s: Scale,
    private val onFocus: (AppEntry, Int) -> Unit,
    private val onClick: (AppEntry) -> Unit,
    private val onLong: (AppEntry) -> Unit,
    private val onToStage: () -> Unit,
    private val onUpFromTop: () -> Unit,
) : RecyclerView.Adapter<RailAdapter.VH>() {

    var items: List<AppEntry> = emptyList(); private set
    private var focusedHolder: VH? = null
    private var recycler: RecyclerView? = null
    override fun onAttachedToRecyclerView(rv: RecyclerView) { recycler = rv }
    override fun onDetachedFromRecyclerView(rv: RecyclerView) { if (recycler === rv) recycler = null }

    /** Moves focus one row up or down by position, so a burst of key presses never depends on a row being laid out yet. */
    private fun step(from: Int, delta: Int) {
        val rv = recycler ?: return
        // Presses that arrive before the previous target has been laid out and focused count from that target.
        val to = (if (pending >= 0) pending else from) + delta
        if (to < 0 || to >= items.size) return
        pending = to
        val holder = rv.findViewHolderForAdapterPosition(to)
        if (holder != null) holder.itemView.requestFocus()
        else { rv.scrollToPosition(to); focusLater(rv, to, 4) }
    }

    /** The target row appears after the next layout pass; try on the following frames, then give up and release the target. */
    private fun focusLater(rv: RecyclerView, to: Int, tries: Int) {
        rv.post {
            val h = rv.findViewHolderForAdapterPosition(to)
            when {
                pending != to -> {}                                      // a newer press has moved the target on
                h != null -> h.itemView.requestFocus()
                tries > 0 -> focusLater(rv, to, tries - 1)
                else -> pending = -1
            }
        }
    }
    private var pending = -1

    fun submit(list: List<AppEntry>) {
        pending = -1
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].key == list[n].key
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == list[n]
        })
        items = list
        diff.dispatchUpdatesTo(this)
    }

    /** Colors the focused row's marker with the artwork accent once it is known. */
    fun tint(color: Int) { focusedHolder?.dot?.background = dotShape(color) }

    class VH(val row: LinearLayout, val dot: View, val name: TextView) : RecyclerView.ViewHolder(row) { var bound: String? = null }

    private fun dotShape(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
        val ctx = parent.context
        val dot = View(ctx).apply { background = dotShape(0x66FFFFFF); alpha = 0f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val name = TextView(ctx).apply {
            sizePx(s.px(46f)); setTextColor(T.TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; typeface = Fonts.light; includeFontPadding = false; alpha = 0.5f
        }
        val row = PressRow(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; isLongClickable = true
            layoutParams = RecyclerView.LayoutParams(-1, s.px(92))
            setPaddingRelative(s.px(22), 0, s.px(22), 0)
            // The focused row sits in a glass capsule with a bright ring; the drawable fades itself in and out, so rapid
            // D-pad movement retargets instead of queueing.
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(T.GLASS_HI, s.px(T.R_PILL + 8), T.RING, s.px(T.RING_PX)))
                addState(intArrayOf(), rounded(Color.TRANSPARENT, s.px(T.R_PILL + 8)))
                setEnterFadeDuration(T.d(T.FOCUS).toInt()); setExitFadeDuration(T.d(T.FOCUS).toInt())
            }
        }
        row.addView(dot, LinearLayout.LayoutParams(s.px(14), s.px(14)).apply { marginEnd = s.px(22) })
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val vh = VH(row, dot, name)
        row.setOnFocusChangeListener { _, f ->
            val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
            name.pivotX = if (rtl) name.width.toFloat() else 0f; name.pivotY = name.height / 2f
            val sc = if (f && T.animated) 1f + 0.16f * T.motion else 1f
            name.animate().scaleX(sc).scaleY(sc).alpha(if (f) 1f else 0.5f).setDuration(T.d(T.FOCUS)).setInterpolator(T.OUT).start()
            dot.animate().alpha(if (f) 1f else 0f).setDuration(T.d(T.FAST)).start()
            vh.bound?.let { k -> val fam = Fonts.familyFor(k); name.typeface = if (f) fam.second else fam.first }
            if (f) { focusedHolder = vh; val p = vh.bindingAdapterPosition; if (p == pending) pending = -1; items.getOrNull(p)?.let { onFocus(it, p) } }
            else if (focusedHolder === vh) focusedHolder = null
        }
        row.setOnKeyListener { _, code, e ->
            val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val toStage = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val away = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            if (e.action != KeyEvent.ACTION_DOWN) false
            else when {
                code == toStage -> { onToStage(); true }
                code == away -> true                         // nothing lives on that side; never jump elsewhere
                code == KeyEvent.KEYCODE_DPAD_UP && vh.bindingAdapterPosition == 0 -> { onUpFromTop(); true }
                code == KeyEvent.KEYCODE_DPAD_UP -> { vh.bindingAdapterPosition.takeIf { it >= 0 }?.let { step(it, -1) }; true }
                code == KeyEvent.KEYCODE_DPAD_DOWN -> { vh.bindingAdapterPosition.takeIf { it >= 0 }?.let { step(it, 1) }; true }   // the last row stays put
                else -> false
            }
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.bound = e.key
        h.name.text = e.label
        h.name.typeface = Fonts.familyFor(e.key).first
        h.row.contentDescription = e.label
        h.row.setOnClickListener { v ->
            if (T.animated) v.animate().alpha(0.7f).setDuration(T.d(T.FAST - 40)).withEndAction { v.alpha = 1f; onClick(e) }.start() else onClick(e)
        }
        h.row.setOnLongClickListener { onLong(e); true }
    }

    override fun getItemCount() = items.size
}

/** Shrinks a little while OK is held, so a press is felt before the action runs. */
fun View.pressFeedback(down: Boolean) {
    if (!T.animated) return
    val k = if (down) 1f - 0.04f * T.motion else 1f
    animate().scaleX(k).scaleY(k).setDuration(T.d(if (down) 70 else 140)).setInterpolator(T.OUT).start()
}

class PressRow(ctx: Context) : LinearLayout(ctx) {
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (e.repeatCount == 0 && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) pressFeedback(true)
        return super.onKeyDown(code, e)
    }
    override fun onKeyUp(code: Int, e: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) pressFeedback(false)
        return super.onKeyUp(code, e)
    }
}

class Pill(ctx: Context) : TextView(ctx) {
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (e.repeatCount == 0 && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) pressFeedback(true)
        return super.onKeyDown(code, e)
    }
    override fun onKeyUp(code: Int, e: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) pressFeedback(false)
        return super.onKeyUp(code, e)
    }
}

/** A pill-shaped, remote-focusable action on the stage: glass at rest, solid white with a soft glow when focused. */
fun stagePill(ctx: Context, s: Scale, primary: Boolean, label: String, onClick: () -> Unit): TextView = Pill(ctx).apply {
    text = label; sizePx(s.px(28f)); typeface = Fonts.medium; isFocusable = true; isClickable = true; gravity = Gravity.CENTER
    setTextColor(T.TEXT); minimumHeight = s.px(64); setSingleLine()
    setPaddingRelative(s.px(38), s.px(14), s.px(38), s.px(14))
    val idle = if (primary) 0x40FFFFFF else T.GLASS_HI
    val rest = rounded(idle, s.px(T.R_PILL), T.HAIRLINE, s.px(1))
    val lit = rounded(Color.WHITE, s.px(T.R_PILL))
    background = rest
    if (android.os.Build.VERSION.SDK_INT >= 28) outlineSpotShadowColor = 0xFFFFFFFF.toInt()
    setOnFocusChangeListener { v, f ->
        background = if (f) lit else rest
        setTextColor(if (f) T.ON_ACCENT else T.TEXT)
        val sc = if (f && T.animated) 1f + T.FOCUS_LIFT * T.motion else 1f
        v.animate().scaleX(sc).scaleY(sc).translationZ(if (f && T.animated) s.px(14f) else 0f).setDuration(T.d(T.FOCUS)).setInterpolator(T.OUT).start()
    }
    setOnClickListener { onClick() }
}

/**
 * Shown on the stage while a scope tab (not an app) has focus: the scope's name in the display face with a
 * light that slowly sweeps across the letters, and a one-line summary.
 */
class ScopeHero(ctx: Context, private val s: Scale) : LinearLayout(ctx) {
    private val title = TextView(ctx).apply {
        sizePx(s.px(132f)); typeface = Fonts.display; includeFontPadding = false; maxLines = 1
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START; setTextColor(Color.WHITE)
    }
    private val sub = TextView(ctx).apply {
        sizePx(s.px(34f)); typeface = Fonts.light; setTextColor(T.TEXT_DIM); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }
    private val rule = View(ctx)
    private var accent = T.ACCENT
    private var sweep: android.animation.ValueAnimator? = null
    private val m = android.graphics.Matrix()
    private var shader: android.graphics.LinearGradient? = null

    init {
        orientation = VERTICAL
        addView(title, LayoutParams(-2, -2))
        addView(rule, LayoutParams(s.px(96), s.px(4)).apply { topMargin = s.px(30) })
        addView(sub, LayoutParams(-2, -2).apply { topMargin = s.px(22) })
    }

    fun set(name: String, summary: String, color: Int) {
        title.text = name; sub.text = summary; accent = color
        rule.background = rounded(color, s.px(2f))
        val w = s.px(900f).toFloat()
        shader = android.graphics.LinearGradient(0f, 0f, w, 0f,
            intArrayOf(0x88FFFFFF.toInt(), Color.WHITE, 0x88FFFFFF.toInt(), 0xFF000000.toInt() or (color and 0xFFFFFF), 0x88FFFFFF.toInt()),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), android.graphics.Shader.TileMode.MIRROR)
        title.paint.shader = shader
        title.invalidate()
        restart()
    }

    private fun restart() {
        sweep?.cancel()
        if (!T.animated || !isShown) return
        sweep = android.animation.ValueAnimator.ofFloat(0f, s.px(1800f).toFloat()).apply {
            duration = 6500; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { m.setTranslate(it.animatedValue as Float, 0f); shader?.setLocalMatrix(m); title.invalidate() }
            start()
        }
    }

    override fun onVisibilityChanged(v: View, vis: Int) { super.onVisibilityChanged(v, vis); if (vis == VISIBLE) restart() else sweep?.cancel() }
    override fun onDetachedFromWindow() { sweep?.cancel(); super.onDetachedFromWindow() }
}
