package org.gipsybuho.recetasfamiliares

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gipsybuho.recetasfamiliares.ui.RecetasApp
import org.gipsybuho.recetasfamiliares.ui.RecetasViewModel
import org.gipsybuho.recetasfamiliares.ui.RecetasViewModelFactory
import org.gipsybuho.recetasfamiliares.ui.theme.RecetasTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as RecetasApplication).container
        val initialRecipeId = intent?.getStringExtra(EXTRA_RECIPE_ID)
        setContent {
            val viewModel: RecetasViewModel = viewModel(factory = RecetasViewModelFactory(container))
            val appTheme  by viewModel.selectedTheme.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            RecetasTheme(appTheme = appTheme, themeMode = themeMode) {
                RecetasApp(viewModel, initialRecipeId = initialRecipeId)
            }
        }
    }
}
