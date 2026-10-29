package com.floatinglens.floatinglensaitranslator.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlaySettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "floating_lens_settings_prefs"

        private const val KEY_SOURCE_LANG = "key_source_lang"
        private const val KEY_TARGET_LANG = "key_target_lang"
        private const val KEY_PREFER_PLUGIN = "key_prefer_plugin"
        private const val KEY_PILL_COLOR_HEX = "key_pill_color_hex"
        private const val KEY_CORNER_RADIUS_DP = "key_corner_radius_dp"
        private const val KEY_TEXT_COLOR_HEX = "key_text_color_hex"
        private const val KEY_AUTO_SCALE_TEXT = "key_auto_scale_text"
        private const val KEY_FILTER_FURIGANA = "key_filter_furigana"

        @Volatile
        private var instance: OverlaySettingsRepository? = null

        fun getInstance(context: Context): OverlaySettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: OverlaySettingsRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    private fun loadSettings(): OverlaySettings {
        return OverlaySettings(
            sourceLanguage = prefs.getString(KEY_SOURCE_LANG, TranslateLanguage.JAPANESE)
                ?: TranslateLanguage.JAPANESE,
            targetLanguage = prefs.getString(KEY_TARGET_LANG, TranslateLanguage.ENGLISH)
                ?: TranslateLanguage.ENGLISH,
            preferPlugin = prefs.getBoolean(KEY_PREFER_PLUGIN, true),
            pillColorHex = prefs.getString(KEY_PILL_COLOR_HEX, "#FFFFFF") ?: "#FFFFFF",
            cornerRadiusDp = prefs.getInt(KEY_CORNER_RADIUS_DP, 8),
            textColorHex = prefs.getString(KEY_TEXT_COLOR_HEX, "#111111") ?: "#111111",
            autoScaleText = prefs.getBoolean(KEY_AUTO_SCALE_TEXT, true),
            filterFurigana = prefs.getBoolean(KEY_FILTER_FURIGANA, true)
        )
    }

    fun updateSourceLanguage(lang: String) {
        prefs.edit().putString(KEY_SOURCE_LANG, lang).apply()
        _settings.value = _settings.value.copy(sourceLanguage = lang)
    }

    fun updateTargetLanguage(lang: String) {
        prefs.edit().putString(KEY_TARGET_LANG, lang).apply()
        _settings.value = _settings.value.copy(targetLanguage = lang)
    }

    fun updatePreferPlugin(preferPlugin: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_PLUGIN, preferPlugin).apply()
        _settings.value = _settings.value.copy(preferPlugin = preferPlugin)
    }

    fun updatePillColor(colorHex: String) {
        prefs.edit().putString(KEY_PILL_COLOR_HEX, colorHex).apply()
        _settings.value = _settings.value.copy(pillColorHex = colorHex)
    }

    fun updateCornerRadius(radiusDp: Int) {
        prefs.edit().putInt(KEY_CORNER_RADIUS_DP, radiusDp).apply()
        _settings.value = _settings.value.copy(cornerRadiusDp = radiusDp)
    }

    fun updateTextColor(colorHex: String) {
        prefs.edit().putString(KEY_TEXT_COLOR_HEX, colorHex).apply()
        _settings.value = _settings.value.copy(textColorHex = colorHex)
    }

    fun updateAutoScaleText(autoScale: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCALE_TEXT, autoScale).apply()
        _settings.value = _settings.value.copy(autoScaleText = autoScale)
    }

    fun updateFilterFurigana(filter: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_FURIGANA, filter).apply()
        _settings.value = _settings.value.copy(filterFurigana = filter)
    }
}
