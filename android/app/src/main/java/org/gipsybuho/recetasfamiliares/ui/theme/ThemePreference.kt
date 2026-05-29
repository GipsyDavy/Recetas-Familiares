package org.gipsybuho.recetasfamiliares.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "recetas_theme")

private val KEY_THEME      = stringPreferencesKey("selected_theme")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

class ThemePreference(private val context: Context) {

    val selectedTheme: Flow<AppTheme> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.BOSQUE
    }

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }
}
