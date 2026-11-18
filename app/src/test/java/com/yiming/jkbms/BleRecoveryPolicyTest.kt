package com.yiming.jkbms

import com.yiming.jkbms.model.BleRecoveryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRecoveryPolicyTest {
    @Test fun disconnectedSessionCanRecoverWithoutWaitingForAnOldConnectionFlag() {
        assertTrue(BleRecoveryPolicy.needsReconnect(false, false, 0L, null, 100L))
    }

    @Test fun pendingConnectionAndAuthenticationHaveABoundedTimeout() {
        assertFalse(BleRecoveryPolicy.needsReconnect(true, false, 1_000L, null, 15_999L))
        assertTrue(BleRecoveryPolicy.needsReconnect(true, false, 1_000L, null, 16_000L))
    }

    @Test fun authenticationWithoutBatteryDataAlsoTimesOut() {
        assertTrue(BleRecoveryPolicy.needsReconnect(true, true, 1_000L, null, 16_000L))
    }

    @Test fun foregroundResumeImmediatelyDetectsStaleBatteryData() {
        assertFalse(BleRecoveryPolicy.needsReconnect(true, true, 0L, 5_000L, 7_999L))
        assertTrue(BleRecoveryPolicy.needsReconnect(true, true, 0L, 5_000L, 8_000L))
        assertTrue(BleRecoveryPolicy.needsReconnect(true, true, 0L, 5_000L, 300_000L))
    }
}
