package dev.l1avzh.haven

import org.junit.Assert.*
import org.junit.Test

class LookTest {
    @Test fun rosterIdsAreStable() {
        // These strings are saved in preferences; changing them would silently reset users' choices.
        assertEquals(listOf("human", "wizard", "robot"), Look.CHARACTERS.map { it.id })
        assertEquals(listOf("city", "field"), Look.def("human").outfits)
        assertEquals(listOf("archmage", "apprentice"), Look.def("wizard").outfits)
        assertEquals(listOf("plated", "stealth"), Look.def("robot").outfits)
        assertEquals("human", Look.DEFAULT_CHARACTER)
        assertEquals(Look.CHARACTERS.size, Look.CHARACTERS.map { it.id }.toSet().size)
    }

    @Test fun everyCharacterHasItsAssetAndValidDefaults() {
        Look.CHARACTERS.forEach {
            assertEquals("character/${it.id}.glb", it.asset)
            assertTrue(it.defaultOutfit in it.outfits); assertTrue(it.defaultProp in it.props); assertTrue("none" in it.props)
        }
    }

    @Test fun unknownCharacterFallsBackToTheDefault() {
        listOf(null, "", "warden", "HUMAN").forEach { assertEquals("human", Look.character(it)) }
    }

    @Test fun outfitsAndPropsAreValidatedAgainstTheirCharacter() {
        assertEquals("archmage", Look.outfit("wizard", "archmage"))
        assertEquals("city", Look.outfit("human", "archmage"))              // another character's outfit
        assertEquals("city", Look.outfit("human", "courier"))               // an id written by an older version
        assertEquals("plated", Look.outfit("robot", null))
        assertEquals("staff", Look.prop("wizard", "staff"))
        assertEquals("lantern", Look.prop("human", "staff"))                // the staff belongs to the wizard
        assertEquals("tablet", Look.prop("robot", "sword"))
    }

    @Test fun theStarIsOnlyWornOnceUnlocked() {
        assertFalse(LookState.of("human", "city", "lantern", star = true, starUnlocked = false).star)
        assertTrue(LookState.of("human", "city", "lantern", star = true, starUnlocked = true).star)
        assertFalse(LookState.of("human", "city", "lantern", star = false, starUnlocked = true).star)
    }

    @Test fun anInvalidSavedLookStillProducesAValidOne() {
        val l = LookState.of("zzz", "archmage", "staff", star = true, starUnlocked = false)
        assertEquals(LookState("human", "city", "lantern", false), l)
    }
}

class AppKindsTest {
    @Test fun spotifyOnTvAndOnPhones() {
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.spotify.tv.android", "Spotify"))
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.spotify.music", "Spotify"))
    }

    @Test fun youtubeMusicIsMusicButYouTubeIsVideo() {
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.google.android.youtube.tvmusic", "YouTube Music"))
        assertEquals(AppKind.VIDEO, AppKinds.classify("com.google.android.youtube.tv", "YouTube"))
    }

    @Test fun videoServicesAcrossPackageVariants() {
        listOf("com.netflix.ninja" to "Netflix", "com.netflix.mediaclient" to "Netflix", "com.plexapp.android" to "Plex",
            "com.amazon.amazonvideo.livingroom" to "Prime Video", "com.amazon.avod.thirdpartyclient" to "Prime Video", "com.disney.disneyplus" to "Disney+")
            .forEach { (p, l) -> assertEquals(p, AppKind.VIDEO, AppKinds.classify(p, l)) }
    }

    @Test fun theAppsOwnDeclaredCategoryIsUsedWhenThePackageIsUnknown() {
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.example.player", "Player", AppKinds.CATEGORY_AUDIO))
        assertEquals(AppKind.VIDEO, AppKinds.classify("com.example.player", "Player", AppKinds.CATEGORY_VIDEO))
        assertEquals(AppKind.OTHER, AppKinds.classify("com.example.player", "Player", -1))
    }

    @Test fun labelsHelpWhenNothingElseDoes() {
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.x.y", "Cool Music"))
        assertEquals(AppKind.MUSIC, AppKinds.classify("com.x.y", "מוזיקה"))
        assertEquals(AppKind.VIDEO, AppKinds.classify("com.x.y", "Movies Anywhere"))
        assertEquals(AppKind.VIDEO, AppKinds.classify("com.x.y", "NextGEN IPTV"))
    }

