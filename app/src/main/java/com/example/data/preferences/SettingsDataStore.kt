package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT_MULT = floatPreferencesKey("line_height_mult")
        val HORIZONTAL_MARGIN_DP = intPreferencesKey("horizontal_margin_dp")
        val VERTICAL_MARGIN_DP = intPreferencesKey("vertical_margin_dp")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val THEME = stringPreferencesKey("theme")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val USE_SYSTEM_BRIGHTNESS = booleanPreferencesKey("use_system_brightness")
        val JUSTIFY_TEXT = booleanPreferencesKey("justify_text")
        val HYPHENATION = booleanPreferencesKey("hyphenation")
        val SEEDED_SAMPLES = booleanPreferencesKey("seeded_samples_v1")
    }

    val settingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 18f).coerceIn(12f, 34f),
            lineHeightMultiplier = (prefs[Keys.LINE_HEIGHT_MULT] ?: 1.45f).coerceIn(1.1f, 2.1f),
            horizontalMarginDp = (prefs[Keys.HORIZONTAL_MARGIN_DP] ?: 24).coerceIn(12, 48),
            verticalMarginDp = (prefs[Keys.VERTICAL_MARGIN_DP] ?: 16).coerceIn(8, 40),
            fontFamily = ReaderFontFamily.fromKey(prefs[Keys.FONT_FAMILY] ?: ReaderFontFamily.LITERATA.key),
            theme = ReaderTheme.fromKey(prefs[Keys.THEME] ?: ReaderTheme.LIGHT.key),
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            readingMode = ReadingMode.fromKey(prefs[Keys.READING_MODE] ?: ReadingMode.PAGED.key),
            brightness = (prefs[Keys.BRIGHTNESS] ?: 0.75f).coerceIn(0.05f, 1.0f),
            useSystemBrightness = prefs[Keys.USE_SYSTEM_BRIGHTNESS] ?: true,
            justifyText = prefs[Keys.JUSTIFY_TEXT] ?: true,
            hyphenation = prefs[Keys.HYPHENATION] ?: true
        )
    }

    val isSamplesSeededFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEEDED_SAMPLES] ?: false
    }

    suspend fun markSamplesSeeded() {
        context.dataStore.edit { it[Keys.SEEDED_SAMPLES] = true }
    }

    suspend fun updateFontSize(sizeSp: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(12f, 34f) }
    }

    suspend fun updateLineHeight(multiplier: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT_MULT] = multiplier.coerceIn(1.1f, 2.1f) }
    }

    suspend fun updateMargins(horizontalDp: Int, verticalDp: Int) {
        context.dataStore.edit {
            it[Keys.HORIZONTAL_MARGIN_DP] = horizontalDp.coerceIn(12, 48)
            it[Keys.VERTICAL_MARGIN_DP] = verticalDp.coerceIn(8, 40)
        }
    }

    suspend fun updateFontFamily(fontFamily: ReaderFontFamily) {
        context.dataStore.edit { it[Keys.FONT_FAMILY] = fontFamily.key }
    }

    suspend fun updateTheme(theme: ReaderTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.key }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateReadingMode(mode: ReadingMode) {
        context.dataStore.edit { it[Keys.READING_MODE] = mode.key }
    }

    suspend fun updateBrightness(brightness: Float, useSystem: Boolean) {
        context.dataStore.edit {
            it[Keys.BRIGHTNESS] = brightness.coerceIn(0.05f, 1.0f)
            it[Keys.USE_SYSTEM_BRIGHTNESS] = useSystem
        }
    }

    suspend fun updateJustify(enabled: Boolean) {
        context.dataStore.edit { it[Keys.JUSTIFY_TEXT] = enabled }
    }

    suspend fun updateHyphenation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HYPHENATION] = enabled }
    }
}
