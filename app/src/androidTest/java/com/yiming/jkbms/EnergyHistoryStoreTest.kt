package com.yiming.jkbms

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiming.jkbms.model.EnergyHistoryStore
import com.yiming.jkbms.model.EnergyTracker
import com.yiming.jkbms.model.EnergyUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnergyHistoryStoreTest {
    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("energy_history_regression", Context.MODE_PRIVATE)

    @Test fun savedHistorySurvivesAStoreAndTrackerRestart() {
        prefs.edit().clear().commit()
        val before = EnergyUiState(
            tripDischargedAh = 1.2f, tripRecoveredAh = 0.2f, tripDistanceKm = 3.6f,
            sessionDischargedAh = 2f, sessionRecoveredAh = 0.5f, sessionDistanceKm = 6f,
            remainingAh = 10f
        )
        val editor = prefs.edit()
        EnergyHistoryStore(prefs).write(before, editor)
        assertTrue(editor.commit())
        var restored = EnergyUiState()
        EnergyTracker { restored = it }.restoreLastSession(EnergyHistoryStore(prefs).read()!!)
        assertTrue(restored.isHistorical)
        assertEquals(1f, restored.tripNetAh, 0.0001f)
        assertEquals(3.6f, restored.tripDistanceKm, 0f)
        assertEquals(36f, restored.estimatedRangeKm!!, 0.001f)
    }

    @Test fun corruptOrMissingHistoryDoesNotPreventStartup() {
        prefs.edit().clear().commit()
        assertNull(EnergyHistoryStore(prefs).read())
        prefs.edit().putString("pref_last_energy_session", "broken json").commit()
        assertNull(EnergyHistoryStore(prefs).read())
    }
}
