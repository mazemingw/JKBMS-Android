package com.yiming.jkbms.ui.components

import android.annotation.SuppressLint
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject

data class EChartPoint(val timestampMs: Long, val value: Float)
data class EChartPieSlice(val name: String, val value: Float, val color: Color)

private class LocalChartWebView(context: android.content.Context) : WebView(context) {
    var pageReady = false
    var lastPayload: String? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EChartsLine(
    points: List<EChartPoint>,
    name: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    showArea: Boolean = false,
    windowMs: Long? = null,
    windowStepMs: Long? = null,
    showExtrema: Boolean = false,
    pieSlices: List<EChartPieSlice> = emptyList()
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val payload = remember(
        points, name, unit, color, dark, decimals, showArea,
        windowMs, windowStepMs, showExtrema, pieSlices
    ) {
        buildPayload(
            points, name, unit, color, dark, decimals, showArea,
            windowMs, windowStepMs, showExtrema, pieSlices
        )
    }
    val latestPayload by rememberUpdatedState(payload)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            LocalChartWebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(TRANSPARENT)
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    @Deprecated("Deprecated in WebViewClient")
                    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(Uri.parse(url))
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        pageReady = true
                        pushPayload(latestPayload)
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/echarts/chart.html")
            }
        },
        update = { view ->
            if (view.pageReady) view.pushPayload(payload)
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        }
    )
}

private fun LocalChartWebView.pushPayload(payload: String) {
    if (lastPayload == payload) return
    lastPayload = payload
    evaluateJavascript("window.updateChart && window.updateChart($payload);", null)
}

private fun buildPayload(
    points: List<EChartPoint>,
    name: String,
    unit: String,
    color: Color,
    dark: Boolean,
    decimals: Int,
    showArea: Boolean,
    windowMs: Long?,
    windowStepMs: Long?,
    showExtrema: Boolean,
    pieSlices: List<EChartPieSlice>
): String {
    val data = JSONArray()
    points.forEach { point ->
        data.put(JSONArray().put(point.timestampMs).put(point.value.toDouble()))
    }
    return JSONObject()
        .put("name", name)
        .put("unit", unit)
        .put("color", color.toCssHex())
        .put("areaColor", color.toCssHex())
        .put("dark", dark)
        .put("decimals", decimals.coerceIn(0, 4))
        .put("area", showArea)
        .put("showExtrema", showExtrema)
        .apply { if (windowMs != null && windowMs > 0L) put("windowMs", windowMs) }
        .apply {
            if (windowMs != null && windowStepMs != null && windowStepMs > 0L) {
                put("windowSlots", (windowMs / windowStepMs).coerceAtLeast(2L))
                put("windowStepMs", windowStepMs)
            }
        }
        .put("pie", JSONArray().apply {
            pieSlices.filter { it.value.isFinite() && it.value >= 0f }.forEach { slice ->
                put(
                    JSONObject()
                        .put("name", slice.name)
                        .put("value", slice.value.toDouble())
                        .put("color", slice.color.toCssHex())
                )
            }
        })
        .put("points", data)
        .toString()
}

private fun Color.toCssHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
