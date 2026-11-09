package dev.l1avzh.haven

import org.json.JSONException
import org.json.JSONObject

/**
 * Decides which weather request may still publish its result. A new request for a different key (city or language)
 * supersedes any older one, so a slow answer for a city the user has since left is dropped instead of blocking or
 * overwriting the new city. The same key is never fetched twice at once. Pure Kotlin, no Android types.
 */
class RequestGate<K> {
    class Ticket<K>(internal val generation: Long, val key: K)

    private var generation = 0L
    private var running: K? = null
    private var active = false

    /** Returns a ticket to start work, or null when the very same request is already in flight. */
    @Synchronized fun begin(key: K): Ticket<K>? {
        if (active && running == key) return null
        generation++; running = key; active = true
        return Ticket(generation, key)
    }

    @Synchronized fun isCurrent(t: Ticket<K>) = t.generation == generation

    /** Always call when the work ends, however it ends. Only the newest ticket clears the in-flight state. */
    @Synchronized fun finish(t: Ticket<K>) { if (t.generation == generation) { active = false; running = null } }

    /** The selection changed: forget anything in flight so the next [begin] always starts. */
    @Synchronized fun supersede() { generation++; active = false; running = null }
}

/** The "temp|code|timestamp|cityIndex" last-good reading kept in preferences (format unchanged from earlier versions). */
object WeatherCache {
    /** [isDay] is the provider's day/night flag for that moment; entries written by older versions have none (null). */
    class Entry(val temp: Double, val code: Int, val stamp: Long, val city: Int, val isDay: Boolean? = null)

    fun encode(e: Entry) = "${e.temp}|${e.code}|${e.stamp}|${e.city}" + (e.isDay?.let { "|${if (it) 1 else 0}" } ?: "")

    fun decode(raw: String): Entry? {
        val p = raw.split('|')
        if (p.size != 4 && p.size != 5) return null
        val t = p[0].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val day = if (p.size == 5) when (p[4]) { "1" -> true; "0" -> false; else -> return null } else null
        return Entry(t, p[1].toIntOrNull() ?: return null, p[2].toLongOrNull() ?: return null, p[3].toIntOrNull() ?: return null, day)
    }

    /** The entry only counts for the city it was fetched for. */
    fun forCity(raw: String, city: Int): Entry? = decode(raw)?.takeIf { it.city == city }

    fun isFresh(e: Entry, now: Long, maxAgeMs: Long = 30 * 60_000L) = now - e.stamp in 0 until maxAgeMs
}

object WeatherJson {
    class Current(val temp: Double, val code: Int, val isDay: Boolean?)

    /** Parses the provider's "current" block; anything malformed or implausible yields null instead of throwing. */
    fun parseCurrent(body: String): Current? = try {
        val cur = JSONObject(body).getJSONObject("current")
        val t = cur.getDouble("temperature_2m")
        val c = cur.getInt("weather_code")
        val day = if (cur.has("is_day")) when (cur.getInt("is_day")) { 1 -> true; 0 -> false; else -> null } else null
        if (t.isFinite() && t in -90.0..65.0 && c in 0..99) Current(t, c, day) else null
    } catch (_: JSONException) { null }

    /** First geocoding result whose position is near the expected one, or null. */
    fun parseName(body: String, lat: Double, lon: Double): String? = try {
        val arr = JSONObject(body).optJSONArray("results")
        var found: String? = null
        if (arr != null) for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (Math.abs(r.optDouble("latitude") - lat) < 0.6 && Math.abs(r.optDouble("longitude") - lon) < 0.6) {
                val n = r.optString("name"); if (n.isNotEmpty()) { found = n; break }
            }
        }
        found
    } catch (_: JSONException) { null }
}
