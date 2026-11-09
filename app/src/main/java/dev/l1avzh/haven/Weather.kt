package dev.l1avzh.haven

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Weather widget data. Opt-in: nothing is requested until a city is chosen in Settings, and the only thing sent is
 * that city's coordinates to open-meteo.com over HTTPS (no key, no account, no device identifiers, no location access).
 */
object Weather {
    class City(val name: String, val lat: Double, val lon: Double)

    val CITIES = listOf(
        City("Tel Aviv", 32.08, 34.78), City("Jerusalem", 31.77, 35.21), City("Haifa", 32.79, 34.99), City("Beer Sheva", 31.25, 34.79), City("Eilat", 29.56, 34.95),
        City("London", 51.51, -0.13), City("Paris", 48.86, 2.35), City("Berlin", 52.52, 13.41), City("Madrid", 40.42, -3.70), City("Rome", 41.90, 12.50),
        City("Amsterdam", 52.37, 4.90), City("Vienna", 48.21, 16.37), City("Athens", 37.98, 23.73), City("Istanbul", 41.01, 28.98), City("Moscow", 55.76, 37.62),
        City("Kyiv", 50.45, 30.52), City("Warsaw", 52.23, 21.01), City("Stockholm", 59.33, 18.07), City("Lisbon", 38.72, -9.14), City("Dublin", 53.35, -6.26),
        City("New York", 40.71, -74.01), City("Los Angeles", 34.05, -118.24), City("Chicago", 41.88, -87.63), City("Miami", 25.76, -80.19), City("Toronto", 43.65, -79.38),
        City("Mexico City", 19.43, -99.13), City("São Paulo", -23.55, -46.63), City("Buenos Aires", -34.60, -58.38),
        City("Dubai", 25.20, 55.27), City("Cairo", 30.04, 31.24), City("Riyadh", 24.71, 46.68), City("Johannesburg", -26.20, 28.05), City("Lagos", 6.52, 3.38),
        City("Mumbai", 19.08, 72.88), City("Delhi", 28.61, 77.21), City("Bangkok", 13.76, 100.50), City("Singapore", 1.35, 103.82),
        City("Hong Kong", 22.32, 114.17), City("Shanghai", 31.23, 121.47), City("Tokyo", 35.68, 139.69), City("Seoul", 37.57, 126.98), City("Sydney", -33.87, 151.21),
    )

    private val ui = Handler(Looper.getMainLooper())

    /** Language code the geocoding service understands, from the app's current locale. */
    private fun langCode(ctx: Context): String = when (val l = Lang.locale(ctx).language) { "iw" -> "he"; "in" -> "id"; "ji" -> "yi"; else -> l }

    private fun nameKey(ctx: Context, idx: Int) = "wname_${idx}_${langCode(ctx)}"

    /** The city's name in the app's language once fetched, otherwise the English name. */
    fun displayName(ctx: Context, p: Prefs, idx: Int): String {
        val city = CITIES.getOrNull(idx) ?: return ""
        return p.str(nameKey(ctx, idx)).ifEmpty { city.name }
    }

    private const val MAX_BODY = 32 * 1024

    /** GET over HTTPS with short timeouts; null for any non-200 answer, I/O problem or oversized body. */
    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.requestMethod = "GET"
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val n = s.read(chunk); if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_BODY) return null
                }
                buf.toString("UTF-8")
            }
        } catch (_: java.io.IOException) { null } finally { conn.disconnect() }
    }

    /** "temp|code|timestamp|cityIndex" last good reading, for the selected city only. */
    fun cached(p: Prefs): Triple<Double, Int, Long>? =
        WeatherCache.forCity(p.weatherCache, p.weatherCity - 1)?.let { Triple(it.temp, it.code, it.stamp) }

    private val gate = RequestGate<Pair<Int, String>>()

    /** The selected city changed: drop the cache and let any slow request for the old city die quietly. */
    fun selectionChanged(p: Prefs) { p.weatherCache = ""; gate.supersede() }

    /**
     * Refresh in the background if the selected city's reading is stale or its localized name is missing.
     * Results are published only while the request still matches the selected city; [done] runs on the main thread.
     */
    fun refresh(ctx: Context, done: () -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        val idx = p.weatherCity - 1
        val city = CITIES.getOrNull(idx) ?: return
        val lang = langCode(app)
        val c = WeatherCache.forCity(p.weatherCache, idx)
        val fresh = c != null && WeatherCache.isFresh(c, System.currentTimeMillis())
        val needName = lang != "en" && p.str(nameKey(app, idx)).isEmpty()
        if (fresh && !needName) return
        val ticket = gate.begin(idx to lang) ?: return
        thread(isDaemon = true) {
            try {
                fun stillSelected() = gate.isCurrent(ticket) && Prefs(app).weatherCity - 1 == idx
                if (needName) {
                    val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + java.net.URLEncoder.encode(city.name, "UTF-8") + "&count=5&format=json&language=" + lang
                    val n = get(url)?.let { WeatherJson.parseName(it, city.lat, city.lon) }
                    if (n != null && stillSelected()) { p.putStr(nameKey(app, idx), n); ui.post(done) }
                }
                if (!fresh) {
                    val body = get(String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code", city.lat, city.lon))
                    val cur = body?.let { WeatherJson.parseCurrent(it) }
                    if (cur != null && stillSelected()) {
                        p.weatherCache = WeatherCache.encode(WeatherCache.Entry(cur.temp, cur.code, System.currentTimeMillis(), idx))
                        ui.post(done)
                    }
                }
            } catch (_: Exception) { /* keep the last good reading */ } finally { gate.finish(ticket) }
        }
    }

    fun describe(ctx: Context, code: Int): String = ctx.getString(when (code) {
        0 -> R.string.wx_clear
        1, 2 -> R.string.wx_partly
        3 -> R.string.wx_cloudy
        45, 48 -> R.string.wx_fog
        in 51..67, in 80..82 -> R.string.wx_rain
        in 71..77, 85, 86 -> R.string.wx_snow
        in 95..99 -> R.string.wx_storm
        else -> R.string.wx_cloudy
    })

    fun format(p: Prefs, celsius: Double): String =
        if (p.weatherUnit == 1) "${Math.round(celsius * 9 / 5 + 32)}°" else "${Math.round(celsius)}°"
}
