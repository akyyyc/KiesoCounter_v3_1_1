package com.example.kiesocounter_v3_1_1

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ========== BEÁLLÍTÁSOK ADATSTRUKTÚRA ==========

data class AppSettings(
    val dialogOpacity: Float = 0.8f,  // ← VÁLTOZOTT! 0-100% (0.0-1.0), alapértelmezett 80%
    val smartButtonsDays: Int = 7,
    val lastWorkdaySearchDepth: Int = 30,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val fontSize: FontSizeOption = FontSizeOption.NORMAL
)

enum class DarkModeOption {
    LIGHT,      // Mindig világos
    DARK,       // Mindig sötét
    SYSTEM      // Rendszer szerint (alapértelmezett)
}

enum class FontSizeOption(val label: String, val scale: Float) {
    SMALL("Kicsi", 0.85f),
    NORMAL("Normál", 1.0f),
    LARGE("Nagy", 1.15f),
    XLARGE("Extra nagy", 1.3f)
}

// ========== BEÁLLÍTÁSOK KEZELŐ ==========

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // ========== BETÖLTÉS ==========
    private fun loadSettings(): AppSettings {
        return AppSettings(
            dialogOpacity = prefs.getFloat("dialog_opacity", 0.8f),  // ← VÁLTOZOTT!
            smartButtonsDays = prefs.getInt("smart_buttons_days", 7),
            lastWorkdaySearchDepth = prefs.getInt("last_workday_depth", 30),
            darkMode = DarkModeOption.valueOf(
                prefs.getString("dark_mode", DarkModeOption.SYSTEM.name) ?: DarkModeOption.SYSTEM.name
            ),
            fontSize = FontSizeOption.valueOf(
                prefs.getString("font_size", FontSizeOption.NORMAL.name) ?: FontSizeOption.NORMAL.name
            )
        )
    }

    // ========== MENTÉS ==========
    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putFloat("dialog_opacity", newSettings.dialogOpacity)
            putInt("smart_buttons_days", newSettings.smartButtonsDays)
            putInt("last_workday_depth", newSettings.lastWorkdaySearchDepth)
            putString("dark_mode", newSettings.darkMode.name)
            putString("font_size", newSettings.fontSize.name)
            apply()
        }
        _settings.value = newSettings
    }

    // ========== EGYEDI FRISSÍTÉSEK ==========
    fun updateDialogOpacity(opacity: Float) {
        updateSettings(_settings.value.copy(dialogOpacity = opacity))
    }

    fun updateSmartButtonsDays(days: Int) {
        updateSettings(_settings.value.copy(smartButtonsDays = days))
    }

    fun updateLastWorkdayDepth(depth: Int) {
        updateSettings(_settings.value.copy(lastWorkdaySearchDepth = depth))
    }

    fun updateDarkMode(mode: DarkModeOption) {
        updateSettings(_settings.value.copy(darkMode = mode))
    }

    fun updateFontSize(size: FontSizeOption) {
        updateSettings(_settings.value.copy(fontSize = size))
    }
}