package org.gipsybuho.recetasfamiliares

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import org.gipsybuho.recetasfamiliares.auth.AuthRepository
import org.gipsybuho.recetasfamiliares.auth.LoginScreen
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.sync.SyncRepository
import org.gipsybuho.recetasfamiliares.theme.AppTheme
import org.gipsybuho.recetasfamiliares.theme.AppTypography
import org.gipsybuho.recetasfamiliares.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.theme.ThemePreference
import org.gipsybuho.recetasfamiliares.theme.darkColors
import org.gipsybuho.recetasfamiliares.theme.lightColors
import org.gipsybuho.recetasfamiliares.ui.MainTabScreen

@Composable
fun App() {
    val session       = remember { SessionStore() }
    val apiClient     = remember { ApiClient(session) }
    val authRepo      = remember { AuthRepository(apiClient, session) }
    val driverFactory = remember { DatabaseDriverFactory() }
    val syncRepo      = remember { SyncRepository(apiClient, session, driverFactory) }
    val themePref     = remember { ThemePreference() }

    var isLoggedIn    by remember { mutableStateOf(session.isLoggedIn) }
    var selectedTheme by remember { mutableStateOf(themePref.selectedTheme) }
    var themeMode     by remember { mutableStateOf(themePref.themeMode) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) syncRepo.pullIncremental()
    }

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) selectedTheme.darkColors() else selectedTheme.lightColors(),
        typography  = AppTypography
    ) {
        if (!isLoggedIn) {
            LoginScreen(repository = authRepo, onLoginSuccess = { isLoggedIn = true })
        } else {
            MainTabScreen(
                apiClient     = apiClient,
                session       = session,
                driverFactory = driverFactory,
                syncRepo      = syncRepo,
                selectedTheme = selectedTheme,
                themeMode     = themeMode,
                onThemeChange = { t -> selectedTheme = t; themePref.selectedTheme = t },
                onModeChange  = { m -> themeMode = m; themePref.themeMode = m },
                onLogout      = { session.clear(); isLoggedIn = false }
            )
        }
    }
}
