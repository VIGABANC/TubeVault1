package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserSettings(
    val blockPopups: Boolean = true,
    val blockAdRedirects: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val theme: String = "System" // "System", "Light", "Dark", "OLED"
)

class BrowserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tubevault_browser_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()

    private fun loadSettings(): BrowserSettings {
        return BrowserSettings(
            blockPopups = prefs.getBoolean(KEY_BLOCK_POPUPS, true),
            blockAdRedirects = prefs.getBoolean(KEY_BLOCK_AD_REDIRECTS, true),
            javascriptEnabled = prefs.getBoolean(KEY_JS_ENABLED, true),
            theme = prefs.getString(KEY_THEME, "System") ?: "System"
        )
    }

    fun setBlockPopups(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BLOCK_POPUPS, enabled) }
        _settings.value = _settings.value.copy(blockPopups = enabled)
    }

    fun setBlockAdRedirects(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BLOCK_AD_REDIRECTS, enabled) }
        _settings.value = _settings.value.copy(blockAdRedirects = enabled)
    }

    fun setJavascriptEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_JS_ENABLED, enabled) }
        _settings.value = _settings.value.copy(javascriptEnabled = enabled)
    }

    fun setTheme(theme: String) {
        prefs.edit { putString(KEY_THEME, theme) }
        _settings.value = _settings.value.copy(theme = theme)
    }

    companion object {
        private const val KEY_BLOCK_POPUPS = "browser_block_popups"
        private const val KEY_BLOCK_AD_REDIRECTS = "browser_block_ad_redirects"
        private const val KEY_JS_ENABLED = "browser_js_enabled"
        private const val KEY_THEME = "browser_theme"

        @Volatile
        private var INSTANCE: BrowserPreferences? = null

        fun getInstance(context: Context): BrowserPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BrowserPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
