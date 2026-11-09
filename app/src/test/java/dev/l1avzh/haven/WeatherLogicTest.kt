package dev.l1avzh.haven

import org.junit.Assert.*
import org.junit.Test

class RequestGateTest {
    @Test fun sameKeyInFlightIsNotStartedTwice() {
        val g = RequestGate<Int>()
        val t = g.begin(1)
        assertNotNull(t)
        assertNull(g.begin(1))
        g.finish(t!!)
        assertNotNull(g.begin(1))
    }

    @Test fun newCityStartsEvenWhileOldOneIsStillRunning() {
        val g = RequestGate<Int>()
        val old = g.begin(1)!!
        val now = g.begin(2)
        assertNotNull("a slow request for the old city must not block the new one", now)
        assertFalse(g.isCurrent(old))
        assertTrue(g.isCurrent(now!!))
    }

    @Test fun staleFinishDoesNotClearTheNewerRequest() {
        val g = RequestGate<Int>()
        val old = g.begin(1)!!
        val now = g.begin(2)!!
        g.finish(old)
        assertNull("the newer request is still in flight", g.begin(2))
        g.finish(now)
        assertNotNull(g.begin(2))
    }

    @Test fun supersedeLetsTheSameKeyStartAgain() {
        val g = RequestGate<Int>()
        val t = g.begin(1)!!
        g.supersede()
        assertFalse(g.isCurrent(t))
        assertNotNull(g.begin(1))
    }

    @Test fun failureNeverLeavesAPermanentInFlightState() {
        val g = RequestGate<Pair<Int, String>>()
        repeat(3) {
            val t = g.begin(4 to "he")!!
            try { throw java.io.IOException("offline") } catch (_: Exception) { } finally { g.finish(t) }
        }
        assertNotNull(g.begin(4 to "he"))
    }

    @Test fun sameCityDifferentLanguageIsADifferentRequest() {
        val g = RequestGate<Pair<Int, String>>()
        g.begin(1 to "en")
        assertNotNull(g.begin(1 to "he"))
    }
}

class WeatherCacheTest {
    @Test fun roundTrip() {
        val e = WeatherCache.decode(WeatherCache.encode(WeatherCache.Entry(21.5, 3, 1000L, 7)))!!
        assertEquals(21.5, e.temp, 0.0); assertEquals(3, e.code); assertEquals(1000L, e.stamp); assertEquals(7, e.city)
    }

    @Test fun readsTheFormatEarlierVersionsWrote() {
        val e = WeatherCache.decode("19.3|61|1762600000000|0")!!
        assertEquals(61, e.code); assertEquals(0, e.city)
    }

    @Test fun malformedValuesAreRejected() {
        listOf("", "x", "1|2|3", "a|1|2|3", "1|b|2|3", "1|2|c|3", "1|2|3|d", "NaN|1|2|3", "1|2|3|4|5", "Infinity|1|2|3")
            .forEach { assertNull(it, WeatherCache.decode(it)) }
    }

    @Test fun anEntryOnlyCountsForItsOwnCity() {
        val raw = WeatherCache.encode(WeatherCache.Entry(10.0, 0, 5L, 2))
        assertNotNull(WeatherCache.forCity(raw, 2))
        assertNull(WeatherCache.forCity(raw, 3))
        assertNull(WeatherCache.forCity(raw, -1))
    }

    @Test fun freshnessUsesTheMaximumAge() {
        val e = WeatherCache.Entry(10.0, 0, 1_000_000L, 0)
        assertTrue(WeatherCache.isFresh(e, 1_000_000L + 29 * 60_000L))
        assertFalse(WeatherCache.isFresh(e, 1_000_000L + 30 * 60_000L))
        assertFalse("a clock that went backwards is not fresh", WeatherCache.isFresh(e, 500_000L))
    }
}

class WeatherJsonTest {
    @Test fun validReading() {
        val c = WeatherJson.parseCurrent("""{"current":{"temperature_2m":18.4,"weather_code":61}}""")!!
        assertEquals(18.4, c.temp, 0.0); assertEquals(61, c.code)
    }

    @Test fun malformedOrImplausibleBodiesGiveNull() {
        listOf("", "not json", "{}", """{"current":{}}""", """{"current":{"temperature_2m":"warm","weather_code":1}}""",
            """{"current":{"temperature_2m":999,"weather_code":1}}""", """{"current":{"temperature_2m":10,"weather_code":500}}""",
            """{"current":null}""", "[]", """{"error":true,"reason":"limit"}""")
            .forEach { assertNull(it, WeatherJson.parseCurrent(it)) }
    }

    @Test fun nameMatchesByPosition() {
        val body = """{"results":[{"name":"Elsewhere","latitude":10.0,"longitude":10.0},{"name":"תל אביב-יפו","latitude":32.08,"longitude":34.78}]}"""
        assertEquals("תל אביב-יפו", WeatherJson.parseName(body, 32.08, 34.78))
    }

    @Test fun nameIsNullWhenNothingMatchesOrBodyIsBad() {
        assertNull(WeatherJson.parseName("""{"results":[{"name":"X","latitude":1.0,"longitude":1.0}]}""", 32.0, 34.0))
        assertNull(WeatherJson.parseName("""{"generationtime_ms":0.1}""", 32.0, 34.0))
        assertNull(WeatherJson.parseName("garbage", 32.0, 34.0))
        assertNull(WeatherJson.parseName("""{"results":[null,{"name":"","latitude":32.0,"longitude":34.0}]}""", 32.0, 34.0))
    }
}
