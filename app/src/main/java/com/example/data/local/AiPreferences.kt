package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiSettings(
    val aiEnabled: Boolean = true,
    val autoTagAfterDownload: Boolean = false,
    val autoSummaryAfterDownload: Boolean = false,
    val wifiOnlyForAi: Boolean = false,
    val preferredSummaryLength: String = "short", // "short" or "detailed"
    val allowCloudAiForPrivateContent: Boolean = false,
    val cloudAiDisclosureAccepted: Boolean = false
)

class AiPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tubevault_ai_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private fun loadSettings(): AiSettings {
        return AiSettings(
            aiEnabled = prefs.getBoolean(KEY_AI_ENABLED, true),
            autoTagAfterDownload = prefs.getBoolean(KEY_AUTO_TAG, false),
            autoSummaryAfterDownload = prefs.getBoolean(KEY_AUTO_SUMMARY, false),
            wifiOnlyForAi = prefs.getBoolean(KEY_WIFI_ONLY_AI, false),
            preferredSummaryLength = prefs.getString(KEY_SUMMARY_LENGTH, "short") ?: "short",
            allowCloudAiForPrivateContent = prefs.getBoolean(KEY_ALLOW_CLOUD_PRIVATE, false),
            cloudAiDisclosureAccepted = prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
        )
    }

    fun setAiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AI_ENABLED, enabled) }
        _settings.value = _settings.value.copy(aiEnabled = enabled)
    }

    fun setAutoTagAfterDownload(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_TAG, enabled) }
        _settings.value = _settings.value.copy(autoTagAfterDownload = enabled)
    }

    fun setAutoSummaryAfterDownload(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SUMMARY, enabled) }
        _settings.value = _settings.value.copy(autoSummaryAfterDownload = enabled)
    }

    fun setWifiOnlyForAi(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY_AI, enabled) }
        _settings.value = _settings.value.copy(wifiOnlyForAi = enabled)
    }

    fun setPreferredSummaryLength(length: String) {
        prefs.edit { putString(KEY_SUMMARY_LENGTH, length) }
        _settings.value = _settings.value.copy(preferredSummaryLength = length)
    }

    fun setAllowCloudAiForPrivateContent(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ALLOW_CLOUD_PRIVATE, enabled) }
        _settings.value = _settings.value.copy(allowCloudAiForPrivateContent = enabled)
    }

    fun setCloudAiDisclosureAccepted(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DISCLOSURE_ACCEPTED, enabled) }
        _settings.value = _settings.value.copy(cloudAiDisclosureAccepted = enabled)
    }

    companion object {
        private const val KEY_AI_ENABLED = "pref_ai_enabled"
        private const val KEY_AUTO_TAG = "pref_auto_tag"
        private const val KEY_AUTO_SUMMARY = "pref_auto_summary"
        private const val KEY_WIFI_ONLY_AI = "pref_wifi_only_ai"
        private const val KEY_SUMMARY_LENGTH = "pref_summary_length"
        private const val KEY_ALLOW_CLOUD_PRIVATE = "pref_allow_cloud_private"
        private const val KEY_DISCLOSURE_ACCEPTED = "pref_disclosure_accepted"

        @Volatile
        private var INSTANCE: AiPreferences? = null

        fun getInstance(context: Context): AiPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
