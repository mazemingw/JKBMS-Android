package com.yiming.jkbms

import com.yiming.jkbms.model.EnergyTracker
import com.yiming.jkbms.model.EnergyUiState
import com.yiming.jkbms.model.TripMotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTrackerTest {
    @Test
    fun tenAmpsForSixMinutesAtThirtySixKmh_isOneAhForThreePointSixKm() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..360) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }

        assertEquals(TripMotionState.MOVING, state.motionState)
        assertEquals(1f, state.tripNetAh, 0.002f)
        assertEquals(3.6f, state.tripDistanceKm, 0.02f)
        assertEquals(3.6f, state.tripKmPerAh!!, 0.04f)
        assertEquals(36f, state.estimatedRangeKm!!, 0.5f)
        assertNotNull(state.sessionKmPerAh)
    }

    @Test
    fun fifteenSecondStopPausesButDoesNotSplitTrip() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..5) tracker.onSpeedSample(20f, second * 1_000L, second * 1_000L)
        for (second in 6..21) tracker.onSpeedSample(0f, second * 1_000L, second * 1_000L)
        assertEquals(TripMotionState.PAUSED, state.motionState)

        tracker.onSpeedSample(20f, 22_000L, 22_000L)
        assertEquals(TripMotionState.MOVING, state.motionState)
    }

    @Test
    fun regenerativeCurrentReducesNetConsumption() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..360) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(if (second < 180) -10f else 5f, 10f, elapsed, elapsed)
        }

        assertEquals(0.5f, state.tripDischargedAh, 0.01f)
        assertEquals(0.25f, state.tripRecoveredAh, 0.01f)
        assertEquals(0.25f, state.tripNetAh, 0.02f)
        assertNotNull(state.tripKmPerAh)
        assertTrue(state.tripKmPerAh!! > 10f)
    }

    @Test
    fun trendUsesHalfSecondCadenceAndKeepsOnlyOneMinuteWindow() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (halfSecond in 0..260) {
            val elapsed = halfSecond * 500L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }

        assertTrue(state.trend.size >= 115)
        assertTrue(state.trend.size <= 126)
        assertTrue(state.trend.last().timestampMs - state.trend.first().timestampMs <= 62_000L)
    }

    @Test
    fun zeroGpsSpeedImmediatelyFreezesEnergyAndTrend() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (halfSecond in 0..40) {
            val elapsed = halfSecond * 500L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }
        val tripAhAtStop = state.tripNetAh
        val sessionAhAtStop = state.sessionNetAh
        val trendSizeAtStop = state.trend.size

        tracker.onSpeedSample(0f, 20_500L, 20_500L)
        for (halfSecond in 41..61) {
            val elapsed = halfSecond * 500L
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
            tracker.onSpeedSample(0f, elapsed, elapsed)
        }

        assertEquals(tripAhAtStop, state.tripNetAh, 0.00001f)
        assertEquals(sessionAhAtStop, state.sessionNetAh, 0.00001f)
        assertEquals(trendSizeAtStop, state.trend.size)
    }

    @Test
    fun resumingAfterStopDoesNotIntegrateAcrossStoppedTime() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..10) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }
        tracker.onSpeedSample(0f, 11_000L, 11_000L)
        tracker.onBmsSample(-10f, 10f, 20_000L, 20_000L)
        val tripAhBeforeResume = state.tripNetAh

        tracker.onSpeedSample(36f, 21_000L, 21_000L)
        tracker.onBmsSample(-10f, 10f, 21_000L, 21_000L)
        assertEquals(tripAhBeforeResume, state.tripNetAh, 0.00001f)

        tracker.onBmsSample(-10f, 10f, 21_500L, 21_500L)
        assertEquals(10f * 0.5f / 3_600f, state.tripNetAh - tripAhBeforeResume, 0.0001f)
    }
}
