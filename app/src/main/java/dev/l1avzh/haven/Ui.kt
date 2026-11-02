package dev.l1avzh.haven

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** The UI is laid out on a 1920x1080 grid and scaled to the real window, whatever the density says. */
class Scale(ctx: Context) {
    private val k = ctx.resources.displayMetrics.widthPixels / 1920f
    fun px(v: Int) = (v * k).toInt()
    fun px(v: Float) = v * k
}

fun rounded(color: Int, radius: Float, strokeColor: Int = 0, stroke: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun TextView.sizePx(v: Float) = setTextSize(TypedValue.COMPLEX_UNIT_PX, v)

/** Overlapping rendering is off so alpha is applied per draw op instead of through an offscreen layer. */
class Block(ctx: Context) : LinearLayout(ctx) { override fun hasOverlappingRendering() = false }
class Row(ctx: Context) : RecyclerView(ctx) {
    override fun hasOverlappingRendering() = false

    /** Left/Right at the end of a shelf stays put instead of jumping to the other shelf. */
    override fun focusSearch(focused: View, direction: Int): View? {
        val r = super.focusSearch(focused, direction)
        if ((direction == FOCUS_LEFT || direction == FOCUS_RIGHT) && (r == null || r.parent !== this)) return focused
        return r
    }
}
class Cell(ctx: Context) : FrameLayout(ctx) { override fun hasOverlappingRendering() = false }

/** Keeps the focused tile on a fixed slot (second from the left) so the row glides under a stable cursor. */
class AnchorLayoutManager(ctx: Context, private val cell: Int) : LinearLayoutManager(ctx, HORIZONTAL, false) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean,
    ): Boolean {
        val off = if (getPosition(child) == 0) 0 else cell
        // Screen coordinates: in RTL the row starts at the right edge, so the slot is measured from there.
        val dx = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) child.right - (width - paddingRight - off)
                 else child.left - (paddingLeft + off)
        if (dx == 0) return false
        if (immediate || !T.animated) parent.scrollBy(dx, 0)
        else parent.smoothScrollBy(dx, 0, T.OUT, T.d(T.MED + 60).toInt())
        return true
    }
}

/** Decodes banners/icons off the main thread into fixed-size bitmaps and remembers each app's accent colour. */
class IconLoader(private val pm: PackageManager) {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }
    private val accents = ConcurrentHashMap<String, Int>()
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun accent(key: String): Int? = accents[key]

    fun load(e: AppEntry, w: Int, h: Int, done: (Bitmap?) -> Unit) {
        val k = "${e.key}@${w}x$h"
        cache.get(k)?.let { done(it); return }
        pool.execute {
            val bmp = try { render(e, w, h) } catch (_: Exception) { null }
            if (bmp != null) cache.put(k, bmp)
            main.post { done(bmp) }
        }
    }

    private fun render(e: AppEntry, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (e.virtual) { drawShield(c, w, h); accents[e.key] = ADGUARD_GREEN; return out }
        val info = pm.getActivityInfo(e.component, 0)
        val banner = info.loadBanner(pm)
        if (banner != null) {
            // Fill the card, cropping to keep the aspect ratio.
            val bw = banner.intrinsicWidth.coerceAtLeast(1); val bh = banner.intrinsicHeight.coerceAtLeast(1)
            val s = maxOf(w / bw.toFloat(), h / bh.toFloat())
            val dw = (bw * s).toInt(); val dh = (bh * s).toInt()
            banner.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
            banner.draw(c)
            accents[e.key] = accentOf(out)
        } else {
            val icon = info.loadIcon(pm)
            val probe = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 32, 32); icon.draw(Canvas(probe))
            val accent = accentOf(probe)
            accents[e.key] = accent
            // Dark tint of the icon's own colour, so icon-only apps still feel like part of the artwork.
            val hsv = FloatArray(3); Color.colorToHSV(accent, hsv)
            val top = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.7f, 0.34f))
            val bottom = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.6f, 0.14f))
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(),
                Paint().apply { shader = LinearGradient(0f, 0f, w * 0.6f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
            val s = (h * 0.58f).toInt()
            icon.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2)
            icon.draw(c)
        }
        return out
    }

    /** Original generic shield-and-check glyph for the "get AdGuard" shortcut (not the AdGuard logo). */
    private fun drawShield(c: Canvas, w: Int, h: Int) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.7f, h.toFloat(), 0xFF173B24.toInt(), 0xFF0B1A12.toInt(), Shader.TileMode.CLAMP)
        })
        val s = h * 0.56f; val cx = w / 2f; val top = (h - s) / 2f
        val shield = android.graphics.Path().apply {
            moveTo(cx, top); lineTo(cx + s * 0.42f, top + s * 0.16f); lineTo(cx + s * 0.42f, top + s * 0.52f)
            cubicTo(cx + s * 0.42f, top + s * 0.78f, cx + s * 0.2f, top + s * 0.92f, cx, top + s)
            cubicTo(cx - s * 0.2f, top + s * 0.92f, cx - s * 0.42f, top + s * 0.78f, cx - s * 0.42f, top + s * 0.52f)
            lineTo(cx - s * 0.42f, top + s * 0.16f); close()
        }
        c.drawPath(shield, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ADGUARD_GREEN })
        val tick = android.graphics.Path().apply {
            moveTo(cx - s * 0.2f, top + s * 0.5f); lineTo(cx - s * 0.05f, top + s * 0.65f); lineTo(cx + s * 0.22f, top + s * 0.34f)
        }
        c.drawPath(tick, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.1f; color = 0xFF0B1A12.toInt()
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
    }

    /** Saturation-weighted average, so a red logo on a white banner yields red rather than grey. */
    private fun accentOf(bmp: Bitmap): Int {
        val sm = Bitmap.createScaledBitmap(bmp, 16, 9, true)
        val hsv = FloatArray(3)
        var r = 0.0; var g = 0.0; var b = 0.0; var wt = 0.0
        for (y in 0 until sm.height) for (x in 0 until sm.width) {
            val px = sm.getPixel(x, y)
            if (Color.alpha(px) < 128) continue
            Color.colorToHSV(px, hsv)
            val weight = hsv[1] * hsv[2] + 0.02
            r += Color.red(px) * weight; g += Color.green(px) * weight; b += Color.blue(px) * weight; wt += weight
        }
        if (wt == 0.0) return T.ACCENT
        Color.colorToHSV(Color.rgb((r / wt).toInt(), (g / wt).toInt(), (b / wt).toInt()), hsv)
        if (hsv[1] < 0.12f) return T.ACCENT
        hsv[1] = hsv[1].coerceAtLeast(0.55f); hsv[2] = hsv[2].coerceAtLeast(0.85f)
        return Color.HSVToColor(hsv)
    }
}

