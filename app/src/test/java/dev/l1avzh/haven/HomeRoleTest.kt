package dev.l1avzh.haven

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRoleTest {
    private fun st(held: Boolean?, pkg: String?, picker: Boolean = true) = HomeRoleLogic.status(held, pkg, "dev.l1avzh.haven", picker)

    @Test fun theRoleIsAuthoritativeWhenItExists() {
        assertEquals(HomeStatus.HELD, st(true, "com.google.android.apps.tv.launcherx"))          // role says yes, whatever the resolver prints
        assertEquals(HomeStatus.NOT_HELD, st(false, "dev.l1avzh.haven"))                        // role says no even if we are the only handler
    }
    @Test fun withoutTheRoleTheResolvedHandlerDecides() {
        assertEquals(HomeStatus.HELD, st(null, "dev.l1avzh.haven"))
        assertEquals(HomeStatus.NOT_HELD, st(null, "com.google.android.tvlauncher"))
        assertEquals(HomeStatus.NOT_HELD, st(null, "android"))                                  // the system chooser: nobody picked yet
        assertEquals(HomeStatus.NOT_HELD, st(null, null))
    }
    @Test fun noWayToChooseIsReportedHonestlyNotAsSuccess() {
        assertEquals(HomeStatus.UNSUPPORTED, st(false, "x", picker = false))
        assertEquals(HomeStatus.UNSUPPORTED, st(null, null, picker = false))
        assertEquals("already held stays held even with no picker", HomeStatus.HELD, st(true, "x", picker = false))
    }
}
