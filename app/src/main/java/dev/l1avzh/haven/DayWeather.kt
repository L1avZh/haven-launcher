package dev.l1avzh.haven

/** How the launcher decides between day and night, and how weather changes the character. Pure Kotlin, unit tested. */
enum class DayNightMode { AUTO, DAY, NIGHT }

object DayNight {
    const val DEFAULT_NIGHT_FROM = 19
    const val DEFAULT_NIGHT_TO = 6

    fun mode(i: Int) = DayNightMode.values().getOrElse(i) { DayNightMode.AUTO }

    /**
     * Automatic mode prefers the provider's own day/night value for the selected city when it is fresh, and otherwise
     * falls back to the local clock and the user's schedule. It never needs the device's location.
     */
    fun isNight(mode: DayNightMode, weatherIsDay: Boolean?, hour: Int, nightFrom: Int = DEFAULT_NIGHT_FROM, nightTo: Int = DEFAULT_NIGHT_TO): Boolean = when (mode) {
        DayNightMode.DAY -> false
        DayNightMode.NIGHT -> true
        DayNightMode.AUTO -> if (weatherIsDay != null) !weatherIsDay else byClock(hour, nightFrom, nightTo)
    }

    fun byClock(hour: Int, from: Int, to: Int): Boolean {
        val h = hour.coerceIn(0, 23)
        return if (from == to) false else if (from < to) h in from until to else h >= from || h < to
    }
}

/** What the current weather asks of the character. Ordered by priority, strongest first. */
enum class WeatherEffect { STORM, SNOW, RAIN, COLD, NONE }

object WeatherEffects {
    /** At or below this many degrees Celsius it counts as cold. Always compared against the Celsius value, never a display string. */
    const val COLD_MAX_C = 8.0
    /** A reading older than this is ignored (the widget refreshes about every 30 minutes). */
    const val FRESH_MS = 90 * 60_000L

    fun isFresh(stampMs: Long, nowMs: Long) = nowMs - stampMs in 0 until FRESH_MS

    /** WMO codes: 95-99 thunderstorm, 71-77 and 85-86 snow, 51-67 and 80-82 rain (drizzle, rain, freezing rain, showers). */
    fun classify(code: Int, celsius: Double, fresh: Boolean): WeatherEffect {
        if (!fresh || !celsius.isFinite()) return WeatherEffect.NONE
        return when {
            code in 95..99 -> WeatherEffect.STORM
            code in 71..77 || code == 85 || code == 86 -> WeatherEffect.SNOW
            code in 51..67 || code in 80..82 -> WeatherEffect.RAIN
            celsius <= COLD_MAX_C -> WeatherEffect.COLD
            else -> WeatherEffect.NONE
        }
    }
}

/** The stance the character takes for a weather effect, and what it holds while doing so. */
enum class Stance { NONE, RAIN, SNOW, STORM, COLD, SLEEP }

/** The result of combining the user's look with the time of day and the weather. */
class SceneLook(val stance: Stance, val propOverride: String?, val sleeping: Boolean)

object SceneRules {
    /**
     * Night wins over weather: a sleeping character never holds an umbrella. Otherwise any weather effect puts an umbrella in
     * the hand (cold included) and picks a matching stance. With no usable weather the user's saved prop is simply kept.
     */
    fun decide(effect: WeatherEffect, night: Boolean, sleepAtNight: Boolean, weatherReactions: Boolean): SceneLook {
        if (night && sleepAtNight) return SceneLook(Stance.SLEEP, null, true)
        if (!weatherReactions) return SceneLook(Stance.NONE, null, false)
        return when (effect) {
            WeatherEffect.STORM -> SceneLook(Stance.STORM, "umbrella", false)
            WeatherEffect.SNOW -> SceneLook(Stance.SNOW, "umbrella", false)
            WeatherEffect.RAIN -> SceneLook(Stance.RAIN, "umbrella", false)
            WeatherEffect.COLD -> SceneLook(Stance.COLD, "umbrella", false)
            WeatherEffect.NONE -> SceneLook(Stance.NONE, null, false)
        }
    }
}
