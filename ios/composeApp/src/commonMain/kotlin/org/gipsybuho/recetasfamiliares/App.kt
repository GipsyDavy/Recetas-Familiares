package org.gipsybuho.recetasfamiliares

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import org.gipsybuho.recetasfamiliares.auth.AuthRepository
import org.gipsybuho.recetasfamiliares.auth.LoginScreen
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.ui.MainTabScreen

@Composable
fun App() {
    val session       = remember { SessionStore() }
    val apiClient     = remember { ApiClient(session) }
    val authRepo      = remember { AuthRepository(apiClient, session) }
    val driverFactory = remember { DatabaseDriverFactory() }

    var isLoggedIn by remember { mutableStateOf(session.isLoggedIn) }

    MaterialTheme {
        if (!isLoggedIn) {
            LoginScreen(repository = authRepo, onLoginSuccess = { isLoggedIn = true })
        } else {
            MainTabScreen(
                apiClient     = apiClient,
                session       = session,
                driverFactory = driverFactory,
                onLogout      = { session.clear(); isLoggedIn = false }
            )
        }
    }
}
