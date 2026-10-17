package com.walktape.app.playback.dsp

import org.junit.Assert.*
import org.junit.Test

class BassBoostRoutingTest {
    @Test fun unknownOrSpeakerNeverEnablesBass() {
        val routing = BassBoostRouting()
        assertFalse(routing.shouldEnable(true, 800))
        routing.update("main", BassBoostRouting.Route.SPEAKER, true)
        assertEquals(BassBoostRouting.Route.SPEAKER, routing.route)
        assertFalse(routing.shouldEnable(true, 800))
    }

    @Test fun headphonesRespectOptInAndZeroStrength() {
        val routing = BassBoostRouting()
        routing.update("main", BassBoostRouting.Route.HEADPHONES, true)
        assertTrue(routing.shouldEnable(true, 400))
        assertFalse(routing.shouldEnable(false, 400))
        assertFalse(routing.shouldEnable(true, 0))
        assertTrue(routing.shouldEnable(true, 50))
    }

    @Test fun overlappingDecksBlockUntilBothHaveSafeRouting() {
        val routing = BassBoostRouting()
        routing.update("outgoing", BassBoostRouting.Route.HEADPHONES, true)
        routing.update("incoming", BassBoostRouting.Route.UNKNOWN, true)
        assertFalse(routing.shouldEnable(true, 400))
        routing.update("incoming", BassBoostRouting.Route.SPEAKER, true)
        assertEquals(BassBoostRouting.Route.SPEAKER, routing.route)
        routing.update("incoming", BassBoostRouting.Route.HEADPHONES, true)
        assertTrue(routing.shouldEnable(true, 400))
    }

    @Test fun pausedPreloadAndLateReleaseDoNotBlockActiveDeck() {
        val routing = BassBoostRouting()
        routing.update("old", BassBoostRouting.Route.SPEAKER, true)
        routing.update("new", BassBoostRouting.Route.HEADPHONES, true)
        routing.update("old", BassBoostRouting.Route.SPEAKER, false)
        assertTrue(routing.shouldEnable(true, 400))
        routing.remove("old")
        assertTrue(routing.shouldEnable(true, 400))
        routing.remove("new")
        assertEquals(BassBoostRouting.Route.UNKNOWN, routing.route)
        assertFalse(routing.shouldEnable(true, 400))
    }

    @Test fun releaseClearsRouteForNextSession() {
        val routing = BassBoostRouting()
        routing.update("old", BassBoostRouting.Route.HEADPHONES, true)
        routing.clear()
        assertFalse(routing.shouldEnable(true, 400))
        routing.remove("old")
        assertEquals(BassBoostRouting.Route.UNKNOWN, routing.route)
    }
}
