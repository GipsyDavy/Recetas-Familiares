package org.gipsybuho.recetasfamiliares

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gipsybuho.recetasfamiliares.ui.RecetasApp
import org.gipsybuho.recetasfamiliares.ui.RecetasViewModel
import org.gipsybuho.recetasfamiliares.ui.RecetasViewModelFactory
import org.gipsybuho.recetasfamiliares.ui.theme.RecetasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecetasApplication).container
        setContent {
            RecetasTheme {
                val viewModel: RecetasViewModel = viewModel(factory = RecetasViewModelFactory(container))
                RecetasApp(viewModel)
            }
        }
    }
}
