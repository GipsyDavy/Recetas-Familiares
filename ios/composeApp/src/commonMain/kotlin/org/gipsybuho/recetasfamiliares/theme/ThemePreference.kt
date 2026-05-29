package org.gipsybuho.recetasfamiliares.theme

expect class ThemePreference() {
    var selectedTheme: AppTheme
    var themeMode: ThemeMode
    var hapticsEnabled: Boolean
}
