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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.ui.theme.AppTheme
import org.gipsybuho.recetasfamiliares.ui.theme.ThemeMode
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeRatingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.sync.ExpiryNotificationWorker
import org.gipsybuho.recetasfamiliares.sync.SyncWorker
import java.util.concurrent.TimeUnit

class RecetasViewModel(private val container: AppContainer) : ViewModel() {

    val recipes: StateFlow<List<RecipeEntity>> = container.recipeRepository.recipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stockItems: StateFlow<List<StockItemEntity>> = container.stockRepository.stockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoggedIn = MutableStateFlow(container.authRepository.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _onboardingDone = MutableStateFlow(container.onboardingPreference.onboardingDone)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    val myUserId: String? get() = container.sessionStore.userId

    private val _displayName = MutableStateFlow(container.sessionStore.displayName)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(container.sessionStore.email)
    val email: StateFlow<String?> = _email.asStateFlow()

    private val _filterByStock = MutableStateFlow(false)
    val filterByStock: StateFlow<Boolean> = _filterByStock.asStateFlow()

    val filteredRecipes: StateFlow<List<RecipeEntity>> = combine(
        container.recipeRepository.recipes,
        stockItems,
        container.database.recipeIngredientDao().observeAllIngredients(),
        _filterByStock
    ) { recipeList, stock, ingredients, active ->
        if (!active) recipeList
        else {
            val stockNames = stock.map { it.name.lowercase().trim() }.toSet()
            if (stockNames.isEmpty()) recipeList
            else {
                val matchingIds = ingredients
                    .filter { it.name.lowercase().trim() in stockNames }
                    .map { it.recipeId }
                    .toSet()
                recipeList.filter { it.id in matchingIds }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _recipeNextPage = MutableStateFlow(1)
    private val _recipeHasMore = MutableStateFlow(false)
    val recipeHasMore: StateFlow<Boolean> = _recipeHasMore.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

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
        _displayName.value = null
        _email.value = null
    }

    fun markOnboardingDone() {
        container.onboardingPreference.onboardingDone = true
        _onboardingDone.value = true
    }

    fun toggleStockFilter() { _filterByStock.value = !_filterByStock.value }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                container.recipeRepository.refresh()
                container.stockRepository.refresh()
                runCatching { container.syncRepository.pullOnce() }
                _recipeNextPage.value = 1
                _recipeHasMore.value = container.recipeRepository.totalPages > 1
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadNextRecipePage() {
        val next = _recipeNextPage.value
        if (!_recipeHasMore.value) return
        viewModelScope.launch {
            runCatching { container.recipeRepository.loadPage(next) }
                .onSuccess {
                    _recipeNextPage.value = next + 1
                    _recipeHasMore.value = next + 1 < container.recipeRepository.totalPages
                }
        }
    }

    fun scheduleSync(workManager: WorkManager) {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork("family-sync", ExistingPeriodicWorkPolicy.UPDATE, syncRequest)

        val expiryRequest = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork("expiry-check", ExistingPeriodicWorkPolicy.KEEP, expiryRequest)
    }

    val menuItems: StateFlow<List<MenuItemEntity>> =
        container.database.menuItemDao().observeMenuItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shoppingLists: StateFlow<List<ShoppingListEntity>> =
        container.shoppingListRepository.shoppingLists
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingredientsFor(recipeId: String): Flow<List<RecipeIngredientEntity>> =
        container.database.recipeIngredientDao().observeIngredients(recipeId)

    fun stepsFor(recipeId: String): Flow<List<RecipeStepEntity>> =
        container.database.recipeStepDao().observeSteps(recipeId)

    fun itemsFor(listId: String): Flow<List<ShoppingListItemEntity>> =
        container.shoppingListRepository.itemsFor(listId)

    fun isFavorite(recipeId: String): Flow<Boolean> =
        container.favoriteRepository.isFavorite(recipeId)

    fun toggleFavorite(recipeId: String) {
        viewModelScope.launch {
            runCatching { container.favoriteRepository.toggle(recipeId) }
        }
    }

    fun checkItem(item: ShoppingListItemEntity, checked: Boolean) {
        viewModelScope.launch {
            runCatching { container.shoppingListRepository.checkItem(item, checked) }
        }
    }

    val notes: StateFlow<List<FamilyNoteEntity>> = container.familyNoteRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedTheme: StateFlow<AppTheme> = container.themePreference.selectedTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppTheme.BOSQUE)

    val themeMode: StateFlow<ThemeMode> = container.themePreference.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { container.themePreference.setTheme(theme) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.themePreference.setThemeMode(mode) }
    }

    fun createRecipe(
        title: String, description: String?, servings: Int?,
        prepMinutes: Int?, cookMinutes: Int?, difficulty: String?,
        ingredients: List<RecipeIngredientItemDto>, steps: List<RecipeStepItemDto>,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                container.recipeRepository.create(title, description, servings, prepMinutes, cookMinutes, difficulty, ingredients, steps)
            }.onSuccess { _userMessage.emit("Receta creada") }
             .onFailure { onError(it.message ?: "Error al crear receta") }
        }
    }

    fun updateRecipe(
        recipe: RecipeEntity, title: String, description: String?, servings: Int?,
        prepMinutes: Int?, cookMinutes: Int?, difficulty: String?,
        ingredients: List<RecipeIngredientItemDto>, steps: List<RecipeStepItemDto>,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                container.recipeRepository.update(recipe, title, description, servings, prepMinutes, cookMinutes, difficulty, ingredients, steps)
            }.onSuccess { _userMessage.emit("Receta actualizada") }
             .onFailure { onError(it.message ?: "Error al actualizar receta") }
        }
    }

    fun deleteRecipe(recipe: RecipeEntity) {
        viewModelScope.launch {
            runCatching { container.recipeRepository.delete(recipe) }
                .onSuccess { _userMessage.emit("Receta eliminada") }
        }
    }

    fun createStockItem(
        name: String, quantity: Double?, unit: String?,
        lowStockThreshold: Double?, expiresAt: String?, note: String?,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                container.stockRepository.create(name, quantity, unit, lowStockThreshold, expiresAt, note)
            }.onSuccess { _userMessage.emit("Artículo añadido al stock") }
             .onFailure { onError(it.message ?: "Error al crear item de stock") }
        }
    }

    fun updateStockItem(
        item: StockItemEntity, name: String, quantity: Double?, unit: String?,
        lowStockThreshold: Double?, expiresAt: String?, note: String?,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                container.stockRepository.update(item, name, quantity, unit, lowStockThreshold, expiresAt, note)
            }.onSuccess { _userMessage.emit("Artículo de stock actualizado") }
             .onFailure { onError(it.message ?: "Error al actualizar item de stock") }
        }
    }

    fun deleteStockItem(item: StockItemEntity) {
        viewModelScope.launch {
            runCatching { container.stockRepository.delete(item) }
                .onSuccess { _userMessage.emit("Artículo de stock eliminado") }
        }
    }

    fun createNote(title: String, body: String, pinned: Boolean, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { container.familyNoteRepository.create(title, body, pinned) }
                .onSuccess { _userMessage.emit("Nota creada") }
                .onFailure { onError(it.message ?: "Error al crear nota") }
        }
    }

    fun updateNote(note: FamilyNoteEntity, title: String, body: String, pinned: Boolean, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { container.familyNoteRepository.update(note, title, body, pinned) }
                .onSuccess { _userMessage.emit("Nota actualizada") }
                .onFailure { onError(it.message ?: "Error al actualizar nota") }
        }
    }

    fun deleteNote(note: FamilyNoteEntity) {
        viewModelScope.launch {
            runCatching { container.familyNoteRepository.delete(note) }
                .onSuccess { _userMessage.emit("Nota eliminada") }
        }
    }

    fun photosFor(recipeId: String): Flow<List<RecipePhotoEntity>> =
        container.recipePhotoRepository.photosFor(recipeId)

    fun loadPhotos(recipeId: String) {
        viewModelScope.launch {
            runCatching { container.recipePhotoRepository.loadPhotos(recipeId) }
        }
    }

    fun launchUploadPhoto(context: Context, recipeId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (bytes, contentType) = compressImage(context, uri)
                container.recipePhotoRepository.upload(recipeId, bytes, contentType, null)
            }.onSuccess { _userMessage.emit("Foto añadida") }
             .onFailure { _userMessage.emit("Error al subir la foto") }
        }
    }

    // ── Ratings ────────────────────────────────────────────────────────────────

    private val _recipeRatings = MutableStateFlow<List<RecipeRatingDto>>(emptyList())
    val recipeRatings: StateFlow<List<RecipeRatingDto>> = _recipeRatings.asStateFlow()

    fun loadRatings(recipeId: String) {
        viewModelScope.launch {
            runCatching { container.recipeRatingRepository.loadRatings(recipeId) }
                .onSuccess { _recipeRatings.value = it }
        }
    }

    fun submitRating(recipeId: String, stars: Int, comment: String?, existingRatingId: String?,
                     onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                if (existingRatingId == null) {
                    container.recipeRatingRepository.create(recipeId, stars, comment)
                } else {
                    container.recipeRatingRepository.update(recipeId, existingRatingId, stars, comment)
                }
            }.onSuccess { updated ->
                val current = _recipeRatings.value.toMutableList()
                val idx = current.indexOfFirst { it.id == updated.id }
                if (idx >= 0) current[idx] = updated else current.add(0, updated)
                _recipeRatings.value = current
                _userMessage.emit("Valoración guardada")
            }.onFailure { onError(it.message ?: "Error al guardar valoración") }
        }
    }

    fun deleteRating(recipeId: String, ratingId: String) {
        viewModelScope.launch {
            runCatching { container.recipeRatingRepository.delete(recipeId, ratingId) }
                .onSuccess {
                    _recipeRatings.value = _recipeRatings.value.filter { it.id != ratingId }
                    _userMessage.emit("Valoración eliminada")
                }
        }
    }

    fun assignToMenu(recipeId: String, plannedDate: String, mealType: String) {
        viewModelScope.launch {
            runCatching { container.menuItemRepository.assign(recipeId, plannedDate, mealType) }
                .onSuccess { _userMessage.emit("Comida añadida al menú") }
                .onFailure { _userMessage.emit("Error al añadir al menú") }
        }
    }

    fun removeFromMenu(item: org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity) {
        viewModelScope.launch {
            runCatching { container.menuItemRepository.remove(item) }
                .onSuccess { _userMessage.emit("Comida eliminada del menú") }
        }
    }

    fun deletePhoto(photo: RecipePhotoEntity) {
        viewModelScope.launch {
            runCatching { container.recipePhotoRepository.delete(photo) }
                .onSuccess { _userMessage.emit("Foto eliminada") }
        }
    }

    private fun compressImage(context: Context, uri: Uri): Pair<ByteArray, String> {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val maxDim = 1080
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
            )
        } else bitmap
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Pair(baos.toByteArray(), "image/jpeg")
    }
}

class RecetasViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecetasViewModel(container) as T
}