    @Test fun everyOtherAppStaysOther() {
        listOf("com.android.tv.settings" to "Settings", "com.android.vending" to "Play Store", "com.android.gallery3d" to "Gallery",
            "com.adguard.android" to "AdGuard", "dev.l1avzh.haven" to "Haven").forEach { (p, l) -> assertEquals(p, AppKind.OTHER, AppKinds.classify(p, l)) }
    }

    @Test fun wordsAreMatchedWholeSoPlayStoreIsNotMusic() {
        assertEquals(AppKind.OTHER, AppKinds.classify("com.android.vending", "Play Store"))
        assertEquals(AppKind.OTHER, AppKinds.classify("com.x.y", "Gamestream helper"))
    }
}

class AppReactionTest {
    @Test fun focusingAMusicOrVideoAppStartsAThemedBeat() {
        val m = CharacterReactor(); m.onInput(CharInput.AppFocus(AppKind.MUSIC), 1000); assertEquals(Beat.LISTEN, m.active(1100)!!.beat)
        val v = CharacterReactor(); v.onInput(CharInput.AppFocus(AppKind.VIDEO), 1000); assertEquals(Beat.WATCH, v.active(1100)!!.beat)
    }

    @Test fun otherAppsKeepTheGenericReaction() {
        val c = CharacterReactor(); c.onInput(CharInput.AppFocus(AppKind.OTHER), 1000)
        assertNull(c.active(1100))
        c.onInput(CharInput.Focus(0.5f, 0f), 1200); assertEquals(Beat.NAVIGATE, c.active(1250)!!.beat)
    }

    @Test fun scrollingPastManyMusicAppsDoesNotKeepRestartingTheGesture() {
        val c = CharacterReactor(); c.onInput(CharInput.AppFocus(AppKind.MUSIC), 1000)
        val p0 = c.active(1100)!!.progress
        c.onInput(CharInput.AppFocus(AppKind.MUSIC), 1300); c.onInput(CharInput.AppFocus(AppKind.MUSIC), 1500)
        assertTrue(c.active(1600)!!.progress > p0)
    }

    @Test fun launchingIsStrongerThanFocusingAndThanConfirm() {
        val c = CharacterReactor(); c.onInput(CharInput.AppFocus(AppKind.VIDEO), 1000)
        c.onInput(CharInput.Key(KeyKind.OK), 1100)
        assertEquals(Beat.CONFIRM, c.active(1150)!!.beat)
        c.onInput(CharInput.AppLaunch(AppKind.VIDEO), 1200)
        assertEquals(Beat.LAUNCH_WATCH, c.active(1250)!!.beat)
    }

    @Test fun launchingAGenericAppAddsNothingBeyondConfirm() {
        val c = CharacterReactor(); c.onInput(CharInput.Key(KeyKind.OK), 1000); c.onInput(CharInput.AppLaunch(AppKind.OTHER), 1100)
        assertEquals(Beat.CONFIRM, c.active(1150)!!.beat)
    }

    @Test fun returningFromAnotherAppIsASmallWave() {
        val c = CharacterReactor(); c.onInput(CharInput.Returned, 0); assertEquals(Beat.RETURN, c.active(10)!!.beat)
    }

    @Test fun reducedMotionSuppressesThemedBeatsToo() {
        val c = CharacterReactor(enabled = false)
        c.onInput(CharInput.AppFocus(AppKind.MUSIC), 0); c.onInput(CharInput.AppLaunch(AppKind.VIDEO), 0); c.onInput(CharInput.Returned, 0)
        assertNull(c.active(50))
    }

    @Test fun listeningRaisesBothHandsAndWatchingTurnsTheBody() {
        val r1 = CharacterReactor(); val a1 = CharacterAnimator(r1); a1.update(0, 1f); r1.onInput(CharInput.AppFocus(AppKind.MUSIC), 1000)
        var armL = 0f; var armR = 0f
        for (t in 1000..2500 step 16) { val p = a1.update(t.toLong(), 1f); armL = maxOf(armL, -p.get("forearm_l", 0)); armR = maxOf(armR, -p.get("forearm_r", 0)) }
        assertTrue(armL > 1f && armR > 1f)
        val r2 = CharacterReactor(); val a2 = CharacterAnimator(r2); a2.update(0, 1f); r2.onInput(CharInput.AppFocus(AppKind.VIDEO), 1000)
        var yaw = 0f
        for (t in 1000..2300 step 16) yaw = minOf(yaw, a2.update(t.toLong(), 1f).rootYaw)
        assertTrue(yaw < -0.15f)
    }

