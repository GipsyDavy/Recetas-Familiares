package org.gipsybuho.recetasfamiliares.theme

import platform.Foundation.NSUserDefaults

actual class ThemePreference {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual var selectedTheme: AppTheme
        get() {
            val name = defaults.stringForKey(KEY_THEME) ?: return AppTheme.BOSQUE
            return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.BOSQUE)
        }
        set(value) { defaults.setObject(value.name, forKey = KEY_THEME) }

    actual var themeMode: ThemeMode
        get() {
            val name = defaults.stringForKey(KEY_MODE) ?: return ThemeMode.SYSTEM
            return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
        }
        set(value) { defaults.setObject(value.name, forKey = KEY_MODE) }

    companion object {
        private const val KEY_THEME = "rf_selected_theme"
        private const val KEY_MODE  = "rf_theme_mode"
    }
}
