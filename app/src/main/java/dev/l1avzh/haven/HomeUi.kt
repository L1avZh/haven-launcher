package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    fun submit(list: List<AppEntry>) {
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
        val dot = View(ctx).apply { background = dotShape(0x66FFFFFF); alpha = 0f }
        val name = TextView(ctx).apply {
            sizePx(s.px(46f)); setTextColor(T.TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; typeface = Fonts.light; includeFontPadding = false; alpha = 0.42f
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; isLongClickable = true
            layoutParams = RecyclerView.LayoutParams(-1, s.px(92))
        }
        row.addView(dot, LinearLayout.LayoutParams(s.px(14), s.px(14)).apply { marginEnd = s.px(22) })
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val vh = VH(row, dot, name)
        row.setOnFocusChangeListener { _, f ->
            val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
            name.pivotX = if (rtl) name.width.toFloat() else 0f; name.pivotY = name.height / 2f
            val sc = if (f && T.animated) 1f + 0.16f * T.motion else 1f
            name.animate().scaleX(sc).scaleY(sc).alpha(if (f) 1f else 0.42f).setDuration(T.d(T.MED)).setInterpolator(T.OUT).start()
            dot.animate().alpha(if (f) 1f else 0f).setDuration(T.d(T.FAST)).start()
            name.typeface = if (f) Fonts.regular else Fonts.light
            if (f) { focusedHolder = vh; val p = vh.bindingAdapterPosition; items.getOrNull(p)?.let { onFocus(it, p) } }
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
                else -> false
            }
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.bound = e.key
        h.name.text = e.label
        h.row.contentDescription = e.label
        h.row.setOnClickListener { v ->
            if (T.animated) v.animate().alpha(0.7f).setDuration(T.d(T.FAST - 40)).withEndAction { v.alpha = 1f; onClick(e) }.start() else onClick(e)
        }
        h.row.setOnLongClickListener { onLong(e); true }
    }

    override fun getItemCount() = items.size
}

/** A pill-shaped, remote-focusable action on the stage. */
fun stagePill(ctx: Context, s: Scale, primary: Boolean, label: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
    text = label; sizePx(s.px(28f)); typeface = Fonts.medium; isFocusable = true; isClickable = true; gravity = Gravity.CENTER
    setTextColor(T.TEXT); minimumHeight = s.px(64); setSingleLine()
    setPaddingRelative(s.px(38), s.px(14), s.px(38), s.px(14))
    val idle = if (primary) 0x40FFFFFF else 0x1FFFFFFF
    background = rounded(idle, s.px(40f))
    setOnFocusChangeListener { v, f ->
        background = rounded(if (f) Color.WHITE else idle, s.px(40f))
        setTextColor(if (f) T.ON_ACCENT else T.TEXT)
        val sc = if (f && T.animated) 1f + 0.06f * T.motion else 1f
        v.animate().scaleX(sc).scaleY(sc).setDuration(T.d(T.FAST)).setInterpolator(T.OUT).start()
    }
    setOnClickListener { onClick() }
}
