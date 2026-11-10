package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * One shelf of the home screen: a label above a horizontal row of artwork cards. Only the cards near the viewport exist
 * (RecyclerView), and each loads its artwork through the shared [IconLoader] at card size, cancelling when recycled.
 */
class ShelfRecycler(ctx: Context) : RecyclerView(ctx) {
    override fun hasOverlappingRendering() = false
}

/** Keeps the focused card on a fixed slot (the second one), so the cursor stays put and the shelf moves under it. */
class ShelfLayoutManager(ctx: Context, private val slot: Int) : LinearLayoutManager(ctx, HORIZONTAL, false) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean,
    ): Boolean {
        val step = child.width
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val k = minOf(getPosition(child), slot) * step
        val target = if (rtl) width - paddingRight - step - k else paddingLeft + k
        val dx = child.left - target
        if (dx == 0) return false
        if (immediate || !T.animated) parent.scrollBy(dx, 0) else parent.smoothScrollBy(dx, 0, T.OUT, T.d(T.MED + 40).toInt())
        return true
    }
}

/** What a shelf tells the home screen. Positions are indexes in that shelf's own list. */
class ShelfCallbacks(
    val onFocus: (AppEntry, Int, Int) -> Unit,
    val onClick: (AppEntry) -> Unit,
    val onLong: (AppEntry) -> Unit,
    val onVertical: (shelf: Int, pos: Int, delta: Int) -> Unit,
    val art: (AppEntry, Int, Int, (android.graphics.Bitmap?) -> Unit) -> IconLoader.Request,
)

/** A focusable card: scales with focus, and feels pressed while OK is held. */
class CardRoot(ctx: Context) : LinearLayout(ctx) {
    var rest = 1f
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (e.repeatCount == 0 && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && T.animated) animate().scaleX(rest * 0.96f).scaleY(rest * 0.96f).setDuration(T.d(70)).start()
        return super.onKeyDown(code, e)
    }
    override fun onKeyUp(code: Int, e: KeyEvent): Boolean {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && T.animated) animate().scaleX(rest).scaleY(rest).setDuration(T.d(140)).start()
        return super.onKeyUp(code, e)
    }
}

class CardAdapter(private val s: Scale, private val shelf: Int, val cardW: Int, private val cb: ShelfCallbacks) : RecyclerView.Adapter<CardAdapter.VH>() {
    var items: List<AppEntry> = emptyList(); private set
    private var recycler: RecyclerView? = null
    private var pending = -1
    override fun onAttachedToRecyclerView(rv: RecyclerView) { recycler = rv }
    override fun onDetachedFromRecyclerView(rv: RecyclerView) { if (recycler === rv) recycler = null }

    val cardH get() = cardW * 9 / 16

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

    /** Moves focus along the shelf by position, so a burst of presses never depends on a card being laid out yet. */
    private fun step(from: Int, delta: Int) {
        val rv = recycler ?: return
        val to = (if (pending >= 0) pending else from) + delta
        if (to < 0 || to >= items.size) return
        pending = to
        val holder = rv.findViewHolderForAdapterPosition(to)
        if (holder != null) holder.itemView.requestFocus() else { rv.scrollToPosition(to); focusLater(rv, to, 4) }
    }

    private fun focusLater(rv: RecyclerView, to: Int, tries: Int) {
        rv.post {
            val h = rv.findViewHolderForAdapterPosition(to)
            when {
                pending != to -> {}
                h != null -> h.itemView.requestFocus()
                tries > 0 -> focusLater(rv, to, tries - 1)
                else -> pending = -1
            }
        }
    }

