package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.notes.NoteRepository
import org.gipsybuho.recetasfamiliares.notes.NotesScreen
import org.gipsybuho.recetasfamiliares.recipes.RecipeListScreen
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository
import org.gipsybuho.recetasfamiliares.stock.StockRepository
import org.gipsybuho.recetasfamiliares.stock.StockScreen

private enum class Tab { RECIPES, STOCK, SHOPPING, NOTES, MENU }

@Composable
fun MainTabScreen(apiClient: ApiClient, session: SessionStore, driverFactory: DatabaseDriverFactory, onLogout: () -> Unit) {
    val recipeRepo = remember { RecipeRepository(apiClient, session, driverFactory) }
    val stockRepo  = remember { StockRepository(apiClient, session, driverFactory) }
    val noteRepo   = remember { NoteRepository(apiClient, session) }

    var selectedTab by remember { mutableStateOf(Tab.RECIPES) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.RECIPES,
                    onClick  = { selectedTab = Tab.RECIPES },
                    icon     = { Icon(Icons.Outlined.Restaurant, contentDescription = "Recetas") },
                    label    = { Text("Recetas") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.STOCK,
                    onClick  = { selectedTab = Tab.STOCK },
                    icon     = { Icon(Icons.Outlined.Inventory2, contentDescription = "Stock") },
                    label    = { Text("Stock") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.SHOPPING,
                    onClick  = { selectedTab = Tab.SHOPPING },
                    icon     = { Icon(Icons.Outlined.ShoppingCart, contentDescription = "Lista") },
                    label    = { Text("Lista") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.NOTES,
                    onClick  = { selectedTab = Tab.NOTES },
                    icon     = { Icon(Icons.Outlined.Description, contentDescription = "Notas") },
                    label    = { Text("Notas") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.MENU,
                    onClick  = { selectedTab = Tab.MENU },
                    icon     = { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Menú") },
                    label    = { Text("Menú") }
                )
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                Tab.RECIPES  -> RecipeListScreen(repository = recipeRepo)
                Tab.STOCK    -> StockScreen(repository = stockRepo)
                Tab.NOTES    -> NotesScreen(repository = noteRepo)
                Tab.SHOPPING -> PlaceholderScreen("Lista de la compra", "Próximamente en Sprint 19")
                Tab.MENU     -> PlaceholderScreen("Menú semanal", "Próximamente en Sprint 19")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