    @Test fun waitingRaisesAHandAndLetsTheEyesWanderButNotInReducedMotion() {
        val r = CharacterReactor(); val a = CharacterAnimator(r); r.waiting = true
        var p = a.update(0, 1f); var minGaze = 1f; var maxGaze = -1f
        for (t in 16..6000 step 16) { p = a.update(t.toLong(), 1f); minGaze = minOf(minGaze, p.get("head", 1)); maxGaze = maxOf(maxGaze, p.get("head", 1)) }
        assertTrue("the hand rests near the chin", -p.get("forearm_r", 0) > 1f)
        assertTrue("the head looks around", maxGaze - minGaze > 0.2f)
        val still = CharacterAnimator(r).update(1000, 0f)
        assertTrue(still.rot.all { it == 0f })
    }
}

class CharacterReactorTest {
    private fun r() = CharacterReactor()

    @Test fun okStartsAConfirmReaction() {
        val c = r(); c.onInput(CharInput.Key(KeyKind.OK), 1000)
        assertEquals(Beat.CONFIRM, c.active(1100)!!.beat)
    }

    @Test fun backStartsABackReaction() {
        val c = r(); c.onInput(CharInput.Key(KeyKind.BACK), 1000)
        assertEquals(Beat.BACK, c.active(1100)!!.beat)
    }

    @Test fun focusMovesTheGazeAndStartsAQuietNavigationBeat() {
        val c = r(); c.onInput(CharInput.Focus(0.7f, -0.2f), 1000)
        assertEquals(0.7f, c.gazeX, 0f); assertEquals(-0.2f, c.gazeY, 0f)
        assertEquals(Beat.NAVIGATE, c.active(1100)!!.beat)
    }

    @Test fun gazeIsClampedToTheScreen() {
        val c = r(); c.onInput(CharInput.Focus(5f, -9f), 0)
        assertEquals(1f, c.gazeX, 0f); assertEquals(-1f, c.gazeY, 0f)
    }

    @Test fun heldKeysNeverRestartAReaction() {
        val c = r(); c.onInput(CharInput.Key(KeyKind.OK, repeat = 0), 1000)
        val first = c.active(1050)!!.progress
        c.onInput(CharInput.Key(KeyKind.OK, repeat = 1), 1100); c.onInput(CharInput.Key(KeyKind.OK, repeat = 2), 1150)
        assertTrue("key repeats must not restart the confirm", c.active(1200)!!.progress > first)
        c.onInput(CharInput.Key(KeyKind.BACK, repeat = 3), 1200)
        assertEquals(Beat.CONFIRM, c.active(1250)!!.beat)
    }

    @Test fun rapidNavigationIsCoalesced() {
        val c = r(); c.onInput(CharInput.Focus(0.1f, 0f), 1000)
        val p0 = c.active(1010)!!.progress
        c.onInput(CharInput.Focus(0.2f, 0f), 1030)          // 30 ms later: gaze updates, the beat is not restarted
        assertTrue(c.active(1040)!!.progress > p0)
        assertEquals(0.2f, c.gazeX, 0f)
    }

    @Test fun strongerReactionsInterruptWeakerOnes() {
        val c = r(); c.onInput(CharInput.Focus(0.4f, 0f), 1000)
        c.onInput(CharInput.Key(KeyKind.OK), 1050)
        assertEquals(Beat.CONFIRM, c.active(1100)!!.beat)
    }

    @Test fun weakerReactionsNeverInterruptStrongerOnes() {
        val c = r(); c.onInput(CharInput.Key(KeyKind.OK), 1000)
        c.onInput(CharInput.Focus(-0.5f, 0f), 1200)          // navigation while confirming
        assertEquals(Beat.CONFIRM, c.active(1250)!!.beat)
        assertEquals(-0.5f, c.gazeX, 0f)                        // but the eyes still follow
    }

    @Test fun equipBeatsEverythingElse() {
        val c = r(); c.onInput(CharInput.Studio(StudioAction.EQUIP), 1000)
        c.onInput(CharInput.Key(KeyKind.OK), 1100); c.onInput(CharInput.Key(KeyKind.BACK), 1150)
        assertEquals(Beat.EQUIP, c.active(1200)!!.beat)
    }

    @Test fun reactionsEndOnTheirOwn() {
        val c = r(); c.onInput(CharInput.Key(KeyKind.OK), 1000)
        assertNotNull(c.active(1000 + Beat.CONFIRM.durationMs - 1))
        assertNull(c.active(1000 + Beat.CONFIRM.durationMs))
    }

    @Test fun reducedMotionSuppressesBodyReactions() {
        val c = CharacterReactor(enabled = false)
        c.onInput(CharInput.Key(KeyKind.OK), 1000); c.onInput(CharInput.Studio(StudioAction.EQUIP), 1000)
        assertNull(c.active(1100))
    }

