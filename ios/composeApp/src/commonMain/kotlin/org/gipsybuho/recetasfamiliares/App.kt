package org.gipsybuho.recetasfamiliares

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.gipsybuho.recetasfamiliares.auth.AuthRepository
import org.gipsybuho.recetasfamiliares.auth.LoginScreen
import org.gipsybuho.recetasfamiliares.core.OnboardingPreference
import org.gipsybuho.recetasfamiliares.ui.OnboardingScreen
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.core.ServerUrlPreference
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.sync.SyncRepository
import org.gipsybuho.recetasfamiliares.theme.AppTheme
import org.gipsybuho.recetasfamiliares.theme.AppShapes
import org.gipsybuho.recetasfamiliares.theme.AppTypography
import org.gipsybuho.recetasfamiliares.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.theme.ThemePreference
import org.gipsybuho.recetasfamiliares.theme.animateAppColorScheme
import org.gipsybuho.recetasfamiliares.theme.darkColors
import org.gipsybuho.recetasfamiliares.theme.lightColors
import org.gipsybuho.recetasfamiliares.theme.rememberReduceMotionEnabled
import org.gipsybuho.recetasfamiliares.ui.MainTabScreen

@Composable
fun App() {
    val session       = remember { SessionStore() }
    val serverUrlPreference = remember { ServerUrlPreference() }
    val apiClient     = remember { ApiClient(session, serverUrlPreference) }
    val authRepo      = remember { AuthRepository(apiClient, session) }
    val driverFactory = remember { DatabaseDriverFactory() }
    val syncRepo      = remember { SyncRepository(apiClient, session, driverFactory) }
    val themePref       = remember { ThemePreference() }
    val onboardingPref  = remember { OnboardingPreference() }

    // Coil usa el HttpClient autenticado: /uploads/** requiere JWT (SEC-3)
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(apiClient.http)) }
            .build()
    }

    var isLoggedIn     by remember { mutableStateOf(session.isLoggedIn) }
    var onboardingDone by remember { mutableStateOf(onboardingPref.onboardingDone) }
    var selectedTheme  by remember { mutableStateOf(themePref.selectedTheme) }
    var themeMode      by remember { mutableStateOf(themePref.themeMode) }
    var hapticsEnabled by remember { mutableStateOf(themePref.hapticsEnabled) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) syncRepo.pullIncremental()
    }

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val targetColorScheme = if (darkTheme) selectedTheme.darkColors() else selectedTheme.lightColors()
    val reduceMotion = rememberReduceMotionEnabled()
    // Evita interpolar texto claro y fondo oscuro en direcciones opuestas: al
    // cambiar de modo se aplica el esquema completo; entre temas del mismo modo
    // se mantiene la transicion suave.
    val animatedColorScheme = if (reduceMotion) {
        targetColorScheme
    } else {
        key(darkTheme) { animateAppColorScheme(targetColorScheme) }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes
    ) {
        if (!onboardingDone) {
            OnboardingScreen(onFinished = { onboardingPref.onboardingDone = true; onboardingDone = true })
        } else if (!isLoggedIn) {
            LoginScreen(
                repository = authRepo,
                serverUrlPreference = serverUrlPreference,
                onLoginSuccess = { isLoggedIn = true }
            )
        } else {
            MainTabScreen(
                apiClient       = apiClient,
                session         = session,
                driverFactory   = driverFactory,
                syncRepo        = syncRepo,
                selectedTheme   = selectedTheme,
                themeMode       = themeMode,
                hapticsEnabled  = hapticsEnabled,
                reduceMotion    = reduceMotion,
                serverUrlPreference = serverUrlPreference,
                onThemeChange   = { t -> selectedTheme = t; themePref.selectedTheme = t },
                onModeChange    = { m -> themeMode = m; themePref.themeMode = m },
                onHapticsChange = { h -> hapticsEnabled = h; themePref.hapticsEnabled = h },
                onLogout        = { authRepo.logout(); isLoggedIn = false }
            )
        }
    }
}
