package dev.l1avzh.haven

import org.junit.Assert.*
import org.junit.Test

class DayNightTest {
    @Test fun fixedModesIgnoreEverything() {
        assertFalse(DayNight.isNight(DayNightMode.DAY, false, 23))
        assertTrue(DayNight.isNight(DayNightMode.NIGHT, true, 12))
    }
    @Test fun autoPrefersProviderDayFlagOverClock() {
        assertTrue("provider says night at noon", DayNight.isNight(DayNightMode.AUTO, false, 12))
        assertFalse("provider says day at midnight", DayNight.isNight(DayNightMode.AUTO, true, 0))
    }
    @Test fun autoFallsBackToTheScheduleWithoutWeather() {
        assertTrue(DayNight.isNight(DayNightMode.AUTO, null, 22))
        assertTrue(DayNight.isNight(DayNightMode.AUTO, null, 3))
        assertFalse(DayNight.isNight(DayNightMode.AUTO, null, 12))
    }
    @Test fun scheduleBoundariesAndWrap() {
        assertTrue(DayNight.byClock(19, 19, 6)); assertFalse(DayNight.byClock(18, 19, 6))
        assertTrue(DayNight.byClock(5, 19, 6)); assertFalse(DayNight.byClock(6, 19, 6))
        assertTrue("same-day window", DayNight.byClock(13, 12, 14)); assertFalse(DayNight.byClock(14, 12, 14))
        assertFalse("equal bounds mean no night", DayNight.byClock(3, 5, 5))
    }
    @Test fun unknownModeReadsAsAutomatic() { assertEquals(DayNightMode.AUTO, DayNight.mode(7)); assertEquals(DayNightMode.NIGHT, DayNight.mode(2)) }
}

class WeatherEffectsTest {
    private fun fx(code: Int, c: Double, fresh: Boolean = true) = WeatherEffects.classify(code, c, fresh)
    @Test fun codesMapToEffects() {
        assertEquals(WeatherEffect.STORM, fx(95, 20.0)); assertEquals(WeatherEffect.SNOW, fx(73, 1.0)); assertEquals(WeatherEffect.SNOW, fx(85, -2.0))
        assertEquals(WeatherEffect.RAIN, fx(61, 15.0)); assertEquals(WeatherEffect.RAIN, fx(80, 15.0)); assertEquals(WeatherEffect.RAIN, fx(51, 15.0))
        assertEquals(WeatherEffect.NONE, fx(0, 22.0)); assertEquals(WeatherEffect.NONE, fx(3, 22.0))
    }
    @Test fun coldComparesTheCelsiusValueAtTheThreshold() {
        assertEquals(WeatherEffect.COLD, fx(0, 8.0)); assertEquals(WeatherEffect.NONE, fx(0, 8.1)); assertEquals(WeatherEffect.COLD, fx(3, -12.0))
    }
    @Test fun precipitationBeatsColdAndStormBeatsAll() {
        assertEquals(WeatherEffect.SNOW, fx(71, -5.0)); assertEquals(WeatherEffect.RAIN, fx(61, 2.0)); assertEquals(WeatherEffect.STORM, fx(99, -3.0))
    }
    @Test fun staleOrBrokenReadingsAreNeutral() {
        assertEquals(WeatherEffect.NONE, fx(95, 0.0, fresh = false)); assertEquals(WeatherEffect.NONE, fx(0, Double.NaN))
        assertTrue(WeatherEffects.isFresh(1_000, 1_000 + 60 * 60_000L)); assertFalse(WeatherEffects.isFresh(1_000, 1_000 + 91 * 60_000L)); assertFalse("clock moved back", WeatherEffects.isFresh(5_000, 1_000))
    }
    @Test fun displayUnitDoesNotChangeTheDecision() {
        // The decision takes Celsius only; 46.4 F is 8.0 C and must be cold whatever the widget shows.
        assertEquals(WeatherEffect.COLD, fx(0, (46.4 - 32) * 5 / 9 - 1e-6))
    }
}

