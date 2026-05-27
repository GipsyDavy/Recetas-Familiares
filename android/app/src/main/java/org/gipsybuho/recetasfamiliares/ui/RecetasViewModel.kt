package org.gipsybuho.recetasfamiliares.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.sync.SyncWorker
import java.util.concurrent.TimeUnit

class RecetasViewModel(private val container: AppContainer) : ViewModel() {
    val recipes: StateFlow<List<RecipeEntity>> = container.recipeRepository.recipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stockItems: StateFlow<List<StockItemEntity>> = container.stockRepository.stockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isLoggedIn = container.authRepository.isLoggedIn
        private set

    fun login(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.authRepository.login(email, password)
                isLoggedIn = true
                refresh()
            }.onFailure { onError(it.message ?: "No se pudo iniciar sesion") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            container.recipeRepository.refresh()
            container.stockRepository.refresh()
        }
    }

    fun scheduleSync(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork("family-sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class RecetasViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecetasViewModel(container) as T
    }
}
