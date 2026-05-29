package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.menu.MenuRepository
import org.gipsybuho.recetasfamiliares.menu.MenuScreen
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.notes.NoteRepository
import org.gipsybuho.recetasfamiliares.notes.NotesScreen
import org.gipsybuho.recetasfamiliares.recipes.RecipeListScreen
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListScreen
import org.gipsybuho.recetasfamiliares.stock.StockRepository
import org.gipsybuho.recetasfamiliares.stock.StockScreen
import org.gipsybuho.recetasfamiliares.sync.SyncRepository
import org.gipsybuho.recetasfamiliares.theme.AppTheme
import org.gipsybuho.recetasfamiliares.theme.ThemeMode

private enum class Tab { RECIPES, STOCK, SHOPPING, NOTES, MENU, SETTINGS }

@Composable
fun MainTabScreen(
    apiClient: ApiClient,
    session: SessionStore,
    driverFactory: DatabaseDriverFactory,
    syncRepo: SyncRepository,
    selectedTheme: AppTheme,
    themeMode: ThemeMode,
    onThemeChange: (AppTheme) -> Unit,
    onModeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit
) {
    val recipeRepo   = remember { RecipeRepository(apiClient, session, driverFactory) }
    val stockRepo    = remember { StockRepository(apiClient, session, driverFactory) }
    val noteRepo     = remember { NoteRepository(apiClient, session) }
    val shoppingRepo = remember { ShoppingListRepository(apiClient, session) }
    val menuRepo     = remember { MenuRepository(apiClient, session) }

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
                NavigationBarItem(
                    selected = selectedTab == Tab.SETTINGS,
                    onClick  = { selectedTab = Tab.SETTINGS },
                    icon     = { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes") },
                    label    = { Text("Ajustes") }
                )
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                Tab.RECIPES  -> RecipeListScreen(repository = recipeRepo, syncRepo = syncRepo)
                Tab.STOCK    -> StockScreen(repository = stockRepo, syncRepo = syncRepo)
                Tab.NOTES    -> NotesScreen(repository = noteRepo)
                Tab.SHOPPING -> ShoppingListScreen(repository = shoppingRepo)
                Tab.MENU     -> MenuScreen(repository = menuRepo)
                Tab.SETTINGS -> SettingsScreen(
                    selectedTheme = selectedTheme,
                    themeMode     = themeMode,
                    onThemeChange = onThemeChange,
                    onModeChange  = onModeChange,
                    onLogout      = onLogout,
                    session       = session
                )
            }
        }
    }
}
