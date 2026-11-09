package dev.l1avzh.haven

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos

/** A glanceable readout. Everything is computed on the device: no permissions, no network. */
class WidgetDef(val id: String, val nameRes: Int, val read: (Context) -> Reading)
class Reading(val value: String, val fraction: Float? = null, val showLabel: Boolean = true, val label: String? = null, val sky: Triple<Sky, Boolean, Boolean>? = null)

object Widgets {
    val ALL = listOf(
        WidgetDef("greet", R.string.w_greeting) { c ->
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            Reading(c.getString(when { h in 5..11 -> R.string.greet_morning; h in 12..17 -> R.string.greet_afternoon; h in 18..22 -> R.string.greet_evening; else -> R.string.greet_night }), null, false)
        },
        WidgetDef("weather", R.string.w_weather) { c ->
            val p = Prefs(c)
            val city = Weather.CITIES.getOrNull(p.weatherCity - 1)
            if (city == null) Reading(c.getString(R.string.weather_pick), null, true, c.getString(R.string.w_weather))
            else {
                val w = Weather.cached(p)
                Weather.refresh(c) { WidgetStrip.refreshAll() }
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                Reading(if (w == null) "…" else "${Weather.format(p, w.first)} ${Weather.describe(c, w.second)}", null, true, Weather.displayName(c, p, p.weatherCity - 1),
                    if (w == null) null else Triple(skyFor(w.second), !Mood.resolve(c, p).night, w.first >= 28))
            }
        },
        WidgetDef("day", R.string.w_day) { _ ->
            val n = Calendar.getInstance()
            val f = (n.get(Calendar.HOUR_OF_DAY) * 60 + n.get(Calendar.MINUTE)) / 1440f
            Reading("${(f * 100).toInt()}%", f)
        },
        WidgetDef("year", R.string.w_year) { _ ->
            val n = Calendar.getInstance()
            val f = (n.get(Calendar.DAY_OF_YEAR) - 1f) / n.getActualMaximum(Calendar.DAY_OF_YEAR)
            Reading("${(f * 100).toInt()}%", f)
        },
        WidgetDef("moon", R.string.w_moon) { _ ->
            val days = (System.currentTimeMillis() - 947182440000L) / 86_400_000.0 // new moon, 6 Jan 2000 18:14 UTC
            val age = ((days % 29.530588853) + 29.530588853) % 29.530588853 / 29.530588853
            val lit = ((1 - cos(2 * PI * age)) / 2).toFloat()
            Reading("${(lit * 100).toInt()}%", lit)
        },
        WidgetDef("storage", R.string.w_storage) { c ->
            val st = StatFs(Environment.getDataDirectory().path)
            Reading(Formatter.formatShortFileSize(c, st.availableBytes), 1f - st.availableBytes.toFloat() / st.totalBytes)
        },
        WidgetDef("memory", R.string.w_memory) { c ->
            val mi = ActivityManager.MemoryInfo()
            (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            Reading(Formatter.formatShortFileSize(c, mi.availMem), 1f - mi.availMem.toFloat() / mi.totalMem)
        },
        WidgetDef("uptime", R.string.w_uptime) { _ ->
            val m = SystemClock.elapsedRealtime() / 60_000
            Reading(if (m >= 1440) "${m / 1440}d ${m % 1440 / 60}h" else if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m")
        },
    )

    fun parse(csv: String): List<String> = csv.split(',').filter { id -> ALL.any { it.id == id } }
    fun toggle(csv: String, id: String, on: Boolean): String =
        ALL.map { it.id }.filter { if (it == id) on else it in parse(csv) }.joinToString(",")
}

private class Bar(ctx: Context, private val w: Int, private val h: Int) : View(ctx) {
    var fraction = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(a: Int, b: Int) = setMeasuredDimension(w, h)
    override fun onDraw(c: Canvas) {
        val r = h / 2f
        p.color = 0x33FFFFFF; c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, p)
        p.color = 0xCCFFFFFF.toInt(); c.drawRoundRect(0f, 0f, (w * fraction).coerceAtLeast(h.toFloat()), h.toFloat(), r, r, p)
    }
    override fun hasOverlappingRendering() = false
}

/**
 * A row of widget cards for one to [MAX_HOME]/[MAX_SAVER] widgets. Card width is derived from the space the parent offers and the
 * number of widgets, so one widget gets a roomy card and four share the row without ever overflowing; long text shrinks to fit
 * instead of being cut. Each type has its own treatment: weather shows its animated icon, share-style widgets a progress bar,
 * the greeting and uptime only text. Refreshes itself every 30 s while on screen.
 */
class WidgetStrip(ctx: Context, private val s: Scale, ids: List<String>, private val large: Boolean) : LinearLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private class Tile(val def: WidgetDef, val card: LinearLayout, val label: TextView, val value: TextView, val bar: Bar, val icon: WeatherIcon?)
    companion object {
        const val MAX_HOME = 3
        const val MAX_SAVER = 4
        private val live = java.util.WeakHashMap<WidgetStrip, Unit>()
        fun refreshAll() { live.keys.toList().forEach { it.refresh() } }

        /** Width of one card in a row of [count] inside [avail] pixels: shared evenly, never wider than [maxCard], never negative. */
        fun cardWidth(avail: Int, count: Int, gap: Int, maxCard: Int): Int =
            if (count <= 0) 0 else ((avail - gap * (count - 1)) / count).coerceIn(0, maxCard)
    }
    private val tiles = ArrayList<Tile>()
    private val gap = s.px(if (large) 28 else 20)
    private val maxCard = s.px(if (large) 440 else 300)
    private val tick = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 30_000) } }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false
        val defs = ids.mapNotNull { id -> Widgets.ALL.firstOrNull { it.id == id } }.take(if (large) MAX_SAVER else MAX_HOME)
        val padH = s.px(if (large) 26 else 18); val padV = s.px(if (large) 22 else 8)
        defs.forEachIndexed { i, d ->
            val first = d.read(ctx)
            val label = TextView(ctx).apply {
                Caps.set(this, first.label ?: ctx.getString(d.nameRes), 0.14f); sizePx(s.px(if (large) 17f else 13f)); setTextColor(T.TEXT_DIM); typeface = Fonts.medium
                letterSpacing = 0.12f; includeFontPadding = false; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val value = TextView(ctx).apply {
                val big = s.px(if (large) 44f else 27f)
                typeface = Fonts.light; setTextColor(T.TEXT); includeFontPadding = false
                maxLines = if (d.id == "weather" || d.id == "greet") 2 else 1
                sizePx(big)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, s.px(14f).toInt(), big.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_PX)
            }
            val bar = Bar(ctx, s.px(if (large) 160 else 110), s.px(if (large) 4 else 3))
            val icon = if (d.id == "weather") WeatherIcon(ctx, s.px(if (large) 88 else 50)) else null
            val col = LinearLayout(ctx).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL
                if (first.showLabel) addView(label, LayoutParams(-1, -2))
                addView(value, LayoutParams(-1, -2).apply { topMargin = s.px(if (first.showLabel) (if (large) 6 else 2) else 0) })
                if (first.fraction != null) addView(bar, LayoutParams(-2, -2).apply { topMargin = s.px(if (large) 10 else 6) })
            }
            val card = LinearLayout(ctx).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(padH, padV, padH, padV)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = s.px(24f).toFloat(); setColor(T.GLASS); setStroke(s.px(1), T.HAIRLINE) }
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                if (icon != null) addView(icon, LayoutParams(-2, -2).apply { marginEnd = s.px(14) })
                addView(col, LayoutParams(0, -2, 1f))
            }
            addView(card, LayoutParams(0, -1).apply { if (i > 0) marginStart = gap })
            tiles.add(Tile(d, card, label, value, bar, icon))
        }
        refresh()
    }

    /** Hands each card an even share of the offered width, so layouts hold for any widget count, language or font scale. */
    override fun onMeasure(w: Int, h: Int) {
        val avail = if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED) maxCard * tiles.size + gap * tiles.size else MeasureSpec.getSize(w)
        val each = cardWidth(avail, tiles.size, gap, maxCard)
        tiles.forEach { (it.card.layoutParams as LayoutParams).apply { width = each; weight = 0f } }
        super.onMeasure(MeasureSpec.makeMeasureSpec(each * tiles.size + gap * (tiles.size - 1).coerceAtLeast(0), MeasureSpec.EXACTLY), h)
    }

    private fun refresh() {
        tiles.forEach { t ->
            val r = try { t.def.read(context) } catch (_: Exception) { return@forEach }
            t.value.text = r.value
            val name = r.label ?: context.getString(t.def.nameRes)
            r.label?.let { Caps.set(t.label, it, 0.14f) }
            t.card.contentDescription = if (r.showLabel) "$name: ${r.value}" else r.value
            r.sky?.let { t.icon?.set(it.first, it.second, it.third) }
            t.icon?.visibility = if (r.sky == null) View.GONE else View.VISIBLE
            if (t.def.id == "weather") LiveBackground.ambient = r.sky?.first?.takeIf { it == Sky.RAIN || it == Sky.SNOW || it == Sky.STORM }
            r.fraction?.let { t.bar.fraction = it.coerceIn(0f, 1f); t.bar.invalidate() }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); live[this] = Unit; handler.removeCallbacks(tick); handler.postDelayed(tick, 30_000); refresh() }
    override fun onDetachedFromWindow() { live.remove(this); handler.removeCallbacks(tick); super.onDetachedFromWindow() }
}
