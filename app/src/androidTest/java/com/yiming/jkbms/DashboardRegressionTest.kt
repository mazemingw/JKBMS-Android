package com.yiming.jkbms

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiming.jkbms.model.EnergyHistoryStore
import com.yiming.jkbms.model.EnergyUiState
import com.yiming.jkbms.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardRegressionTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val resetPreferences = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("jkbms_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
                .putBoolean("pref_split_screen_info_only", true)
                .putBoolean("pref_show_mock_button", false)
            EnergyHistoryStore(prefs).write(EnergyUiState(
                tripDischargedAh = 1.2f, tripRecoveredAh = 0.2f,
                tripDistanceKm = 3.6f, remainingAh = 10f
            ), editor)
            check(editor.commit())
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(resetPreferences).around(compose)

    @Test
    fun compactPreferenceKeepsFullscreenNavigationAfterRecreation() {
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("设置").assertIsDisplayed().performClick()
        compose.onNodeWithText("分屏信息").assertExists()
    }

    @Test
    fun lastTripIsShownBeforeAnyNewBmsConnection() {
        compose.onNodeWithText("上次里程").assertIsDisplayed()
        compose.onNodeWithText("3.60 km").assertIsDisplayed()
        compose.onNodeWithText("1.000 Ah").assertIsDisplayed()
    }

    @Test
    fun twoIndependentChartSourcesSurviveRecreationAndPlaybackKeepsRecording() {
        compose.onNodeWithText("图表").performClick()
        compose.onNodeWithText("暂停录制").assertIsDisplayed()
        compose.onNodeWithText("图表 1 · 电压 (V) ▾").performClick()
        compose.onNodeWithText("电池功率 (W)").performClick()
        compose.onNodeWithText("图表 2 · 速度 (km/h) ▾").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("图表 1 · 电池功率 (W) ▾").assertIsDisplayed()
        compose.onNodeWithText("图表 2 · 速度 (km/h) ▾").assertIsDisplayed()
        compose.onNodeWithText("回读全程").performClick()
        compose.onNodeWithText("暂停录制").assertIsDisplayed()
        compose.onNodeWithText("自动录制中；回读不影响采集").assertIsDisplayed()
    }

    @Test
    fun mockAutomaticallyRecordsBatteryAndSpeedAndKeepsUnitsOnOneLine() {
        val model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.runOnIdle {
            model.gpsEnabled = true
            model.showMockButton = true
        }
        compose.onNodeWithText("MOCK").performClick()
        compose.waitUntil(5_000L) { model.chartSamples.count { it.voltage != null && it.speedKmh != null } >= 2 }
        assertUnitsOnlyBesideValues()
        // The gauge has a continuous frame animation. Freeze the test clock for layout checks.
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { model.gaugeEnabled = true }
            repeat(4) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            assertUnitsOnlyBesideValues()
        } finally {
            compose.runOnUiThread { model.gaugeEnabled = false }
            repeat(4) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("图表").performClick()
        compose.onNodeWithText("图表 1 · 电压 (V) ▾").assertIsDisplayed()
        compose.onNodeWithText("图表 2 · 速度 (km/h) ▾").assertIsDisplayed()
        assertTrue(model.chartRecording)
    }

    private fun assertUnitsOnlyBesideValues() {
        listOf("A", "W", "%", "V").forEach { unit ->
            compose.onAllNodesWithText(unit, useUnmergedTree = true).assertCountEquals(0)
        }
        val unitValues = compose.onAllNodes(SemanticsMatcher("value and unit") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text.contains('\u00A0')
            } == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(5, unitValues.size)
        unitValues.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("Overflowed unit value: ${node.config[SemanticsProperties.Text]}", layouts.single().didOverflowWidth)
        }
    }

}
