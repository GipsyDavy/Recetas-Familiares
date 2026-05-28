package org.gipsybuho.recetasfamiliares

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import org.gipsybuho.recetasfamiliares.auth.AuthRepository
import org.gipsybuho.recetasfamiliares.auth.LoginScreen
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.recipes.RecipeListScreen
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository

@Composable
fun App() {
    val session        = remember { SessionStore() }
    val apiClient      = remember { ApiClient(session) }
    val authRepo       = remember { AuthRepository(apiClient, session) }
    val recipeRepo     = remember { RecipeRepository(apiClient, session) }

    var isLoggedIn by remember { mutableStateOf(session.isLoggedIn) }

    MaterialTheme {
        if (!isLoggedIn) {
            LoginScreen(repository = authRepo, onLoginSuccess = { isLoggedIn = true })
        } else {
            RecipeListScreen(repository = recipeRepo)
        }
    }
}