    class VH(val root: CardRoot, val art: ImageView, val name: TextView) : RecyclerView.ViewHolder(root) { var req: IconLoader.Request? = null; var bound: String? = null }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
        val ctx = parent.context
        val gap = s.px(24)
        val radius = s.px(T.R_CARD)
        val art = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val ring = View(ctx).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(Color.TRANSPARENT, radius, T.RING, s.px(T.RING_PX)))
                addState(intArrayOf(), rounded(Color.TRANSPARENT, radius, 0x22FFFFFF, s.px(1)))
            }
            isDuplicateParentStateEnabled = true
        }
        val box = FrameLayout(ctx).apply {
            background = rounded(T.CARD, radius); clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
            addView(art, FrameLayout.LayoutParams(-1, -1)); addView(ring, FrameLayout.LayoutParams(-1, -1))
        }
        val name = TextView(ctx).apply {
            sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; typeface = Fonts.medium; alpha = 0.6f
        }
        val root = CardRoot(ctx).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; isLongClickable = true; clipChildren = false
            layoutParams = RecyclerView.LayoutParams(cardW + gap, -2)
            setPaddingRelative(gap / 2, 0, gap / 2, 0)
            addView(box, LinearLayout.LayoutParams(cardW, cardH))
            addView(name, LinearLayout.LayoutParams(cardW, -2).apply { topMargin = s.px(12) })
        }
        val vh = VH(root, art, name)
        root.setOnFocusChangeListener { v, f ->
            val sc = if (f && T.animated) 1f + 0.08f * T.motion else 1f
            root.rest = sc
            v.pivotX = v.width / 2f; v.pivotY = cardH / 2f
            v.animate().scaleX(sc).scaleY(sc).translationZ(if (f && T.animated) s.px(16f) else 0f).setDuration(T.d(T.FOCUS)).setInterpolator(T.OUT).start()
            name.animate().alpha(if (f) 1f else 0.6f).setDuration(T.d(T.FOCUS)).start()
            vh.bound?.let { k -> val fam = Fonts.familyFor(k); name.typeface = if (f) fam.second else fam.first }
            if (f) { val p = vh.bindingAdapterPosition; if (p == pending) pending = -1; items.getOrNull(p)?.let { cb.onFocus(it, shelf, p) } }
        }
        root.setOnKeyListener { _, code, e ->
            val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val before = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            val after = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val p = vh.bindingAdapterPosition
            if (e.action != KeyEvent.ACTION_DOWN || p < 0) false
            else when (code) {
                before -> { step(p, -1); true }                  // the ends hold still; nothing else lives beside a shelf
                after -> { step(p, 1); true }
                KeyEvent.KEYCODE_DPAD_UP -> { cb.onVertical(shelf, p, -1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { cb.onVertical(shelf, p, 1); true }
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
        h.root.contentDescription = e.label
        h.root.setOnClickListener { cb.onClick(e) }
        h.root.setOnLongClickListener { cb.onLong(e); true }
        h.req?.cancel(); h.art.setImageDrawable(null)
        val key = e.key
        h.req = cb.art(e, cardW, cardH) { bmp -> if (h.bound == key && bmp != null) h.art.setImageBitmap(bmp) }
    }

    override fun onViewRecycled(h: VH) { h.req?.cancel(); h.req = null; h.bound = null; h.art.setImageDrawable(null) }
    override fun getItemCount() = items.size
}

/**
 * The three shelves (Favorites, Recent, Library) stacked under the hero. Two show at a time; moving to the third
 * slides the stack up by one shelf. An empty shelf keeps its label and a quiet one-line hint and is skipped by Up/Down.
 */
class ShelfStack(ctx: Context, private val s: Scale, titles: List<String>, hints: List<String>, cardW: Int, private val cb: ShelfCallbacks) : LinearLayout(ctx) {
    private val adapters = titles.indices.map { CardAdapter(s, it, cardW, cb) }
    private val recyclers = mutableListOf<ShelfRecycler>()
    private val labels = mutableListOf<TextView>()
    private val hintViews = mutableListOf<TextView>()
    private val shelves = mutableListOf<LinearLayout>()
    private var offsetShelf = -1
    private var cur = 0
    private val emptyHint = titles.indices.map { hints[it].isEmpty() }
    val last get() = adapters.lastIndex

    init {
        orientation = VERTICAL; clipChildren = false; clipToPadding = false
        titles.forEachIndexed { i, title ->
            val label = TextView(ctx).apply {
                text = title; sizePx(s.px(T.LABEL + 4)); typeface = Fonts.medium; setTextColor(T.TEXT_DIM); maxLines = 1
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START; setPaddingRelative(s.px(12), 0, 0, 0)
            }
            val rv = ShelfRecycler(ctx).apply {
                layoutManager = ShelfLayoutManager(ctx, 1); adapter = adapters[i]
                clipChildren = false; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
                setPaddingRelative(s.px(12), s.px(18), s.px(T.MARGIN), s.px(26))
                itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false; addDuration = T.d(T.MED); removeDuration = T.d(T.FAST); moveDuration = T.d(T.MED) }
            }
            val hint = TextView(ctx).apply {
                text = hints[i]; sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM); typeface = Fonts.light; visibility = View.GONE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START; setPaddingRelative(s.px(12), s.px(22), s.px(T.MARGIN), s.px(22))
            }
            val shelf = LinearLayout(ctx).apply {
                orientation = VERTICAL; clipChildren = false
                addView(label, LayoutParams(-1, -2)); addView(rv, LayoutParams(-1, -2)); addView(hint, LayoutParams(-1, -2))
            }
            recyclers += rv; labels += label; hintViews += hint; shelves += shelf
            addView(shelf, LayoutParams(-1, -2))
        }
    }

    /** The stack is taller than the viewport that clips it: measure it with all the room it wants, not what is left. */
    override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

    fun size(shelf: Int) = adapters[shelf].items.size
    fun items(shelf: Int) = adapters[shelf].items
    fun anyApps() = adapters.any { it.items.isNotEmpty() }

    fun submit(shelf: Int, list: List<AppEntry>) {
        val rv = recyclers[shelf]
        val apply = {
            adapters[shelf].submit(list); rv.visibility = if (list.isEmpty()) GONE else VISIBLE; hintViews[shelf].visibility = if (list.isEmpty()) VISIBLE else GONE
            shelves[shelf].visibility = if (list.isEmpty() && emptyHint[shelf]) GONE else VISIBLE      // an empty shelf with nothing to say folds away
            offsetShelf = -1; post { reveal(cur) }
            Unit
        }
        if (rv.isComputingLayout) rv.post(apply) else apply()
    }

    /** The nearest shelf that has apps, looking from [from] in [dir] first and then the other way; -1 when all are empty. */
    fun nonEmpty(from: Int, dir: Int = 1): Int {
        var i = from.coerceIn(0, last)
        while (i in 0..last) { if (size(i) > 0) return i; i += dir }
        i = from.coerceIn(0, last) - dir
        while (i in 0..last) { if (size(i) > 0) return i; i -= dir }
        return -1
    }

    /** Puts focus on a card. The card may not exist yet right after a list or screen change, so it retries briefly. */
    fun focus(shelf: Int, pos: Int, tries: Int = 14) {
        val sh = nonEmpty(shelf).takeIf { it >= 0 } ?: return
        val n = size(sh); val i = pos.coerceIn(0, n - 1)
        reveal(sh)
        val rv = recyclers[sh]
        rv.scrollToPosition(i)
        rv.post {
            val v = rv.findViewHolderForAdapterPosition(i)?.itemView
            if (v != null && v.isShown && v.requestFocus()) return@post
            if (tries > 0) rv.postDelayed({ focus(sh, i, tries - 1) }, 50)
        }
    }

    /** Slides the stack so the focused shelf is one of the two on screen; folded-away shelves do not count. */
    fun reveal(shelf: Int) {
        cur = shelf
        val shown = shelves.indices.filter { shelves[it].visibility == VISIBLE }
        val pos = shown.indexOf(shelf).coerceAtLeast(0)
        val first = if (pos >= 2) pos - 1 else 0
        if (first == offsetShelf && translationY == targetY(shown, first)) return
        offsetShelf = first
        animate().cancel()
        val y = targetY(shown, first)
        if (T.animated) animate().translationY(y).setDuration(T.d(T.MED)).setInterpolator(T.OUT).start() else translationY = y
        shelves.forEachIndexed { i, v ->
            val p = shown.indexOf(i); val on = p in first..first + 1
            v.animate().cancel(); if (T.animated) v.animate().alpha(if (on) 1f else 0f).setDuration(T.d(T.MED)).start() else v.alpha = if (on) 1f else 0f
        }
    }

    private fun targetY(shown: List<Int>, first: Int) = if (first == 0 || shown.isEmpty()) 0f else -shelves[shown[first]].top.toFloat()

    fun markFocused(shelf: Int) { labels.forEachIndexed { i, l -> l.setTextColor(if (i == shelf) T.TEXT else T.TEXT_DIM) } }
    fun tint(shelf: Int, color: Int) { labels.getOrNull(shelf)?.setTextColor(legible(color)) }
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

/** An accent colour lightened toward white so small text in it stays readable over a photograph. */
fun legible(c: Int): Int = androidx.core.graphics.ColorUtils.blendARGB(c, Color.WHITE, 0.45f)