class SceneRulesTest {
    private fun d(e: WeatherEffect, night: Boolean = false, sleep: Boolean = true, react: Boolean = true) = SceneRules.decide(e, night, sleep, react)
    @Test fun coldPutsAnUmbrellaInHand() { val s = d(WeatherEffect.COLD); assertEquals(Stance.COLD, s.stance); assertEquals("umbrella", s.propOverride) }
    @Test fun everyEffectHasItsOwnStance() {
        assertEquals(Stance.RAIN, d(WeatherEffect.RAIN).stance); assertEquals(Stance.SNOW, d(WeatherEffect.SNOW).stance); assertEquals(Stance.STORM, d(WeatherEffect.STORM).stance)
    }
    @Test fun noWeatherKeepsTheSavedProp() { val s = d(WeatherEffect.NONE); assertEquals(Stance.NONE, s.stance); assertNull(s.propOverride) }
    @Test fun nightSleepsAndNeverHoldsAnUmbrella() {
        for (e in WeatherEffect.values()) { val s = d(e, night = true); assertTrue(s.sleeping); assertNull(s.propOverride); assertEquals(Stance.SLEEP, s.stance) }
    }
    @Test fun sleepSwitchOffLetsWeatherShowAtNight() { assertEquals(Stance.RAIN, d(WeatherEffect.RAIN, night = true, sleep = false).stance) }
    @Test fun weatherSwitchOffMeansNoReaction() { assertEquals(Stance.NONE, d(WeatherEffect.STORM, react = false).stance); assertTrue(d(WeatherEffect.STORM, night = true, react = false).sleeping) }
}

class WeatherCacheDayTest {
    @Test fun isDayRoundTripsAndOldFormatStillDecodes() {
        val e = WeatherCache.Entry(12.5, 3, 123456L, 4, false)
        assertEquals(false, WeatherCache.decode(WeatherCache.encode(e))?.isDay)
        assertEquals(true, WeatherCache.decode(WeatherCache.encode(WeatherCache.Entry(1.0, 0, 5L, 0, true)))?.isDay)
        val old = WeatherCache.decode("12.5|3|123456|4")
        assertNotNull(old); assertNull(old!!.isDay)
    }
    @Test fun jsonReadsIsDayWhenPresent() {
        assertEquals(true, WeatherJson.parseCurrent("""{"current":{"temperature_2m":10.0,"weather_code":1,"is_day":1}}""")?.isDay)
        assertEquals(false, WeatherJson.parseCurrent("""{"current":{"temperature_2m":10.0,"weather_code":1,"is_day":0}}""")?.isDay)
        assertNull(WeatherJson.parseCurrent("""{"current":{"temperature_2m":10.0,"weather_code":1}}""")?.isDay)
    }
}

class WidgetLayoutTest {
    @Test fun cardsShareTheWidthForAnyCount() {
        for (n in 1..4) { val w = WidgetStrip.cardWidth(1500, n, 28, 440); assertTrue(w * n + 28 * (n - 1) <= 1500); assertTrue(w <= 440) }
    }
    @Test fun degenerateSpaceNeverGoesNegative() { assertEquals(0, WidgetStrip.cardWidth(10, 4, 28, 440)); assertEquals(0, WidgetStrip.cardWidth(500, 0, 28, 440)) }
}

class SleepAndStanceTest {
    @Test fun aSleepingCharacterIgnoresEveryReaction() {
        val r = CharacterReactor(); r.stance = Stance.SLEEP
        r.onInput(CharInput.AppFocus(AppKind.MUSIC), 100); r.onInput(CharInput.AppLaunch(AppKind.VIDEO), 100); r.onInput(CharInput.Returned, 100)
        assertNull(r.active(150))
    }
    @Test fun sleepLiesDownAndClosesTheEyesThenStandsBackUp() {
        val r = CharacterReactor(); val a = CharacterAnimator(r); a.update(0, 1f); r.stance = Stance.SLEEP
        var p = a.update(0, 1f); for (t in 16..6000 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("rolled onto its side", p.rootRoll > 1.4f); assertTrue("smaller on the floor", p.rootScale < 0.7f); assertTrue("eyes closed", p.eyes < 0.15f)
        r.stance = Stance.NONE
        for (t in 6016..14000 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("upright again", p.rootRoll < 0.05f); assertTrue(p.eyes > 0.5f)
    }
    @Test fun coldRaisesTheUmbrellaArmAndReducedMotionKeepsTheStill() {
        val r = CharacterReactor(); val a = CharacterAnimator(r); r.stance = Stance.COLD
        var p = a.update(0, 1f); for (t in 16..4000 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("forearm raised", -p.get("forearm_r", 0) > 1.2f)
        val calm = CharacterAnimator(r); var q = calm.update(0, 0f); for (t in 16..4000 step 16) q = calm.update(t.toLong(), 0f)
        assertTrue("reduced motion keeps the pose", -q.get("forearm_r", 0) > 1.2f)
        val s2 = CharacterReactor(); s2.stance = Stance.SLEEP; val z = CharacterAnimator(s2); var sp = z.update(0, 0f); for (t in 16..6000 step 16) sp = z.update(t.toLong(), 0f)
        assertTrue("and still lies down", sp.rootRoll > 1.4f)
    }
}