/**
 * A banner tile. Focus = lift + scale + white edge + a coloured shadow taken from the app's own artwork.
 * No text: the home screen's hero area names the focused app.
 */
class TileAdapter(
    private val s: Scale, private val icons: IconLoader,
    private val w: Int, private val gap: Int, private val focusScale: Float,
    private val onClick: (AppEntry) -> Unit, private val onLong: (AppEntry) -> Unit,
    private val onArtReady: (AppEntry) -> Unit,
) : RecyclerView.Adapter<TileAdapter.VH>() {

    var items: List<AppEntry> = emptyList(); private set

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

    private val pw get() = s.px(w)
    private val ph get() = s.px(w) * 9 / 16
    val cellWidth get() = pw + s.px(gap)

    class VH(val root: Cell, val card: FrameLayout, val img: ImageView) : RecyclerView.ViewHolder(root) {
        var bound: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
        val ctx = parent.context
        val radius = s.px(T.R_CARD)
        val card = FrameLayout(ctx).apply {
            background = rounded(T.CARD, radius)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) }
            }
        }
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_XY }
        card.addView(img, FrameLayout.LayoutParams(-1, -1))
        val root = Cell(ctx).apply {
            isFocusable = true; isClickable = true; isLongClickable = true
            val hp = s.px(gap) / 2
            setPadding(hp, s.px(26), hp, s.px(26))
            layoutParams = RecyclerView.LayoutParams(pw + s.px(gap), ph + s.px(52))
        }
        root.addView(card, FrameLayout.LayoutParams(pw, ph))
        val vh = VH(root, card, img)
        root.setOnFocusChangeListener { _, f -> applyFocus(vh, f, animate = true) }
        return vh
    }

    private fun applyFocus(h: VH, focused: Boolean, animate: Boolean) {
        val sc = if (focused && T.animated) 1f + (focusScale - 1f) * T.motion else 1f
        val al = if (focused) 1f else 0.82f
        h.card.foreground = if (focused) rounded(Color.TRANSPARENT, s.px(T.R_CARD), Color.WHITE, s.px(3)) else null
        h.root.translationZ = if (focused) s.px(10f) else 0f
        if (animate && T.animated) {
            h.root.animate().scaleX(sc).scaleY(sc).alpha(al).setDuration(T.d(T.MED)).setInterpolator(T.OUT).start()
        } else {
            h.root.animate().cancel(); h.root.scaleX = sc; h.root.scaleY = sc; h.root.alpha = al
        }
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.bound = e.key
        h.root.contentDescription = e.label
        h.img.setImageDrawable(null)
        applyFocus(h, h.root.isFocused, animate = false)
        icons.load(e, pw, ph) { bmp ->
            if (h.bound == e.key && bmp != null) {
                h.img.setImageBitmap(bmp)
                onArtReady(e)
            }
        }
        h.root.setOnClickListener { v ->
            if (T.animated) v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(T.d(T.FAST - 40))
                .withEndAction { onClick(e) }.start()
            else onClick(e)
        }
        h.root.setOnLongClickListener { onLong(e); true }
    }

    override fun getItemCount() = items.size
}