    @Test fun studioActionsMapToTheirBeats() {
        val expect = mapOf(StudioAction.PREVIEW to Beat.PREVIEW, StudioAction.EQUIP to Beat.EQUIP, StudioAction.APPLY to Beat.APPLY, StudioAction.CANCEL to Beat.CANCEL)
        expect.forEach { (action, beat) -> val c = r(); c.onInput(CharInput.Studio(action), 0); assertEquals(beat, c.active(10)!!.beat) }
    }

    @Test fun screenChangeStartsACategoryBeatAndLongPressItsOwn() {
        val c = r(); c.onInput(CharInput.ScreenChanged, 0); assertEquals(Beat.CATEGORY, c.active(10)!!.beat)
        val d = r(); d.onLongPress(0); assertEquals(Beat.LONG_PRESS, d.active(10)!!.beat)
    }

    @Test fun resetClearsGazeAndBeat() {
        val c = r(); c.onInput(CharInput.Focus(0.9f, 0.9f), 0); c.reset()
        assertEquals(0f, c.gazeX, 0f); assertNull(c.active(10))
    }
}

class CharacterAnimatorTest {
    @Test fun reducedMotionGivesAStillPose() {
        val reactor = CharacterReactor(); val a = CharacterAnimator(reactor)
        reactor.onInput(CharInput.Focus(1f, 1f), 0)
        val p = a.update(5000, motion = 0f)
        assertTrue(p.rot.all { it == 0f }); assertEquals(0f, p.hipsY, 0f); assertEquals(1f, p.eyes, 0f)
    }

    @Test fun idleMovesButStaysSmall() {
        val a = CharacterAnimator(CharacterReactor())
        var maxAbs = 0f; var changed = false; var first = a.update(0, 1f).hipsX
        for (t in 0..20000 step 100) { val p = a.update(t.toLong(), 1f); maxAbs = maxOf(maxAbs, p.rot.maxOf { kotlin.math.abs(it) }); if (p.hipsX != first) changed = true }
        assertTrue("idle should move", changed); assertTrue("idle should stay restrained: $maxAbs", maxAbs < 0.2f)
    }

    @Test fun gazeTurnsTheHeadTowardTheTarget() {
        val reactor = CharacterReactor(); val a = CharacterAnimator(reactor)
        reactor.onInput(CharInput.Focus(1f, 0f), 0)
        var p = a.update(0, 1f); for (t in 16..1500 step 16) p = a.update(t.toLong(), 1f)
        assertTrue("head yaw should turn right toward +x", p.get("head", 1) > 0.15f)
    }

    @Test fun confirmRaisesTheHandAndWidensTheEyes() {
        val reactor = CharacterReactor(); val a = CharacterAnimator(reactor)
        a.update(0, 1f); reactor.onInput(CharInput.Key(KeyKind.OK), 1000)
        var peakEyes = 0f; var peakArm = 0f
        for (t in 1000..1700 step 16) { val p = a.update(t.toLong(), 1f); peakEyes = maxOf(peakEyes, p.eyes); peakArm = maxOf(peakArm, -p.get("forearm_r", 0)) }
        assertTrue(peakEyes > 1.1f); assertTrue(peakArm > 0.5f)
    }

    @Test fun equipTurnsAFullCircleAndComesBackToRest() {
        val reactor = CharacterReactor(); val a = CharacterAnimator(reactor)
        a.update(0, 1f); reactor.onInput(CharInput.Studio(StudioAction.EQUIP), 1000)
        var maxYaw = 0f
        for (t in 1000 until 1000 + Beat.EQUIP.durationMs.toInt() step 16) maxYaw = maxOf(maxYaw, a.update(t.toLong(), 1f).rootYaw)
        assertTrue(maxYaw > 6f)                                  // close to 2*pi
        assertEquals(0f, a.update(3000, 1f).rootYaw, 0f)
    }

    @Test fun gazeFromScreenIsCentreRelativeAndClamped() {
        assertEquals(0f to 0f, gazeFromScreen(960f, 540f, 1920, 1080, 960f, 540f))
        val (x, y) = gazeFromScreen(1920f, 0f, 1920, 1080, 960f, 540f)
        assertEquals(1f, x, 0.001f); assertEquals(1f, y, 0.001f)
        assertEquals(-1f to 0f, gazeFromScreen(-5000f, 540f, 1920, 1080, 960f, 540f))
        assertEquals(0f to 0f, gazeFromScreen(10f, 10f, 0, 0, 0f, 0f))
    }
}
