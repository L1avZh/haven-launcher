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
        assertTrue("on its back", p.rootPitch < -1.4f); assertTrue("head toward the pillow", p.rootYaw > 1.4f); assertTrue("bed is up", p.bedScale > 0.95f); assertTrue("prop put away", p.propScale == 0f); assertTrue("eyes closed", p.eyes < 0.15f)
        r.stance = Stance.NONE
        for (t in 6016..14000 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("upright again", p.rootPitch > -0.05f); assertTrue("bed gone", p.bedScale < 0.02f); assertTrue(p.eyes > 0.5f)
    }
    @Test fun coldRaisesTheUmbrellaArmAndReducedMotionKeepsTheStill() {
        val r = CharacterReactor(); val a = CharacterAnimator(r); r.stance = Stance.COLD
        var p = a.update(0, 1f); for (t in 16..4000 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("forearm raised", -p.get("forearm_r", 0) > 1.1f)
        val calm = CharacterAnimator(r); var q = calm.update(0, 0f); for (t in 16..4000 step 16) q = calm.update(t.toLong(), 0f)
        assertTrue("reduced motion keeps the pose", -q.get("forearm_r", 0) > 1.1f)
        val s2 = CharacterReactor(); s2.stance = Stance.SLEEP; val z = CharacterAnimator(s2); var sp = z.update(0, 0f); for (t in 16..6000 step 16) sp = z.update(t.toLong(), 0f)
        assertTrue("and still lies down", sp.rootPitch < -1.4f)
    }
}

/** The runtime GLBs must keep Haven's rig and node contract (see docs/character.md); this reads their JSON chunk, no renderer needed. */
class AssetContractTest {
    private fun gltf(id: String): org.json.JSONObject {
        val f = java.io.File("src/main/assets/character/$id.glb")
        val b = java.nio.ByteBuffer.wrap(f.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, b.getInt(0))                       // "glTF"
        val len = b.getInt(12); val json = ByteArray(len); b.position(20); b.get(json)
        return org.json.JSONObject(String(json, Charsets.UTF_8))
    }
    private fun names(g: org.json.JSONObject) = (0 until g.getJSONArray("nodes").length()).map { g.getJSONArray("nodes").getJSONObject(it).optString("name") }.toSet()

    @Test fun everyCharacterKeepsTheRigAndTheNamedNodes() {
        for (def in Look.CHARACTERS) {
            val n = names(gltf(def.id))
            for (j in Pose.JOINTS + "root") assertTrue("${def.id} lacks joint $j", j in n)
            for (x in listOf("attach_hand_r", "attach_hand_l", "acc_star")) assertTrue("${def.id} lacks $x", x in n)
            for (o in def.outfits) assertTrue("${def.id} lacks outfit_$o", "outfit_$o" in n)
        }
    }
    @Test fun theWorldHasEveryPropAndTheScenery() {
        val n = names(gltf("world"))
        for (p in Look.ALL_PROPS) if (p != "none") assertTrue("world lacks prop_$p", "prop_$p" in n)
        for (x in listOf("prop_umbrella", "bed", "env_day", "env_snow", "ground_grass", "ground_snow", "env_sun", "env_moon", "fx_rain_0", "fx_rain_1", "fx_snow_0", "fx_snow_1")) assertTrue("world lacks $x", x in n)
    }
    @Test fun jointsAreRotationFreeSoPoseDeltasActOnWorldAxes() {
        for (def in Look.CHARACTERS) {
            val g = gltf(def.id); val arr = g.getJSONArray("nodes")
            for (i in 0 until arr.length()) { val nd = arr.getJSONObject(i); if (nd.optString("name") in Pose.JOINTS) assertFalse("${def.id}/${nd.optString("name")} has a rotation", nd.has("rotation")) }
        }
    }
    @Test fun noExtensionsBeyondWhatFilamentLoads() {
        for (id in Look.CHARACTERS.map { it.id } + "world") (gltf(id).optJSONArray("extensionsRequired") ?: org.json.JSONArray()).let { arr -> for (i in 0 until arr.length()) assertEquals("KHR_materials_unlit", arr.getString(i)) }
    }
}

