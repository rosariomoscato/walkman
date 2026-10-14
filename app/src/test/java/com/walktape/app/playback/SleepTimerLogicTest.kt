package com.walktape.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerLogicTest {

    @Test
    fun countdownFadeReachesUnityAtStartAndZeroAtPause() {
        assertEquals(1f, SleepTimer.gainFromElapsed(0), 0f)
        assertEquals(0f, SleepTimer.gainFromElapsed(SleepTimer.FADE_MS), 1e-6f)
        assertEquals(0.5f, SleepTimer.gainFromElapsed(SleepTimer.FADE_MS / 2), 0.02f)
        assertEquals(1f, SleepTimer.gainFromElapsed(-50), 0f)
    }

    @Test
    fun boundaryFadeTracksRemainingTime() {
        assertEquals(1f, SleepTimer.gainFromRemaining(SleepTimer.FADE_MS), 0f)
        assertEquals(0f, SleepTimer.gainFromRemaining(0), 0f)
        assertEquals(0.75f, SleepTimer.gainFromRemaining(SleepTimer.FADE_MS * 3 / 4), 0.02f)
        assertEquals(1f, SleepTimer.gainFromRemaining(SleepTimer.FADE_MS * 10), 0f)
    }

    @Test
    fun armedStateFollowsModeAndCountdownKeepsRemaining() {
        val unarmed = SleepTimer.State()
        assertFalse(unarmed.armed)
        val countdown = SleepTimer.State(SleepTimer.Mode.DURATION, 5_000)
        assertTrue(countdown.armed)
        assertEquals(5_000L, countdown.remainingMs)
        assertTrue(SleepTimer.State(SleepTimer.Mode.QUEUE).armed)
    }
}
