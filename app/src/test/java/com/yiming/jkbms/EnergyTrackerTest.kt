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
    fun savedTripIsVisibleImmediatelyWithoutRestoringIntegrationClocks() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.restoreTotal(2f, 0.5f, 6f)
        tracker.restoreLastSession(EnergyUiState(
            tripDischargedAh = 1.2f, tripRecoveredAh = 0.2f, tripDistanceKm = 3.6f,
            sessionDischargedAh = 1.2f, sessionRecoveredAh = 0.2f, sessionDistanceKm = 3.6f,
            remainingAh = 10f
        ))
        assertTrue(state.isHistorical)
        assertEquals(3.6f, state.tripDistanceKm, 0f)
        assertEquals(1f, state.tripNetAh, 0.0001f)
        assertEquals(3.6f, state.tripKmPerAh!!, 0.0001f)
        assertEquals(36f, state.estimatedRangeKm!!, 0.001f)
        tracker.setCollecting(true)
        tracker.onBmsSample(-100f, 10f, 1_000_000L, 1_000_000L)
        assertEquals(1f, state.tripNetAh, 0.0001f)

        for (second in 0..5) {
            val time = 1_001_000L + second * 1_000L
            tracker.onSpeedSample(36f, time, time)
            tracker.onBmsSample(-10f, 10f, time, time)
        }
        assertTrue(!state.isHistorical)
        assertTrue(state.tripDistanceKm < 0.1f)
        assertTrue(state.tripNetAh < 0.02f)
        assertTrue(state.totalDistanceKm > 6f)
        assertTrue(state.totalDistanceKm < 6.1f)
    }

    @Test
    fun bmsLossRequiresFreshGpsBaselinesAndDoesNotBridgeEnergyOrDistance() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)
        for (second in 0..10) {
            val time = second * 1_000L
            tracker.onSpeedSample(36f, time, time)
            tracker.onBmsSample(-30f, 10f, time, time)
        }
        val before = state
        tracker.invalidateBmsContinuity()
        tracker.onBmsSample(-30f, 10f, 13_000L, 13_000L)
        tracker.onSpeedSample(36f, 13_000L, 13_000L)
        tracker.onBmsSample(-30f, 10f, 13_000L, 13_000L)
        assertEquals(before.tripNetAh, state.tripNetAh, 0.00001f)
        assertEquals(before.tripDistanceKm, state.tripDistanceKm, 0.00001f)
        tracker.onSpeedSample(36f, 14_000L, 14_000L)
        tracker.onBmsSample(-30f, 10f, 14_000L, 14_000L)
        assertEquals(before.tripNetAh, state.tripNetAh, 0.00001f)
        tracker.onBmsSample(-30f, 10f, 14_500L, 14_500L)
        assertEquals(30f * 0.5f / 3_600f, state.tripNetAh - before.tripNetAh, 0.0001f)
    }

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
        // The first two GPS fixes establish continuity before current integration starts.
        assertEquals(359f / 360f, state.tripNetAh, 0.002f)
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

        tracker.onSpeedSample(36f, 21_500L, 21_500L)
        tracker.onBmsSample(-10f, 10f, 21_500L, 21_500L)
        assertEquals(tripAhBeforeResume, state.tripNetAh, 0.00001f)

        tracker.onBmsSample(-10f, 10f, 22_000L, 22_000L)
        assertEquals(10f * 0.5f / 3_600f, state.tripNetAh - tripAhBeforeResume, 0.0001f)
    }

    @Test
    fun gpsRecoveryRequiresTwoFreshFixesAndNeverBridgesTheOutage() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..10) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-30f, 10f, elapsed, elapsed)
        }
        tracker.invalidateLocation(15_000L)
        tracker.onBmsSample(-30f, 10f, 120_000L, 120_000L)
        val beforeRecovery = state.tripNetAh

        tracker.onSpeedSample(80f, 120_000L, 120_000L)
        tracker.onBmsSample(-30f, 10f, 120_000L, 120_000L)
        assertEquals(beforeRecovery, state.tripNetAh, 0.00001f)

        tracker.onSpeedSample(36f, 121_000L, 121_000L)
        tracker.onBmsSample(-30f, 10f, 121_000L, 121_000L)
        assertEquals(beforeRecovery, state.tripNetAh, 0.00001f)

        tracker.onBmsSample(-30f, 10f, 121_500L, 121_500L)
        assertEquals(30f * 0.5f / 3_600f, state.tripNetAh - beforeRecovery, 0.0001f)
    }

    @Test
    fun unavailableGpsAdvancesTripIntoPausedStateWithoutAddingEnergy() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.setCollecting(true)

        for (second in 0..5) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(20f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }
        val beforeLoss = state.tripNetAh
        tracker.invalidateLocation(6_000L)
        tracker.invalidateLocation(22_000L)
        tracker.onBmsSample(-10f, 10f, 22_000L, 22_000L)

        assertEquals(TripMotionState.PAUSED, state.motionState)
        assertEquals(beforeLoss, state.tripNetAh, 0.00001f)
    }

    @Test
    fun totalRestoresAcrossRunsAndClearDoesNotResetTripA() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.restoreTotal(dischargedAh = 2f, recoveredAh = 0.5f, distanceKm = 6f)
        tracker.setCollecting(true)

        for (second in 0..10) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }

        assertTrue(state.totalDistanceKm > 6f)
        assertTrue(state.totalNetAh > 1.5f)
        assertNotNull(state.totalKmPerAh)
        val tripDistance = state.tripDistanceKm
        val tripAh = state.tripNetAh

        tracker.clearTotal()

        assertEquals(0f, state.totalDistanceKm, 0f)
        assertEquals(0f, state.totalNetAh, 0f)
        assertEquals(tripDistance, state.tripDistanceKm, 0f)
        assertEquals(tripAh, state.tripNetAh, 0f)
    }

    @Test
    fun mockStyleCollectionCanBeExcludedFromTotal() {
        var state = EnergyUiState()
        val tracker = EnergyTracker { state = it }
        tracker.restoreTotal(dischargedAh = 1f, recoveredAh = 0.2f, distanceKm = 3f)
        tracker.setAccumulateTotal(false)
        tracker.setCollecting(true)

        for (second in 0..10) {
            val elapsed = second * 1_000L
            tracker.onSpeedSample(36f, elapsed, elapsed)
            tracker.onBmsSample(-10f, 10f, elapsed, elapsed)
        }

        assertEquals(3f, state.totalDistanceKm, 0f)
        assertEquals(0.8f, state.totalNetAh, 0.00001f)
        assertTrue(state.tripDistanceKm > 0f)
    }
}