const val ADGUARD_GREEN = 0xFF68BC71.toInt()

class MenuItem(val text: String, val hint: String? = null, val action: (Dialog) -> Unit)

/** Remote-friendly menu on a glass surface with edge lighting; Back dismisses. */
fun Activity.showMenu(s: Scale, title: String, items: List<MenuItem>, focus: Int = 0, extra: ((LinearLayout, Dialog) -> Unit)? = null): Dialog {
    lateinit var box: LinearLayout
    var closing = false
    val d = object : Dialog(this) {
        /** Fades out instead of vanishing, like everything else in the launcher. */
        override fun dismiss() {
            if (!T.animated || !isShowing || closing) { if (!closing) super.dismiss(); return }
            closing = true
            box.animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).setDuration(T.d(T.FAST)).withEndAction { super.dismiss() }.start()
        }
    }
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    d.window?.setDimAmount(0.55f)
    box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(T.SURFACE, s.px(T.R_MENU), 0x1FFFFFFF, s.px(1))
        setPadding(s.px(36), s.px(30), s.px(36), s.px(30))
    }
    box.addView(TextView(this).apply {
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START; text = title; sizePx(s.px(T.TITLE)); setTextColor(T.TEXT); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPaddingRelative(s.px(8), 0, 0, s.px(16))
    })
    extra?.invoke(box, d)
    // Long menus (the language list) scroll; focus moves the scroll position automatically.
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val scroller = object : android.widget.ScrollView(this) {
        override fun onMeasure(w: Int, h: Int) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(h).takeIf { it > 0 } ?: Int.MAX_VALUE, s.px(780)), View.MeasureSpec.AT_MOST))
        }
    }.apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(list) }
    box.addView(scroller, LinearLayout.LayoutParams(-1, -2))
    val rows = items.map { item ->
        val bar = View(this).apply { background = rounded(T.ACCENT, s.px(3f)); alpha = 0f }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPaddingRelative(s.px(14), s.px(16), s.px(24), s.px(16))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x26FFFFFF, s.px(T.R_ROW)))
                addState(intArrayOf(), rounded(Color.TRANSPARENT, s.px(T.R_ROW)))
            }
            setOnClickListener { item.action(d) }
        }
        row.addView(bar, LinearLayout.LayoutParams(s.px(4), s.px(30)).apply { marginEnd = s.px(14) })
        val label = TextView(this).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START; text = item.text; sizePx(s.px(T.BODY)); setTextColor(T.TEXT) }
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        val hv = item.hint?.let { h -> TextView(this).apply { text = h; sizePx(s.px(T.BODY - 2)); setTextColor(T.TEXT_DIM); setPaddingRelative(s.px(16), 0, 0, 0) }.also { row.addView(it) } }
        row.setOnFocusChangeListener { _, f ->
            bar.animate().alpha(if (f) 1f else 0f).setDuration(T.d(T.FAST)).start()
            label.setTextColor(if (f) Color.WHITE else T.TEXT)
            hv?.setTextColor(if (f) T.TEXT else T.TEXT_DIM)
        }
        list.addView(row, LinearLayout.LayoutParams(-1, -2))
        row
    }
    d.setContentView(box, ViewGroup.LayoutParams(s.px(780), -2))
    if (T.animated) { box.alpha = 0f; box.scaleX = 0.96f; box.scaleY = 0.96f; box.translationY = s.px(18f) }
    d.setOnShowListener {
        rows.getOrNull(focus)?.requestFocus()
        if (T.animated) box.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(T.d(T.MED)).setInterpolator(T.OUT).start()
    }
    d.show()
    return d
}
