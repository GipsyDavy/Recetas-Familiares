package org.gipsybuho.recetasfamiliares.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.sync.SyncWorker
import java.util.concurrent.TimeUnit

class RecetasViewModel(private val container: AppContainer) : ViewModel() {

    val recipes: StateFlow<List<RecipeEntity>> = container.recipeRepository.recipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stockItems: StateFlow<List<StockItemEntity>> = container.stockRepository.stockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoggedIn = MutableStateFlow(container.authRepository.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun login(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.authRepository.login(email, password)
                _isLoggedIn.value = true
                refresh()
            }.onFailure { onError(it.message ?: "No se pudo iniciar sesion") }
        }
    }

    fun logout() {
        container.authRepository.logout()
        _isLoggedIn.value = false
    }

    fun refresh() {
        viewModelScope.launch {
            container.recipeRepository.refresh()
            container.stockRepository.refresh()
        }
    }

    fun scheduleSync(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork("family-sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun ingredientsFor(recipeId: String): Flow<List<RecipeIngredientEntity>> =
        container.database.recipeIngredientDao().observeIngredients(recipeId)

    fun stepsFor(recipeId: String): Flow<List<RecipeStepEntity>> =
        container.database.recipeStepDao().observeSteps(recipeId)
}

class RecetasViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecetasViewModel(container) as T
}
