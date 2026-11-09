package dev.l1avzh.haven

import org.junit.Assert.*
import org.junit.Test

class ArtCacheTest {
    private fun cache(max: Long = 1000) = ArtCache<Int>(max) { it.toLong() }

    @Test fun removingAPackageKeepsEveryOtherEntry() {
        val c = cache()
        c.put("com.a/.Main@960x540", 100); c.put("com.a/.Other@960x540", 100); c.put("com.b/.Main@960x540", 100)
        assertEquals(2, c.removePackage("com.a"))
        assertNull(c.get("com.a/.Main@960x540")); assertNull(c.get("com.a/.Other@960x540"))
        assertNotNull(c.get("com.b/.Main@960x540"))
        assertEquals(100L, c.totalBytes())
    }

    @Test fun aPackageNamePrefixDoesNotTouchSimilarNames() {
        val c = cache()
        c.put("com.app/.Main@1x1", 10); c.put("com.apple/.Main@1x1", 10)
        assertEquals(1, c.removePackage("com.app"))
        assertNotNull(c.get("com.apple/.Main@1x1"))
    }

    @Test fun removingAnUnknownPackageIsHarmless() {
        val c = cache(); c.put("a/.B@1x1", 5)
        assertEquals(0, c.removePackage("zzz")); assertEquals(1, c.size())
    }

    @Test fun leastRecentlyUsedGoesFirstWhenOverBudget() {
        val c = cache(250)
        c.put("a/.A@1x1", 100); c.put("b/.A@1x1", 100)
        c.get("a/.A@1x1")                   // a is now newer than b
        c.put("c/.A@1x1", 100)              // 300 > 250: evict the oldest, which is b
        assertNotNull(c.get("a/.A@1x1")); assertNull(c.get("b/.A@1x1")); assertNotNull(c.get("c/.A@1x1"))
        assertTrue(c.totalBytes() <= 250)
    }

    @Test fun replacingAKeyAdjustsTheByteCount() {
        val c = cache(); c.put("a/.A@1x1", 100); c.put("a/.A@1x1", 40)
        assertEquals(40L, c.totalBytes()); assertEquals(1, c.size())
    }

    @Test fun anEntryLargerThanTheBudgetIsStillKeptAlone() {
        val c = cache(50); c.put("a/.A@1x1", 10); c.put("b/.A@1x1", 80)
        assertNotNull(c.get("b/.A@1x1")); assertNull(c.get("a/.A@1x1"))
    }
}

class PrefCodecsTest {
    @Test fun bindingsRoundTrip() {
        val raw = PrefCodecs.formatBindings(listOf(Binding(19, "app", "a/.B", true), Binding(20, "search")))
        val back = PrefCodecs.parseBindings(raw)
        assertEquals(2, back.size); assertEquals(19, back[0].key); assertTrue(back[0].long); assertEquals("a/.B", back[0].arg)
        assertFalse(back[1].long); assertEquals("", back[1].arg)
    }

    @Test fun readsBindingsWrittenByOlderVersions() {
        val old = PrefCodecs.parseBindings("19|app|com.x/.Main\n20|settings")      // 3 fields, then 2 fields: no long-press flag yet
        assertEquals(2, old.size); assertFalse(old[0].long); assertEquals("com.x/.Main", old[0].arg); assertEquals("settings", old[1].action)
    }

    @Test fun skipsGarbageLinesWithoutFailing() {
        assertEquals(1, PrefCodecs.parseBindings("\nnope\nx|y|z\n7|home|\n|||").size)
        assertTrue(PrefCodecs.parseBindings("").isEmpty())
    }

    @Test fun launchLogRoundTripAndGarbage() {
        val m = PrefCodecs.parseLaunchLog(PrefCodecs.formatLaunchLog(listOf(java.util.AbstractMap.SimpleEntry("a/.B", 5L))))
        assertEquals(5L, m["a/.B"])
        assertTrue(PrefCodecs.parseLaunchLog("a|b\nc|\n|1x").size <= 1)
        assertEquals(mapOf("k" to 3L), PrefCodecs.parseLaunchLog("bad\nk|3\nm|zz"))
    }
}
