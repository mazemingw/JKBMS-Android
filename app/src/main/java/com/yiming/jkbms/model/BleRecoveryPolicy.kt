package com.yiming.jkbms.model

/** Uses monotonic time and valid battery frames, never individual notification fragments. */
object BleRecoveryPolicy {
    const val DATA_TIMEOUT_MS = 3_000L
    const val CONNECTION_TIMEOUT_MS = 15_000L

    fun needsReconnect(
        hasGatt: Boolean,
        authenticated: Boolean,
        connectionStartedMs: Long,
        lastMetricsMs: Long?,
        nowMs: Long
    ): Boolean = when {
        !hasGatt -> true
        !authenticated || lastMetricsMs == null -> nowMs - connectionStartedMs >= CONNECTION_TIMEOUT_MS
        else -> nowMs - lastMetricsMs >= DATA_TIMEOUT_MS
    }
}
