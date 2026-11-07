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
                Reading(if (w == null) "…" else "${Weather.format(p, w.first)} ${Weather.describe(c, w.second)}", null, true, city.name,
                    if (w == null) null else Triple(skyFor(w.second), h in 6..18, w.first >= 28))
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

/** A row of widgets. Refreshes itself every 30 s while on screen. */
class WidgetStrip(ctx: Context, s: Scale, ids: List<String>, large: Boolean) : LinearLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private class Tile(val def: WidgetDef, val label: TextView, val value: TextView, val bar: Bar, val icon: WeatherIcon?)
    companion object {
        private val live = java.util.WeakHashMap<WidgetStrip, Unit>()
        fun refreshAll() { live.keys.toList().forEach { it.refresh() } }
    }
    private val tiles = ArrayList<Tile>()
    private val tick = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 30_000) } }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        ids.mapNotNull { id -> Widgets.ALL.firstOrNull { it.id == id } }.forEachIndexed { i, d ->
            val label = TextView(ctx).apply {
                Caps.set(this, d.read(ctx).label ?: ctx.getString(d.nameRes), 0.14f); sizePx(s.px(if (large) 17f else 14f)); setTextColor(T.TEXT_DIM); typeface = Fonts.medium
                letterSpacing = 0.12f; includeFontPadding = false
            }
            val value = TextView(ctx).apply { sizePx(s.px(if (large) 40f else 30f)); setTextColor(T.TEXT); typeface = Fonts.light; includeFontPadding = false; maxLines = 1 }
            val bar = Bar(ctx, s.px(if (large) 150 else 104), s.px(4))
            val icon = if (d.id == "weather") WeatherIcon(ctx, s.px(if (large) 96 else 72)) else null
            val col = LinearLayout(ctx).apply {
                orientation = VERTICAL
                val reading = d.read(ctx)
                if (reading.showLabel) addView(label)
                addView(value, LayoutParams(-2, -2).apply { topMargin = s.px(if (reading.showLabel) 4 else 0) })
                if (reading.fraction != null) addView(bar, LayoutParams(-2, -2).apply { topMargin = s.px(8) })
            }
            val cell = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; if (icon != null) addView(icon, LayoutParams(-2, -2).apply { marginEnd = s.px(14) }); addView(col) }
            addView(cell, LayoutParams(-2, -2).apply { if (i > 0) marginStart = s.px(if (large) 64 else 44) })
            tiles.add(Tile(d, label, value, bar, icon))
        }
        refresh()
    }

    private fun refresh() {
        tiles.forEach { t ->
            val r = try { t.def.read(context) } catch (_: Exception) { return@forEach }
            t.value.text = r.value
            r.label?.let { Caps.set(t.label, it, 0.14f) }
            r.sky?.let { t.icon?.set(it.first, it.second, it.third) }
            t.icon?.visibility = if (r.sky == null) View.GONE else View.VISIBLE
            if (t.def.id == "weather") LiveBackground.ambient = r.sky?.first?.takeIf { it == Sky.RAIN || it == Sky.SNOW || it == Sky.STORM }
            r.fraction?.let { t.bar.fraction = it.coerceIn(0f, 1f); t.bar.invalidate() }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); live[this] = Unit; handler.removeCallbacks(tick); handler.postDelayed(tick, 30_000); refresh() }
    override fun onDetachedFromWindow() { live.remove(this); handler.removeCallbacks(tick); super.onDetachedFromWindow() }
}
