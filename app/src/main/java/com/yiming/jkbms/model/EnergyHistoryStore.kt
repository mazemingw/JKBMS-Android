package com.yiming.jkbms.model

import android.content.SharedPreferences
import org.json.JSONObject

/** Only completed measurements are restored; clocks and integration baselines never are. */
class EnergyHistoryStore(private val prefs: SharedPreferences) {
    fun read(): EnergyUiState? = runCatching {
        val json = JSONObject(prefs.getString(KEY, null) ?: return null)
        EnergyUiState(
            tripDischargedAh = json.value("tripDischargedAh"),
            tripRecoveredAh = json.value("tripRecoveredAh"),
            tripDistanceKm = json.value("tripDistanceKm"),
            sessionDischargedAh = json.value("sessionDischargedAh"),
            sessionRecoveredAh = json.value("sessionRecoveredAh"),
            sessionDistanceKm = json.value("sessionDistanceKm"),
            remainingAh = if (json.has("remainingAh")) json.value("remainingAh") else null
        )
    }.getOrNull()

    fun write(state: EnergyUiState, editor: SharedPreferences.Editor) {
        val json = JSONObject()
            .put("tripDischargedAh", state.tripDischargedAh)
            .put("tripRecoveredAh", state.tripRecoveredAh)
            .put("tripDistanceKm", state.tripDistanceKm)
            .put("sessionDischargedAh", state.sessionDischargedAh)
            .put("sessionRecoveredAh", state.sessionRecoveredAh)
            .put("sessionDistanceKm", state.sessionDistanceKm)
        state.remainingAh?.takeIf { it.isFinite() && it >= 0f }?.let { json.put("remainingAh", it) }
        editor.putString(KEY, json.toString())
    }

    private fun JSONObject.value(key: String): Float =
        optDouble(key, 0.0).toFloat().takeIf { it.isFinite() && it >= 0f } ?: 0f

    private companion object {
        const val KEY = "pref_last_energy_session"
    }
}
