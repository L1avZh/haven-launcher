package dev.l1avzh.haven

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
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
    @Volatile private var fetching = false

    /** "temp|code|timestamp|cityIndex" last good reading. */
    fun cached(p: Prefs): Triple<Double, Int, Long>? {
        val parts = p.weatherCache.split('|')
        if (parts.size != 4 || parts[3].toIntOrNull() != p.weatherCity - 1) return null
        return Triple(parts[0].toDoubleOrNull() ?: return null, parts[1].toIntOrNull() ?: return null, parts[2].toLongOrNull() ?: return null)
    }

    /** Fetch in the background if the cache is stale; calls [done] on the main thread after a successful update. */
    fun refresh(ctx: Context, done: () -> Unit) {
        val p = Prefs(ctx)
        val idx = p.weatherCity - 1
        val city = CITIES.getOrNull(idx) ?: return
        val c = cached(p)
        if (fetching || (c != null && System.currentTimeMillis() - c.third < 30 * 60_000)) return
        fetching = true
        thread(isDaemon = true) {
            try {
                val url = URL(String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code", city.lat, city.lon))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.requestMethod = "GET"
                try {
                    if (conn.responseCode == 200) {
                        val cur = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
                        p.weatherCache = "${cur.getDouble("temperature_2m")}|${cur.getInt("weather_code")}|${System.currentTimeMillis()}|$idx"
                        ui.post(done)
                    }
                } finally { conn.disconnect() }
            } catch (_: Exception) { /* offline or blocked: keep showing the last reading */ }
            fetching = false
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