/** The state, pose and prop policy: what is held, what is shown, and that every change is a blend. */
class PropPolicyTest {
    private fun run(r: CharacterReactor, a: CharacterAnimator, from: Int, to: Int, each: (Pose, Int) -> Unit = { _, _ -> }): Pose {
        var p = a.update(from.toLong(), 1f); for (t in from + 16..to step 16) { p = a.update(t.toLong(), 1f); each(p, t) }; return p
    }
    @Test fun carriedPropIsHeldAndUmbrellaTakesOverWithoutBothShowing() {
        val r = CharacterReactor(); r.carried = "sword"; val a = CharacterAnimator(r)
        var p = run(r, a, 0, 3000); assertTrue(p.propScale > 0.95f); assertEquals(0f, p.umbrellaScale, 0.01f); assertTrue("fingers closed on the grip", p.curlR > 0.9f)
        r.stance = Stance.RAIN
        var worst = 0f
        p = run(r, a, 3000, 7000) { q, _ -> worst = maxOf(worst, q.propScale + q.umbrellaScale) }
        assertTrue("prop and umbrella never both fully shown (sum $worst)", worst < 1.15f)
        assertTrue(p.umbrellaScale > 0.95f); assertEquals(0f, p.propScale, 0.02f)
        r.stance = Stance.NONE; p = run(r, a, 7000, 11000); assertTrue("the saved prop returns", p.propScale > 0.95f); assertEquals(0f, p.umbrellaScale, 0.02f)
    }
    @Test fun noPropMeansNoCarryPoseAndRelaxedFingers() {
        val r = CharacterReactor(); val a = CharacterAnimator(r)
        val p = run(r, a, 0, 3000); assertEquals(0f, p.propScale, 0f); assertTrue(p.curlR < 0.2f); assertTrue(-p.get("forearm_r", 0) < 0.2f)
    }
    @Test fun sleepingPutsThePropAwayFirstAndWakingBringsItBackLast() {
        val r = CharacterReactor(); r.carried = "staff"; val a = CharacterAnimator(r); run(r, a, 0, 2000)
        r.stance = Stance.SLEEP
        var propGoneAt = -1; var lyingAt = -1
        run(r, a, 2000, 9000) { p, t -> if (propGoneAt < 0 && p.propScale < 0.02f) propGoneAt = t; if (lyingAt < 0 && p.rootPitch < -1.0f) lyingAt = t }
        assertTrue("prop away before the body is down ($propGoneAt < $lyingAt)", propGoneAt in 0 until lyingAt)
        r.stance = Stance.NONE
        var propBackAt = -1; var upAt = -1
        run(r, a, 9000, 16000) { p, t -> if (upAt < 0 && p.rootPitch > -0.3f) upAt = t; if (propBackAt < 0 && p.propScale > 0.5f) propBackAt = t }
        assertTrue("standing again before the prop returns ($upAt <= $propBackAt)", upAt in 0..propBackAt)
    }
    @Test fun withAPropInTheRightHandGesturesUseTheLeftArm() {
        val free = CharacterReactor(); val busy = CharacterReactor(); busy.carried = "sword"
        val af = CharacterAnimator(free); val ab = CharacterAnimator(busy); run(free, af, 0, 2500); run(busy, ab, 0, 2500)
        val base = busy.let { ab.update(2500, 1f).get("forearm_r", 0) }
        free.onInput(CharInput.Key(KeyKind.OK), 2600); busy.onInput(CharInput.Key(KeyKind.OK), 2600)
        var rightMove = 0f; var leftLift = 0f; var freeRight = 0f
        for (t in 2616..3200 step 16) { val pf = af.update(t.toLong(), 1f); val pb = ab.update(t.toLong(), 1f)
            rightMove = maxOf(rightMove, kotlin.math.abs(pb.get("forearm_r", 0) - base)); leftLift = maxOf(leftLift, -pb.get("forearm_l", 0)); freeRight = maxOf(freeRight, -pf.get("forearm_r", 0)) }
        assertTrue("the busy right arm stays put ($rightMove)", rightMove < 0.1f); assertTrue("the left hand lifts instead ($leftLift)", leftLift > 0.5f); assertTrue("an empty right hand does the gesture", freeRight > 0.5f)
    }
    private fun arms(p: Pose) = listOf("upperarm_l", "forearm_l", "hand_l", "upperarm_r", "forearm_r", "hand_r").flatMap { j -> (0..2).map { p.get(j, it) } }
    private fun rainPose(prop: String, beat: (CharacterReactor, Long) -> Unit, waiting: Boolean = false): Pair<List<Float>, List<Float>> {
        // compare mid-gesture frames: run both worlds in lockstep
        val rb = CharacterReactor(); rb.carried = prop; rb.stance = Stance.RAIN; rb.waiting = waiting; val ab = CharacterAnimator(rb)
        val rq = CharacterReactor(); rq.carried = prop; rq.stance = Stance.RAIN; rq.waiting = waiting; val aq = CharacterAnimator(rq)
        for (t in 0..5000 step 16) { ab.update(t.toLong(), 1f); aq.update(t.toLong(), 1f) }
        beat(rb, 5000)
        var maxDiff = 0f; var headMoved = 0f
        for (t in 5016..5700 step 16) { val pb = ab.update(t.toLong(), 1f); val pq = aq.update(t.toLong(), 1f)
            arms(pb).zip(arms(pq)).forEach { (x, y) -> maxDiff = maxOf(maxDiff, kotlin.math.abs(x - y)) }; headMoved = maxOf(headMoved, kotlin.math.abs(pb.get("head", 0) - pq.get("head", 0))) }
        return listOf(maxDiff) to listOf(headMoved)
    }
    @Test fun gesturesNeverFightTheWeatherPoseOrTheUmbrellaHand() {
        for (prop in listOf("none", "sword", "book", "staff", "candle")) for (k in listOf(KeyKind.OK, KeyKind.BACK)) {
            val (arm, head) = rainPose(prop, { r, now -> r.onInput(CharInput.Key(k), now) })
            assertTrue("$prop/$k: arms must stay on the rain pose (moved ${arm[0]})", arm[0] < 0.02f)
            if (k == KeyKind.OK) assertTrue("$prop: the nod remains ($head)", head[0] > 0.02f)
        }
        for (ev in listOf<(CharacterReactor, Long) -> Unit>({ r, n -> r.onInput(CharInput.AppFocus(AppKind.MUSIC), n) }, { r, n -> r.onInput(CharInput.AppLaunch(AppKind.VIDEO), n) },
            { r, n -> r.onInput(CharInput.Returned, n) }, { r, n -> r.onLongPress(n) }, { r, n -> r.onInput(CharInput.Studio(StudioAction.EQUIP), n) })) {
            val (arm, _) = rainPose("sword", ev); assertTrue("app and studio reactions leave the rain arms alone (${arm[0]})", arm[0] < 0.02f)
        }
    }
    @Test fun waitingDoesNotMoveArmsHeldByWeather() {
        val on = CharacterReactor().also { it.stance = Stance.RAIN; it.waiting = true }; val off = CharacterReactor().also { it.stance = Stance.RAIN }
        val a1 = CharacterAnimator(on); val a2 = CharacterAnimator(off); var p1 = a1.update(0, 1f); var p2 = a2.update(0, 1f)
        for (t in 16..6000 step 16) { p1 = a1.update(t.toLong(), 1f); p2 = a2.update(t.toLong(), 1f) }
        arms(p1).zip(arms(p2)).forEach { (x, y) -> assertEquals("waiting must not move an arm the weather owns", y, x, 0.02f) }
    }
    @Test fun swappingTheSavedPropGoesThroughAnEmptyHand() {
        val r = CharacterReactor(); r.carried = "sword"; val a = CharacterAnimator(r); run(r, a, 0, 3000)
        r.carried = "staff"; var sawEmptyBetween = false; var last = "sword"; var bad = false
        run(r, a, 3000, 6000) { p, _ -> if (p.heldProp != last) { if (p.propScale > 0.08f) bad = true; if (last == "sword" && p.heldProp == "staff") sawEmptyBetween = true; last = p.heldProp } }
        assertTrue("the new prop appears only once the old one is gone", !bad && sawEmptyBetween)
        val p = a.update(6100, 1f); assertEquals("staff", p.heldProp); assertTrue(p.propScale > 0.9f)
    }
    @Test fun sleepingIgnoresEveryReaction() {
        val a = CharacterReactor().also { it.stance = Stance.SLEEP }; val b = CharacterReactor().also { it.stance = Stance.SLEEP }
        val aa = CharacterAnimator(a); val ab = CharacterAnimator(b); for (t in 0..9000 step 16) { aa.update(t.toLong(), 1f); ab.update(t.toLong(), 1f) }
        b.onInput(CharInput.Key(KeyKind.OK), 9000); b.onInput(CharInput.AppLaunch(AppKind.MUSIC), 9000)
        for (t in 9016..9500 step 16) arms(aa.update(t.toLong(), 1f)).zip(arms(ab.update(t.toLong(), 1f))).forEach { (x, y) -> assertEquals(x, y, 1e-4f) }
    }
    @Test fun noJointSnapsDuringStateChanges() {
        val r = CharacterReactor(); r.carried = "candle"; val a = CharacterAnimator(r); var prev = a.update(0, 1f); var worst = 0f
        val steps = listOf(2000 to Stance.RAIN, 4500 to Stance.SLEEP, 11000 to Stance.NONE, 17000 to Stance.COLD, 20000 to Stance.NONE)
        var next = 0
        for (t in 16..24000 step 16) {
            if (next < steps.size && t >= steps[next].first) { r.stance = steps[next].second; next++ }
            val p = a.update(t.toLong(), 1f)
            for (i in p.rot.indices) worst = maxOf(worst, kotlin.math.abs(p.rot[i] - prev.rot[i]))
            prev = Pose().also { q -> p.rot.copyInto(q.rot) }
        }
        assertTrue("largest joint step per 16 ms frame was $worst rad", worst < 0.09f)
    }
}
