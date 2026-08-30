package com.yiming.jkbms.model

import kotlin.math.max

/**
 * Integrates JK BMS current and GPS speed on monotonic timestamps.
 * JK reports discharge current as negative and charge/regen current as positive.
 */
class EnergyTracker(
    private val onStateChanged: (EnergyUiState) -> Unit
) {
    private data class PendingTotals(
        var dischargedAh: Double = 0.0,
        var recoveredAh: Double = 0.0,
        var distanceKm: Double = 0.0
    ) {
        fun clear() {
            dischargedAh = 0.0
            recoveredAh = 0.0
            distanceKm = 0.0
        }
    }

    private data class RollingDelta(
        val elapsedMs: Long,
        var netAh: Double = 0.0,
        var distanceKm: Double = 0.0
    )

    private var collecting = false
    private var energyIntegrationEnabled = false
    private var motionState = TripMotionState.IDLE
    private var startCandidateAtMs: Long? = null
    private var stationarySinceMs: Long? = null

    private var lastCurrentA: Double? = null
    private var lastCurrentAtMs: Long? = null
    private var lastSpeedMps: Double? = null
    private var lastSpeedAtMs: Long? = null

    private var remainingAh: Double? = null
    private var tripDischargedAh = 0.0
    private var tripRecoveredAh = 0.0
    private var tripDistanceKm = 0.0
    private var sessionDischargedAh = 0.0
    private var sessionRecoveredAh = 0.0
    private var sessionDistanceKm = 0.0

    private val pendingStart = PendingTotals()
    private val pendingPause = PendingTotals()
    private val rolling = ArrayDeque<RollingDelta>()
    private val trend = ArrayDeque<EnergyTrendPoint>()
    private var lastTrendAtMs = 0L

    fun setCollecting(enabled: Boolean) {
        if (collecting == enabled) return
        collecting = enabled
        energyIntegrationEnabled = false
        lastCurrentA = null
        lastCurrentAtMs = null
        lastSpeedMps = null
        lastSpeedAtMs = null
        if (!enabled) {
            startCandidateAtMs = null
            if (stationarySinceMs != null && motionState == TripMotionState.MOVING) {
                commitPendingPause()
                stationarySinceMs = null
            }
        }
        publish()
    }

    fun onBmsSample(currentA: Float, capacityRemainingAh: Float?, elapsedMs: Long, wallTimeMs: Long) {
        if (capacityRemainingAh != null && capacityRemainingAh.isFinite() && capacityRemainingAh >= 0f) {
            remainingAh = capacityRemainingAh.toDouble()
        }
        if (!collecting || !energyIntegrationEnabled || !currentA.isFinite()) {
            lastCurrentA = null
            lastCurrentAtMs = null
            publish()
            return
        }

        val previousA = lastCurrentA
        val previousAt = lastCurrentAtMs
        if (previousA != null && previousAt != null) {
            val dtMs = elapsedMs - previousAt
            if (dtMs in 1..MAX_INTEGRATION_GAP_MS) {
                val hours = dtMs / 3_600_000.0
                val discharged = (max(-previousA, 0.0) + max(-currentA.toDouble(), 0.0)) * 0.5 * hours
                val recovered = (max(previousA, 0.0) + max(currentA.toDouble(), 0.0)) * 0.5 * hours
                addEnergy(discharged, recovered, elapsedMs)
            }
        }
        lastCurrentA = currentA.toDouble()
        lastCurrentAtMs = elapsedMs
        appendTrendIfReady(elapsedMs, wallTimeMs)
        publish()
    }

    fun onSpeedSample(speedKmh: Float, elapsedMs: Long, wallTimeMs: Long) {
        if (!collecting || !speedKmh.isFinite()) return
        val speedMps = max(speedKmh.toDouble(), 0.0) / 3.6

        updateMotionState(speedKmh.toDouble(), elapsedMs)
        updateEnergyIntegrationGate(
            speedKmh >= STOP_SPEED_KMH &&
                stationarySinceMs == null &&
                (motionState == TripMotionState.MOVING || startCandidateAtMs != null)
        )

        val previousSpeed = lastSpeedMps
        val previousAt = lastSpeedAtMs
        if (previousSpeed != null && previousAt != null) {
            val dtMs = elapsedMs - previousAt
            if (dtMs in 1..MAX_LOCATION_GAP_MS) {
                val distanceKm = if (previousSpeed < STOP_SPEED_MPS && speedMps < STOP_SPEED_MPS) {
                    0.0
                } else {
                    (previousSpeed + speedMps) * 0.5 * (dtMs / 1000.0) / 1000.0
                }
                addDistance(distanceKm, elapsedMs)
            }
        }
        lastSpeedMps = speedMps
        lastSpeedAtMs = elapsedMs
        appendTrendIfReady(elapsedMs, wallTimeMs)
        publish()
    }

    fun invalidateLocation() {
        lastSpeedMps = null
        lastSpeedAtMs = null
        updateEnergyIntegrationGate(false)
        publish()
    }

    fun invalidateBmsContinuity() {
        lastCurrentA = null
        lastCurrentAtMs = null
    }

    fun resetSession() {
        motionState = TripMotionState.IDLE
        energyIntegrationEnabled = false
        startCandidateAtMs = null
        stationarySinceMs = null
        lastCurrentA = null
        lastCurrentAtMs = null
        lastSpeedMps = null
        lastSpeedAtMs = null
        remainingAh = null
        tripDischargedAh = 0.0
        tripRecoveredAh = 0.0
        tripDistanceKm = 0.0
        sessionDischargedAh = 0.0
        sessionRecoveredAh = 0.0
        sessionDistanceKm = 0.0
        pendingStart.clear()
        pendingPause.clear()
        rolling.clear()
        trend.clear()
        lastTrendAtMs = 0L
        publish()
    }

    private fun updateMotionState(speedKmh: Double, elapsedMs: Long) {
        when (motionState) {
            TripMotionState.IDLE -> {
                if (speedKmh >= START_SPEED_KMH) {
                    val candidateAt = startCandidateAtMs
                    if (candidateAt == null) {
                        startCandidateAtMs = elapsedMs
                        pendingStart.clear()
                    } else if (elapsedMs - candidateAt >= START_CONFIRM_MS) {
                        beginTrip()
                    }
                } else if (speedKmh < STOP_SPEED_KMH) {
                    startCandidateAtMs = null
                    pendingStart.clear()
                }
            }

            TripMotionState.MOVING -> {
                if (speedKmh < STOP_SPEED_KMH) {
                    val stoppedAt = stationarySinceMs
                    if (stoppedAt == null) {
                        stationarySinceMs = elapsedMs
                        pendingPause.clear()
                    } else if (elapsedMs - stoppedAt >= PAUSE_CONFIRM_MS) {
                        motionState = TripMotionState.PAUSED
                    }
                } else if (speedKmh >= START_SPEED_KMH && stationarySinceMs != null) {
                    commitPendingPause()
                    stationarySinceMs = null
                }
            }

            TripMotionState.PAUSED -> {
                val stoppedAt = stationarySinceMs ?: elapsedMs.also { stationarySinceMs = it }
                if (speedKmh >= START_SPEED_KMH) {
                    commitPendingPause()
                    stationarySinceMs = null
                    motionState = TripMotionState.MOVING
                } else if (elapsedMs - stoppedAt >= TRIP_END_MS) {
                    finishTrip()
                }
            }
        }
    }

    private fun beginTrip() {
        tripDischargedAh = pendingStart.dischargedAh
        tripRecoveredAh = pendingStart.recoveredAh
        tripDistanceKm = pendingStart.distanceKm
        pendingStart.clear()
        pendingPause.clear()
        rolling.clear()
        trend.clear()
        stationarySinceMs = null
        startCandidateAtMs = null
        motionState = TripMotionState.MOVING
    }

    private fun finishTrip() {
        pendingPause.clear()
        stationarySinceMs = null
        startCandidateAtMs = null
        motionState = TripMotionState.IDLE
        lastSpeedMps = null
        lastSpeedAtMs = null
    }

    private fun commitPendingPause() {
        tripDischargedAh += pendingPause.dischargedAh
        tripRecoveredAh += pendingPause.recoveredAh
        tripDistanceKm += pendingPause.distanceKm
        pendingPause.clear()
    }

    private fun updateEnergyIntegrationGate(enabled: Boolean) {
        if (energyIntegrationEnabled == enabled) return
        energyIntegrationEnabled = enabled
        // Never bridge two BMS samples across a GPS stop or signal outage.
        lastCurrentA = null
        lastCurrentAtMs = null
    }

    private fun addEnergy(dischargedAh: Double, recoveredAh: Double, elapsedMs: Long) {
        sessionDischargedAh += dischargedAh
        sessionRecoveredAh += recoveredAh
        rollingDelta(elapsedMs).netAh += dischargedAh - recoveredAh

        when {
            motionState == TripMotionState.IDLE && startCandidateAtMs != null -> {
                pendingStart.dischargedAh += dischargedAh
                pendingStart.recoveredAh += recoveredAh
            }
            motionState == TripMotionState.MOVING -> {
                tripDischargedAh += dischargedAh
                tripRecoveredAh += recoveredAh
            }
        }
    }

    private fun addDistance(distanceKm: Double, elapsedMs: Long) {
        if (distanceKm <= 0.0) return
        sessionDistanceKm += distanceKm
        rollingDelta(elapsedMs).distanceKm += distanceKm

        when {
            motionState == TripMotionState.IDLE && startCandidateAtMs != null -> pendingStart.distanceKm += distanceKm
            stationarySinceMs != null -> pendingPause.distanceKm += distanceKm
            motionState == TripMotionState.MOVING -> tripDistanceKm += distanceKm
        }
    }

    private fun rollingDelta(elapsedMs: Long): RollingDelta {
        val tail = rolling.lastOrNull()
        if (tail != null && elapsedMs - tail.elapsedMs < ROLLING_BUCKET_MS) return tail
        return RollingDelta(elapsedMs).also {
            rolling.addLast(it)
            pruneRolling(elapsedMs)
        }
    }

    private fun pruneRolling(nowMs: Long) {
        while (rolling.isNotEmpty() && nowMs - rolling.first().elapsedMs > ROLLING_WINDOW_MS) {
            rolling.removeFirst()
        }
    }

    private fun appendTrendIfReady(elapsedMs: Long, wallTimeMs: Long) {
        if (!energyIntegrationEnabled || motionState == TripMotionState.IDLE || elapsedMs - lastTrendAtMs < TREND_INTERVAL_MS) return
        pruneRolling(elapsedMs)
        val netAh = rolling.sumOf { it.netAh }
        val distanceKm = rolling.sumOf { it.distanceKm }
        val efficiency = efficiency(distanceKm, netAh, ROLLING_MIN_DISTANCE_KM, ROLLING_MIN_NET_AH) ?: return
        trend.addLast(EnergyTrendPoint(wallTimeMs, efficiency.toFloat()))
        while (trend.isNotEmpty() && wallTimeMs - trend.first().timestampMs > TREND_RETENTION_MS) {
            trend.removeFirst()
        }
        while (trend.size > MAX_TREND_POINTS) trend.removeFirst()
        lastTrendAtMs = elapsedMs
    }

    private fun publish() {
        val visibleDischarged = tripDischargedAh + if (stationarySinceMs != null) pendingPause.dischargedAh else 0.0
        val visibleRecovered = tripRecoveredAh + if (stationarySinceMs != null) pendingPause.recoveredAh else 0.0
        val visibleDistance = tripDistanceKm + if (stationarySinceMs != null) pendingPause.distanceKm else 0.0
        val tripNet = visibleDischarged - visibleRecovered
        val sessionNet = sessionDischargedAh - sessionRecoveredAh
        val tripEfficiency = efficiency(visibleDistance, tripNet, MIN_TRIP_DISTANCE_KM, MIN_NET_AH)
        val sessionEfficiency = efficiency(sessionDistanceKm, sessionNet, MIN_SESSION_DISTANCE_KM, MIN_NET_AH)
        val rangeEfficiency = tripEfficiency ?: sessionEfficiency
        val range = if (remainingAh != null && rangeEfficiency != null) remainingAh!! * rangeEfficiency else null

        onStateChanged(
            EnergyUiState(
                motionState = motionState,
                tripNetAh = tripNet.toFloat(),
                tripDischargedAh = visibleDischarged.toFloat(),
                tripRecoveredAh = visibleRecovered.toFloat(),
                tripDistanceKm = visibleDistance.toFloat(),
                tripKmPerAh = tripEfficiency?.toFloat(),
                sessionNetAh = sessionNet.toFloat(),
                sessionDistanceKm = sessionDistanceKm.toFloat(),
                sessionKmPerAh = sessionEfficiency?.toFloat(),
                estimatedRangeKm = range?.coerceAtLeast(0.0)?.toFloat(),
                remainingAh = remainingAh?.toFloat(),
                trend = trend.toList()
            )
        )
    }

    private fun efficiency(distanceKm: Double, netAh: Double, minDistanceKm: Double, minNetAh: Double): Double? {
        if (distanceKm < minDistanceKm || netAh < minNetAh) return null
        return (distanceKm / netAh).takeIf { it.isFinite() && it in 0.05..100.0 }
    }

    private companion object {
        const val START_SPEED_KMH = 3.0
        const val STOP_SPEED_KMH = 1.0
        const val STOP_SPEED_MPS = STOP_SPEED_KMH / 3.6
        const val START_CONFIRM_MS = 3_000L
        const val PAUSE_CONFIRM_MS = 15_000L
        const val TRIP_END_MS = 180_000L
        const val MAX_INTEGRATION_GAP_MS = 10_000L
        const val MAX_LOCATION_GAP_MS = 5_000L
        const val ROLLING_WINDOW_MS = 60_000L
        const val ROLLING_BUCKET_MS = 1_000L
        const val TREND_INTERVAL_MS = 500L
        const val TREND_RETENTION_MS = 62_000L
        const val MAX_TREND_POINTS = 140
        const val MIN_TRIP_DISTANCE_KM = 0.1
        const val MIN_SESSION_DISTANCE_KM = 0.5
        const val ROLLING_MIN_DISTANCE_KM = 0.03
        const val ROLLING_MIN_NET_AH = 0.003
        const val MIN_NET_AH = 0.01
    }
}
