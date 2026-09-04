package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadSettings(
    val quickDownloadEnabled: Boolean = false,
    val defaultQuality: String = "Recommended", // "Recommended", "Best", "1080p", "720p", "480p", "Audio"
    val turboPartsMode: String = "Auto", // "Auto", "1", "2", "4", "8"
    val maxConcurrentDownloads: Int = 2,
    val wifiOnly: Boolean = false,
    val autoRetry: Boolean = true,
    val detectClipboardLinks: Boolean = false,
    val browserAutoDetect: Boolean = true,
    val customGatewayUrl: String = ""
) {
    val turboDownloadEnabled: Boolean get() = turboPartsMode != "1"
    val segmentsCount: Int get() = when (turboPartsMode) {
        "2" -> 2
        "4" -> 4
        "8" -> 8
        else -> 4
    }
}

class DownloadPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tubevault_download_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<DownloadSettings> = _settings.asStateFlow()

    private fun loadSettings(): DownloadSettings {
        return DownloadSettings(
            quickDownloadEnabled = prefs.getBoolean(KEY_QUICK_DOWNLOAD, false),
            defaultQuality = prefs.getString(KEY_DEFAULT_QUALITY, "Recommended") ?: "Recommended",
            turboPartsMode = prefs.getString(KEY_TURBO_PARTS, "Auto") ?: "Auto",
            maxConcurrentDownloads = prefs.getInt(KEY_MAX_CONCURRENT, 2),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            autoRetry = prefs.getBoolean(KEY_AUTO_RETRY, true),
            detectClipboardLinks = prefs.getBoolean(KEY_CLIPBOARD_DETECT, false),
            browserAutoDetect = prefs.getBoolean(KEY_BROWSER_AUTO_DETECT, true),
            customGatewayUrl = prefs.getString(KEY_CUSTOM_GATEWAY_URL, "") ?: ""
        )
    }

    fun setCustomGatewayUrl(url: String) {
        prefs.edit { putString(KEY_CUSTOM_GATEWAY_URL, url.trim()) }
        _settings.value = _settings.value.copy(customGatewayUrl = url.trim())
    }

    fun setQuickDownloadEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_QUICK_DOWNLOAD, enabled) }
        _settings.value = _settings.value.copy(quickDownloadEnabled = enabled)
    }

    fun setDefaultQuality(quality: String) {
        prefs.edit { putString(KEY_DEFAULT_QUALITY, quality) }
        _settings.value = _settings.value.copy(defaultQuality = quality)
    }

    fun setTurboPartsMode(mode: String) {
        prefs.edit { putString(KEY_TURBO_PARTS, mode) }
        _settings.value = _settings.value.copy(turboPartsMode = mode)
    }

    fun setMaxConcurrentDownloads(max: Int) {
        prefs.edit { putInt(KEY_MAX_CONCURRENT, max.coerceIn(1, 5)) }
        _settings.value = _settings.value.copy(maxConcurrentDownloads = max.coerceIn(1, 5))
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY, enabled) }
        _settings.value = _settings.value.copy(wifiOnly = enabled)
    }

    fun setAutoRetry(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_RETRY, enabled) }
        _settings.value = _settings.value.copy(autoRetry = enabled)
    }

    fun setDetectClipboardLinks(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLIPBOARD_DETECT, enabled) }
        _settings.value = _settings.value.copy(detectClipboardLinks = enabled)
    }

    fun setBrowserAutoDetect(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BROWSER_AUTO_DETECT, enabled) }
        _settings.value = _settings.value.copy(browserAutoDetect = enabled)
    }

    companion object {
        private const val KEY_QUICK_DOWNLOAD = "pref_quick_download"
        private const val KEY_DEFAULT_QUALITY = "pref_default_quality"
        private const val KEY_TURBO_PARTS = "pref_turbo_parts"
        private const val KEY_MAX_CONCURRENT = "pref_max_concurrent"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_AUTO_RETRY = "pref_auto_retry"
        private const val KEY_CLIPBOARD_DETECT = "pref_clipboard_detect"
        private const val KEY_BROWSER_AUTO_DETECT = "pref_browser_auto_detect"
        private const val KEY_CUSTOM_GATEWAY_URL = "pref_custom_gateway_url"

        @Volatile
        private var INSTANCE: DownloadPreferences? = null

        fun getInstance(context: Context): DownloadPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
