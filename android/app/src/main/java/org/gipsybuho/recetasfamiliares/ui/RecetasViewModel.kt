@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.data.remote.ChatSocket
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto
import org.gipsybuho.recetasfamiliares.data.repository.CHAT_MAX_BODY_LENGTH
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityPingDto
import org.gipsybuho.recetasfamiliares.data.repository.PRIVATE_CHAT_MAX_BODY_LENGTH
import org.gipsybuho.recetasfamiliares.ui.theme.AppTheme
import org.gipsybuho.recetasfamiliares.ui.theme.ThemeMode
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.Request
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyStatsDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeRatingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserRecipeRankingDto
import org.gipsybuho.recetasfamiliares.core.ServerUrlConfig
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyMemberDto
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.sync.ExpiryNotificationWorker
import org.gipsybuho.recetasfamiliares.sync.SyncWorker
import java.util.concurrent.TimeUnit

private const val CHAT_POLL_MS = 15_000L

class RecetasViewModel(private val container: AppContainer) : ViewModel() {

    val recipes: StateFlow<List<RecipeEntity>> = container.recipeRepository.recipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recipeCovers: StateFlow<Map<String, String>> = container.sessionStore.familyIdFlow
        .flatMapLatest { familyId ->
            if (familyId == null) flowOf(emptyList())
            else container.database.recipePhotoDao().observeCovers(familyId)
        }
        .map { photos ->
            // La consulta llega ordenada por position ascendente: la primera foto de cada
            // receta es su portada. associateBy conservaria la ultima, asi que se pliega
            // a mano quedandose con la primera.
            buildMap {
                photos.forEach { photo ->
                    if (!containsKey(photo.recipeId)) {
                        preferredCoverUrl(photo.thumbnailUrl, photo.url)?.let { put(photo.recipeId, it) }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val stockItems: StateFlow<List<StockItemEntity>> = container.stockRepository.stockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoggedIn = MutableStateFlow(container.authRepository.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // Si el authenticator limpia la sesion (refresh token revocado o
        // cuenta eliminada), volver a login en vez de quedarse en una UI
        // logueada mostrando datos vacios.
        viewModelScope.launch {
            container.sessionStore.familyIdFlow.collect { id ->
                if (id == null && _isLoggedIn.value && !container.authRepository.isLoggedIn) {
                    _isLoggedIn.value = false
                }
            }
        }
    }

    private val _onboardingDone = MutableStateFlow(container.onboardingPreference.onboardingDone)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    val myUserId: String? get() = container.sessionStore.userId
    val isAdmin: StateFlow<Boolean> = container.sessionStore.familyRoleFlow
        .map { it == "ADMIN" || it == "OWNER" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val activeFamilyId: StateFlow<String?> = container.sessionStore.familyIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.sessionStore.familyId)

    private val _displayName = MutableStateFlow(container.sessionStore.displayName)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(container.sessionStore.email)
    val email: StateFlow<String?> = _email.asStateFlow()

    private val _avatarUrl = MutableStateFlow(container.sessionStore.avatarUrl)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _serverBaseUrl = MutableStateFlow(container.serverUrlStore.baseUrl)
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()

    private val _familyStats = MutableStateFlow<FamilyStatsDto?>(null)
    val familyStats: StateFlow<FamilyStatsDto?> = _familyStats.asStateFlow()

    private val _filterByStock = MutableStateFlow(false)
    val filterByStock: StateFlow<Boolean> = _filterByStock.asStateFlow()

    val filteredRecipes: StateFlow<List<RecipeEntity>> = combine(
        container.recipeRepository.recipes,
        stockItems,
        container.sessionStore.familyIdFlow.flatMapLatest { familyId ->
            if (familyId.isNullOrBlank()) flowOf(emptyList())
            else container.database.recipeIngredientDao().observeAllIngredients(familyId)
        },
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
                // Un wipe de logout aun en vuelo debe terminar antes de cargar
                // la sesion nueva: si no, podria borrar datos recien sincronizados.
                wipeJob?.join()
                container.authRepository.login(email, password)
                _isLoggedIn.value = true
                refresh()
            }.onFailure { onError(it.message ?: "No se pudo iniciar sesion") }
        }
    }

    fun logout() {
        stopChatBadge()
        container.authRepository.logout()
        wipeLocalCaches()
        _isLoggedIn.value = false
        _displayName.value = null
        _email.value = null
        _avatarUrl.value = null
        _families.value = emptyList()
        clearFamilyScopedState()
        _emailVerified.value = null
    }

    private var wipeJob: Job? = null

    /** Privacidad en dispositivos compartidos: al cerrar sesion no queda
     *  contenido familiar en Room. Los datos maestros viven en el servidor;
     *  los cambios offline aun no sincronizados se pierden. Si el vaciado
     *  falla o no llega a correr, pendingWipe fuerza el reintento en el
     *  siguiente login (fail-closed). */
    private fun wipeLocalCaches() {
        container.sessionStore.pendingWipe = true
        wipeJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.database.clearAllTables() }
                .onSuccess { container.sessionStore.pendingWipe = false }
        }
    }

    // ── Ciclo de vida de cuenta (CRIT-2) ────────────────────────────────────

    private val _emailVerified = MutableStateFlow<Boolean?>(null)
    val emailVerified: StateFlow<Boolean?> = _emailVerified.asStateFlow()

    /** Consulta /users/me para el estado de verificacion; offline deja null. */
    fun loadAccountStatus() {
        viewModelScope.launch {
            runCatching { container.userRepository.me() }
                .onSuccess { _emailVerified.value = it.emailVerified }
        }
    }

    fun requestPasswordReset(email: String, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.authRepository.requestPasswordReset(email)
            }.onSuccess { onDone() }
                .onFailure {
                    onError(
                        if ((it as? retrofit2.HttpException)?.code() == 429)
                            "Demasiados intentos. Espera un momento y vuelve a probar."
                        else "No se pudo enviar el correo de recuperación"
                    )
                }
        }
    }

    fun confirmPasswordReset(token: String, newPassword: String, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.authRepository.confirmPasswordReset(token, newPassword)
            }.onSuccess { onDone() }
                .onFailure { onError(accountErrorMessage(it, "No se pudo cambiar la contraseña")) }
        }
    }

    fun requestEmailVerification() {
        val email = container.sessionStore.email ?: return
        viewModelScope.launch {
            runCatching {
                container.authRepository.requestEmailVerification(email)
            }.onSuccess { _userMessage.emit("Correo de verificación enviado. Revisa tu bandeja.") }
                .onFailure { _userMessage.emit("No se pudo enviar el correo de verificación") }
        }
    }

    fun confirmEmailVerification(token: String) {
        viewModelScope.launch {
            runCatching {
                container.authRepository.confirmEmailVerification(token)
            }.onSuccess {
                _emailVerified.value = true
                _userMessage.emit("Correo verificado correctamente")
            }.onFailure { _userMessage.emit("El código no es válido o ha caducado") }
        }
    }

    fun deleteAccount(password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                closeChat()
                container.authRepository.deleteAccount(password)
            }.onSuccess {
                logout()
            }.onFailure {
                val message = when ((it as? retrofit2.HttpException)?.code()) {
                    403 -> "Contraseña incorrecta"
                    429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
                    else -> "No se pudo eliminar la cuenta"
                }
                onError(message)
            }
        }
    }

    private fun accountErrorMessage(error: Throwable, fallback: String): String =
        when ((error as? retrofit2.HttpException)?.code()) {
            429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
            400 -> "El código no es válido o ha caducado"
            else -> fallback
        }

    fun saveServerBaseUrl(rawBaseUrl: String, onError: (String) -> Unit): Boolean {
        val normalized = try {
            ServerUrlConfig.normalizeAndValidate(rawBaseUrl)
        } catch (ex: IllegalArgumentException) {
            onError(ex.message ?: "URL de servidor no valida")
            return false
        }
        val before = container.serverUrlStore.baseUrl
        container.serverUrlStore.baseUrl = normalized
        _serverBaseUrl.value = normalized
        if (before != normalized && _isLoggedIn.value) {
            closeChat()
            stopChatBadge()
            container.sessionStore.clear()
            wipeLocalCaches()
            _isLoggedIn.value = false
            _displayName.value = null
            _email.value = null
            _avatarUrl.value = null
            _families.value = emptyList()
            clearFamilyScopedState()
        }
        return true
    }

    fun resetServerBaseUrl() {
        val before = container.serverUrlStore.baseUrl
        container.serverUrlStore.reset()
        val after = container.serverUrlStore.baseUrl
        _serverBaseUrl.value = after
        if (before != after && _isLoggedIn.value) {
            closeChat()
            stopChatBadge()
            container.sessionStore.clear()
            wipeLocalCaches()
            _isLoggedIn.value = false
            _displayName.value = null
            _email.value = null
            _avatarUrl.value = null
            _families.value = emptyList()
            clearFamilyScopedState()
        }
    }

    private fun clearFamilyScopedState() {
        _familyStats.value = null
        _familyMembers.value = emptyList()
        _onlineUserIds.value = emptySet()
        _userRecipeRankings.value = emptyList()
        _familyInfo.value = null
        _recipeRatings.value = emptyList()
        _chatMessages.value = emptyList()
        _chatHasMoreOlder.value = false
        chatOldestCursor = null
        _chatUnread.value = 0
        _recipeNextPage.value = 1
        _recipeHasMore.value = false
    }

    private val _familyMembers = MutableStateFlow<List<FamilyMemberDto>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMemberDto>> = _familyMembers.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    private val _userRecipeRankings = MutableStateFlow<List<UserRecipeRankingDto>>(emptyList())
    val userRecipeRankings: StateFlow<List<UserRecipeRankingDto>> = _userRecipeRankings.asStateFlow()

    private val _families = MutableStateFlow<List<FamilyDto>>(emptyList())
    val families: StateFlow<List<FamilyDto>> = _families.asStateFlow()

    private val _familyInfo = MutableStateFlow<FamilyDto?>(null)
    val familyInfo: StateFlow<FamilyDto?> = _familyInfo.asStateFlow()

    /** Nombre e imagen del grupo familiar activo. */
    fun loadFamilyInfo() {
        viewModelScope.launch {
            refreshFamiliesFromServer()
        }
    }

    private suspend fun refreshFamiliesFromServer(): List<FamilyDto> {
        return runCatching { container.familyMemberRepository.families() }
            .onSuccess { families ->
                _families.value = families
                val currentFamilyId = container.sessionStore.familyId
                val active = families.firstOrNull { it.id == currentFamilyId }
                    ?: families.firstOrNull()
                if (active != null) {
                    val familyChanged = active.id != currentFamilyId
                    if (active.id != currentFamilyId || active.role != container.sessionStore.familyRole) {
                        container.familyMemberRepository.setActiveFamily(active)
                    }
                    if (familyChanged) {
                        stopChatBadge()
                        clearFamilyScopedState()
                        startChatBadge()
                    }
                    _familyInfo.value = active
                } else {
                    stopChatBadge()
                    clearFamilyScopedState()
                    _familyInfo.value = null
                }
            }
            .getOrElse { _families.value }
    }

    fun switchActiveFamily(familyId: String) {
        if (familyId == container.sessionStore.familyId) return
        viewModelScope.launch {
            val families = _families.value.ifEmpty { refreshFamiliesFromServer() }
            val target = families.firstOrNull { it.id == familyId }
            if (target == null) {
                _userMessage.emit("No se pudo cambiar de familia")
                return@launch
            }
            closeChat()
            stopChatBadge()
            container.familyMemberRepository.setActiveFamily(target)
            clearFamilyScopedState()
            _familyInfo.value = target
            refresh()
            loadFamilyStats()
            loadFamilyActivity()
            loadFamilyMembers()
            loadUserRecipeRankings()
            startChatBadge()
            _userMessage.emit("Familia activa: ${target.name}")
        }
    }

    fun createFamily(name: String) {
        viewModelScope.launch {
            runCatching { container.familyMemberRepository.createFamily(name) }
                .onSuccess { created ->
                    refreshFamiliesFromServer()
                    _userMessage.emit("Familia creada: ${created.name}")
                }
                .onFailure { _userMessage.emit("No se pudo crear la familia") }
        }
    }

    fun copyRecipeToFamily(recipeId: String, targetFamilyId: String) {
        viewModelScope.launch {
            runCatching { container.recipeRepository.copyToFamily(recipeId, targetFamilyId) }
                .onSuccess {
                    val targetName = _families.value.firstOrNull { it.id == targetFamilyId }?.name
                    _userMessage.emit(
                        if (targetName != null) "Receta y fotos copiadas a $targetName"
                        else "Receta copiada"
                    )
                }
                .onFailure { _userMessage.emit("No se pudo copiar la receta") }
        }
    }

    fun uploadFamilyAvatar(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.familyMemberRepository.uploadFamilyAvatar(context, uri)
            }.onSuccess {
                _familyInfo.value = it
                _userMessage.emit("Imagen del grupo actualizada")
            }.onFailure {
                _userMessage.emit("No se pudo actualizar la imagen del grupo")
            }
        }
    }

    /** Lista de miembros de la familia; offline mantiene la ultima carga en memoria. */
    fun loadFamilyMembers() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.members() }
                .onSuccess {
                    // Descarta respuestas tardias si el usuario ya cambio de familia.
                    if (familyId == container.sessionStore.familyId) _familyMembers.value = it
                }
        }
    }

    /** Snapshot inicial de presencia; las actualizaciones en vivo llegan por WebSocket. */
    fun loadPresence() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.presence() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) {
                        _onlineUserIds.value = it.onlineUserIds.toSet()
                    }
                }
        }
    }

    fun updateMemberRole(userId: String, newRole: String) {
        if (newRole != "MEMBER" && newRole != "ADMIN") return
        viewModelScope.launch {
            runCatching { container.familyMemberRepository.updateRole(userId, newRole) }
                .onSuccess { updated ->
                    _familyMembers.update { members ->
                        members.map { member -> if (member.userId == updated.userId) updated else member }
                    }
                    _userMessage.emit("Rol actualizado")
                }
                .onFailure { _userMessage.emit("No se pudo cambiar el rol") }
        }
    }

    fun loadUserRecipeRankings() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.userRecipeRankings() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) _userRecipeRankings.value = it
                }
        }
    }

    fun updateMember(
        userId: String,
        displayName: String,
        email: String,
        passwordAction: String?,
        temporaryPassword: String?
    ) {
        viewModelScope.launch {
            runCatching {
                container.familyMemberRepository.updateMember(
                    userId = userId,
                    displayName = displayName,
                    email = email,
                    passwordAction = passwordAction,
                    temporaryPassword = temporaryPassword
                )
            }.onSuccess { updated ->
                _familyMembers.update { members ->
                    members.map { member -> if (member.userId == updated.userId) updated else member }
                }
                _userMessage.emit("Miembro actualizado")
            }.onFailure {
                _userMessage.emit("No se pudo actualizar el miembro")
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            runCatching { container.familyMemberRepository.remove(userId) }
                .onSuccess {
                    _familyMembers.update { members -> members.filterNot { it.userId == userId } }
                    loadFamilyStats()
                    loadFamilyActivity()
                    _userMessage.emit("Miembro expulsado")
                }
                .onFailure { _userMessage.emit("No se pudo expulsar al miembro") }
        }
    }

    /** Carga las stats del servidor; si falla (offline) se mantiene el fallback local. */
    fun loadFamilyStats() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.stats() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) _familyStats.value = it
                }
        }
    }

    fun markOnboardingDone() {
        container.onboardingPreference.onboardingDone = true
        _onboardingDone.value = true
    }

    fun toggleStockFilter() { _filterByStock.value = !_filterByStock.value }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            runCatching {
                val response = container.userRepository.updateDisplayName(newName)
                _displayName.value = response.displayName
                _userMessage.emit("Nombre actualizado")
            }.onFailure {
                _userMessage.emit("Error al actualizar el nombre")
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val response = container.userRepository.uploadAvatar(uri, context)
                _avatarUrl.value = response.avatarUrl
                _userMessage.emit("Foto de perfil actualizada")
            }.onFailure {
                _userMessage.emit("Error al subir la foto")
            }
        }
    }

    fun inviteMember(email: String, role: String) {
        viewModelScope.launch {
            try {
                container.familyMemberRepository.invite(email, role)
                _userMessage.emit("Miembro invitado correctamente")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _userMessage.emit("No se pudo invitar: usuario no encontrado o sin permisos")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                container.recipeRepository.refresh()
                container.stockRepository.refresh()
                runCatching { container.syncRepository.pullOnce() }
                _recipeNextPage.value = 1
                _recipeHasMore.value = container.recipeRepository.totalPages > 1
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Sesion invalidada (p.ej. cuenta borrada o refresh token revocado):
                // el authenticator ya limpio la sesion; volver a login sin crashear.
                if (!container.authRepository.isLoggedIn) _isLoggedIn.value = false
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
        container.sessionStore.familyIdFlow.flatMapLatest { familyId ->
            if (familyId.isNullOrBlank()) flowOf(emptyList())
            else container.database.menuItemDao().observeMenuItems(familyId)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shoppingLists: StateFlow<List<ShoppingListEntity>> =
        container.shoppingListRepository.shoppingLists
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingredientsFor(recipeId: String): Flow<List<RecipeIngredientEntity>> =
        container.sessionStore.familyIdFlow.flatMapLatest { familyId ->
            if (familyId.isNullOrBlank()) flowOf(emptyList())
            else container.database.recipeIngredientDao().observeIngredients(recipeId, familyId)
        }

    fun stepsFor(recipeId: String): Flow<List<RecipeStepEntity>> =
        container.sessionStore.familyIdFlow.flatMapLatest { familyId ->
            if (familyId.isNullOrBlank()) flowOf(emptyList())
            else container.database.recipeStepDao().observeSteps(recipeId, familyId)
        }

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

    val hapticsEnabled: StateFlow<Boolean> = container.themePreference.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.themePreference.setHapticsEnabled(enabled) }
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
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.recipeRatingRepository.loadRatings(recipeId) }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) _recipeRatings.value = it
                }
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
                loadUserRecipeRankings()
                _userMessage.emit("Valoración guardada")
            }.onFailure { onError(it.message ?: "Error al guardar valoración") }
        }
    }

    fun deleteRating(recipeId: String, ratingId: String) {
        viewModelScope.launch {
            runCatching { container.recipeRatingRepository.delete(recipeId, ratingId) }
                .onSuccess {
                    _recipeRatings.value = _recipeRatings.value.filter { it.id != ratingId }
                    loadUserRecipeRankings()
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

    // ── Chat familiar (fase 1) ───────────────────────────────────────────────

    private val _chatMessages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageDto>> = _chatMessages.asStateFlow()

    private val _chatConnected = MutableStateFlow(false)
    val chatConnected: StateFlow<Boolean> = _chatConnected.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _chatHasMoreOlder = MutableStateFlow(false)
    val chatHasMoreOlder: StateFlow<Boolean> = _chatHasMoreOlder.asStateFlow()

    private var chatOldestCursor: String? = null
    private var chatSocket: ChatSocket? = null
    private var chatPollingJob: Job? = null

    // ── Aviso de mensajes no leidos (badge del icono de chat) ────────────────

    private val _chatUnread = MutableStateFlow(0)
    val chatUnread: StateFlow<Int> = _chatUnread.asStateFlow()

    private var chatBadgeSocket: ChatSocket? = null
    private var chatScreenOpen = false
    // Ids ya contados: evita que ediciones/borrados (que reutilizan id) o
    // duplicados de reconexion inflen el contador.
    private val chatBadgeSeenIds = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            if (size > 500) iterator().let { it.next(); it.remove() }
            return added
        }
    }

    // ── Chat privado 1:1 ─────────────────────────────────────────────────────

    private val _conversations = MutableStateFlow<List<PrivateConversationDto>>(emptyList())
    val conversations: StateFlow<List<PrivateConversationDto>> = _conversations.asStateFlow()

    private val _privateChatUnread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val privateChatUnread: StateFlow<Map<String, Int>> = _privateChatUnread.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<PrivateMessageDto>>(emptyList())
    val privateMessages: StateFlow<List<PrivateMessageDto>> = _privateMessages.asStateFlow()

    private val _privateChatConnected = MutableStateFlow(false)
    val privateChatConnected: StateFlow<Boolean> = _privateChatConnected.asStateFlow()

    private val _privateChatLoading = MutableStateFlow(false)
    val privateChatLoading: StateFlow<Boolean> = _privateChatLoading.asStateFlow()

    private val _privateChatHasMoreOlder = MutableStateFlow(false)
    val privateChatHasMoreOlder: StateFlow<Boolean> = _privateChatHasMoreOlder.asStateFlow()

    private var privateChatOldestCursor: String? = null
    private var privateChatSocket: ChatSocket? = null

    /** Escrito en el hilo principal (efectos de Compose), leido tambien desde el hilo
     * lector de OkHttp (ChatSocket.onMessage) al procesar un ping de inbox. */
    @Volatile
    private var activePrivateConversationId: String? = null

    // ── Avisos de actividad familiar ─────────────────────────────────────────

    private val _sectionsWithUnseenActivity = MutableStateFlow<Set<String>>(emptySet())
    val sectionsWithUnseenActivity: StateFlow<Set<String>> = _sectionsWithUnseenActivity.asStateFlow()

    /** Fetch inicial del estado de avisos; se llama junto a loadFamilyStats(). */
    fun loadFamilyActivity() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.familyActivity() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) applyActivitySnapshot(it)
                }
        }
    }

    private fun applyActivitySnapshot(activity: FamilyActivityDto) {
        val unseen = buildSet {
            if (activity.recipe) add("RECIPE")
            if (activity.note) add("NOTE")
            if (activity.stock) add("STOCK")
        }
        _sectionsWithUnseenActivity.value = unseen
    }

    private fun handleActivityPing(ping: FamilyActivityPingDto) {
        _sectionsWithUnseenActivity.update { it + ping.section }
    }

    /** Marca una seccion como vista al navegar a su tab: limpia el aviso local y avisa al servidor. */
    fun markSectionSeen(section: String) {
        if (!_sectionsWithUnseenActivity.value.contains(section)) return
        _sectionsWithUnseenActivity.update { it - section }
        viewModelScope.launch {
            runCatching { container.familyMemberRepository.markSectionSeen(section) }
        }
    }

    /**
     * Conexion en tiempo real ligera, viva mientras hay sesion, solo para
     * contar mensajes nuevos de otros miembros con el chat cerrado.
     */
    fun startChatBadge() {
        if (chatBadgeSocket != null || !_isLoggedIn.value) return
        chatBadgeSocket = container.chatRepository.openRealtime(
            onMessage = { msg ->
                // Registrar el id SIEMPRE (tambien con el chat abierto): una
                // edicion posterior del mismo mensaje no debe contar como nuevo.
                val firstTime = chatBadgeSeenIds.add(msg.id)
                val fromOther = msg.authorUserId != null && msg.authorUserId != myUserId
                if (firstTime && !chatScreenOpen && fromOther && !msg.deleted) {
                    _chatUnread.update { it + 1 }
                }
            },
            onConnectionChange = {},
            onPresenceUpdate = { online -> _onlineUserIds.value = online },
            onInboxPing = { ping -> handlePrivateInboxPing(ping) },
            onActivityPing = { ping -> handleActivityPing(ping) }
        )
    }

    private fun handlePrivateInboxPing(ping: PrivateInboxPingDto) {
        if (ping.conversationId == activePrivateConversationId) return
        _privateChatUnread.update { current ->
            current + (ping.conversationId to ((current[ping.conversationId] ?: 0) + 1))
        }
    }

    fun stopChatBadge() {
        chatBadgeSocket?.disconnect()
        chatBadgeSocket = null
        chatBadgeSeenIds.clear()
        _chatUnread.value = 0
        _privateChatUnread.value = emptyMap()
        _conversations.value = emptyList()
        _sectionsWithUnseenActivity.value = emptySet()
    }

    /** Bandeja de conversaciones privadas del usuario en la familia activa. */
    fun loadConversations() {
        viewModelScope.launch {
            runCatching { container.privateChatRepository.listConversations() }
                .onSuccess { _conversations.value = it }
                .onFailure { _userMessage.emit("No se pudieron cargar las conversaciones") }
        }
    }

    /** Crea o recupera la conversacion con otro miembro. Llamado desde el boton Mensaje en Miembros. */
    fun createOrGetConversation(otherUserId: String, onResult: (PrivateConversationDto) -> Unit) {
        viewModelScope.launch {
            runCatching { container.privateChatRepository.createOrGetConversation(otherUserId) }
                .onSuccess { conversation ->
                    loadConversations()
                    onResult(conversation)
                }
                .onFailure { _userMessage.emit("No se pudo abrir la conversacion") }
        }
    }

    /** Marca una conversacion como leida (limpia su contador) al abrirla, sin conectar el socket todavia. */
    fun markConversationRead(conversationId: String) {
        _privateChatUnread.update { it - conversationId }
    }

    /** Abre una conversacion: conexion propia y efimera (a diferencia del chat familiar, sin polling de respaldo). */
    fun openPrivateChat(conversationId: String) {
        activePrivateConversationId = conversationId
        markConversationRead(conversationId)
        _privateMessages.value = emptyList()
        _privateChatLoading.value = true
        viewModelScope.launch {
            runCatching { container.privateChatRepository.loadHistory(conversationId) }
                .onSuccess { history ->
                    _privateMessages.value = history.items.sortedBy { it.createdAt }
                    _privateChatHasMoreOlder.value = history.hasMore
                    privateChatOldestCursor = history.nextBefore
                }
                .onFailure { _userMessage.emit("No se pudo cargar la conversacion") }
            _privateChatLoading.value = false
        }
        privateChatSocket = container.chatRepository.openRealtime(
            onMessage = {},
            onConnectionChange = { connected -> _privateChatConnected.value = connected },
            onPresenceUpdate = {},
            conversationId = conversationId,
            onPrivateMessage = { msg -> _privateMessages.update { mergePrivateMessages(it, listOf(msg)) } }
        )
    }

    fun closePrivateChat() {
        activePrivateConversationId = null
        privateChatSocket?.disconnect()
        privateChatSocket = null
        _privateChatConnected.value = false
    }

    fun loadOlderPrivateChat() {
        val conversationId = activePrivateConversationId ?: return
        if (_privateChatLoading.value) return
        if (!_privateChatHasMoreOlder.value) return
        val before = privateChatOldestCursor ?: return
        _privateChatLoading.value = true
        viewModelScope.launch {
            runCatching { container.privateChatRepository.loadHistory(conversationId, before) }
                .onSuccess { history ->
                    _privateMessages.update { mergePrivateMessages(it, history.items) }
                    _privateChatHasMoreOlder.value = history.hasMore
                    privateChatOldestCursor = history.nextBefore
                }
                .onFailure { _userMessage.emit("No se pudieron cargar mensajes anteriores") }
            _privateChatLoading.value = false
        }
    }

    fun sendPrivateMessage(body: String, onSent: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        if (!_privateChatConnected.value) {
            viewModelScope.launch { _userMessage.emit("Sin conexión en tiempo real") }
            return
        }
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.privateChatRepository.send(conversationId, text) }
                .onSuccess { msg ->
                    _privateMessages.update { mergePrivateMessages(it, listOf(msg)) }
                    onSent()
                }
                .onFailure { _userMessage.emit("No se pudo enviar el mensaje") }
        }
    }

    fun sendPrivateImage(context: Context, uri: Uri, caption: String, onSent: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = caption.trim()
        if (!_privateChatConnected.value) {
            viewModelScope.launch { _userMessage.emit("Sin conexión en tiempo real") }
            return
        }
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val image = compressImage(context, uri)
                    container.privateChatRepository.sendImages(conversationId, text, listOf(image))
                }
            }.onSuccess { msg ->
                _privateMessages.update { mergePrivateMessages(it, listOf(msg)) }
                onSent()
            }.onFailure {
                _userMessage.emit("No se pudo enviar la imagen")
            }
        }
    }

    fun editPrivateMessage(message: PrivateMessageDto, body: String, onDone: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = body.trim()
        if (message.deleted || message.authorUserId != myUserId) return
        if (text.isEmpty()) return
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.privateChatRepository.edit(conversationId, message.id, text) }
                .onSuccess { updated ->
                    _privateMessages.update { mergePrivateMessages(it, listOf(updated)) }
                    _userMessage.emit("Mensaje editado")
                    onDone()
                }
                .onFailure { _userMessage.emit("No se pudo editar el mensaje") }
        }
    }

    fun deletePrivateMessage(message: PrivateMessageDto) {
        val conversationId = activePrivateConversationId ?: return
        if (message.authorUserId != myUserId || message.deleted) return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.delete(conversationId, message.id) }
                .onSuccess { updated ->
                    _privateMessages.update { mergePrivateMessages(it, listOf(updated)) }
                    _userMessage.emit("Mensaje eliminado")
                }
                .onFailure { _userMessage.emit("No se pudo eliminar el mensaje") }
        }
    }

    fun clearPrivateChat() {
        val conversationId = activePrivateConversationId ?: return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.clear(conversationId) }
                .onSuccess {
                    _privateMessages.value = emptyList()
                    _privateChatHasMoreOlder.value = false
                    privateChatOldestCursor = null
                    _userMessage.emit("Conversación borrada para ti")
                }
                .onFailure { _userMessage.emit("No se pudo borrar la conversacion") }
        }
    }

    fun exportPrivateChat(onExported: (String) -> Unit) {
        val conversationId = activePrivateConversationId ?: return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.export(conversationId) }
                .onSuccess { export -> onExported(buildPrivateChatExportText(export)) }
                .onFailure { _userMessage.emit("No se pudo exportar la conversacion") }
        }
    }

    /** Mismo criterio que mergeChat, para mensajes de una conversacion privada. */
    private fun mergePrivateMessages(
        existing: List<PrivateMessageDto>,
        incoming: List<PrivateMessageDto>
    ): List<PrivateMessageDto> {
        val byId = LinkedHashMap<String, PrivateMessageDto>(existing.size + incoming.size)
        existing.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.createdAt }
    }

    private fun buildPrivateChatExportText(export: org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto): String {
        val builder = StringBuilder("Conversacion privada - export\n\n")
        export.messages.forEach { message ->
            val attachments = message.attachments.orEmpty()
            val body = message.body ?: if (attachments.isEmpty()) "(mensaje eliminado)" else ""
            builder.append("[").append(message.createdAt).append("] ")
                .append(message.authorDisplayName).append(": ").append(body)
            if (attachments.isNotEmpty()) {
                if (body.isNotBlank()) builder.append(' ')
                builder.append('[').append(attachments.size)
                    .append(if (attachments.size == 1) " imagen]" else " imagenes]")
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    /** Abre el chat: carga la pagina reciente, conecta en tiempo real y arranca el polling de respaldo. */
    fun openChat() {
        chatScreenOpen = true
        _chatUnread.value = 0
        _chatLoading.value = true
        viewModelScope.launch {
            runCatching { container.chatRepository.loadHistory() }
                .onSuccess { history ->
                    _chatMessages.value = history.items.sortedBy { it.createdAt }
                    _chatHasMoreOlder.value = history.hasMore
                    chatOldestCursor = history.nextBefore
                }
                .onFailure { _userMessage.emit("No se pudo cargar el chat") }
            _chatLoading.value = false
        }
        chatSocket = container.chatRepository.openRealtime(
            onMessage = { msg -> _chatMessages.update { mergeChat(it, listOf(msg)) } },
            onConnectionChange = { connected -> _chatConnected.value = connected },
            onPresenceUpdate = { online -> _onlineUserIds.value = online }
        )
        startChatPolling()
    }

    fun closeChat() {
        chatScreenOpen = false
        chatSocket?.disconnect()
        chatSocket = null
        chatPollingJob?.cancel()
        chatPollingJob = null
        _chatConnected.value = false
    }

    fun sendChat(body: String, onSent: () -> Unit = {}) {
        val text = body.trim()
        if (text.isEmpty()) return
        if (!_chatConnected.value) {
            viewModelScope.launch { _userMessage.emit("Sin conexión en tiempo real") }
            return
        }
        if (text.length > CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.chatRepository.send(text) }
                .onSuccess { msg ->
                    _chatMessages.update { mergeChat(it, listOf(msg)) }
                    onSent()
                }
                .onFailure { _userMessage.emit("No se pudo enviar el mensaje") }
        }
    }

    fun sendChatImage(context: Context, uri: Uri, caption: String, onSent: () -> Unit = {}) {
        val text = caption.trim()
        if (!_chatConnected.value) {
            viewModelScope.launch { _userMessage.emit("Sin conexión en tiempo real") }
            return
        }
        if (text.length > CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val image = compressImage(context, uri)
                    container.chatRepository.sendImages(text, listOf(image))
                }
            }.onSuccess { msg ->
                _chatMessages.update { mergeChat(it, listOf(msg)) }
                onSent()
            }.onFailure {
                _userMessage.emit("No se pudo enviar la imagen")
            }
        }
    }

    fun editChatMessage(message: ChatMessageDto, body: String, onDone: () -> Unit = {}) {
        val text = body.trim()
        if (message.deleted || message.authorUserId != myUserId) return
        if (text.isEmpty()) return
        if (text.length > CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.chatRepository.edit(message.id, text) }
                .onSuccess { updated ->
                    _chatMessages.update { mergeChat(it, listOf(updated)) }
                    _userMessage.emit("Mensaje editado")
                    onDone()
                }
                .onFailure { _userMessage.emit("No se pudo editar el mensaje") }
        }
    }

    fun deleteChatMessage(message: ChatMessageDto) {
        if (message.deleted || message.authorUserId != myUserId) return
        viewModelScope.launch {
            runCatching { container.chatRepository.delete(message.id) }
                .onSuccess { updated ->
                    _chatMessages.update { mergeChat(it, listOf(updated)) }
                    _userMessage.emit("Mensaje eliminado")
                }
                .onFailure { _userMessage.emit("No se pudo eliminar el mensaje") }
        }
    }

    fun loadOlderChat() {
        if (!_chatHasMoreOlder.value) return
        val cursor = chatOldestCursor ?: return
        viewModelScope.launch {
            runCatching { container.chatRepository.loadHistory(before = cursor) }
                .onSuccess { history ->
                    _chatMessages.update { mergeChat(it, history.items) }
                    _chatHasMoreOlder.value = history.hasMore
                    chatOldestCursor = history.nextBefore
                }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            runCatching { container.chatRepository.clear() }
                .onSuccess {
                    _chatMessages.value = emptyList()
                    _chatHasMoreOlder.value = false
                    chatOldestCursor = null
                    _userMessage.emit("Chat borrado para ti")
                }
                .onFailure { _userMessage.emit("No se pudo borrar el chat") }
        }
    }

    fun exportChat(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { container.chatRepository.export() }
                .onSuccess { export ->
                    val text = buildString {
                        append("💬 Chat familiar\n\n")
                        export.messages.forEach { m ->
                            append(m.createdAt).append(" — ")
                            append(m.authorDisplayName).append(": ")
                            val attachments = m.attachments.orEmpty()
                            append(m.body ?: if (attachments.isEmpty()) "(mensaje eliminado)" else "")
                            if (attachments.isNotEmpty()) {
                                if (!m.body.isNullOrBlank()) append(" ")
                                append("[${attachments.size} imagen")
                                if (attachments.size != 1) append("es")
                                append("]")
                            }
                            append("\n")
                        }
                    }
                    onReady(text.trim())
                }
                .onFailure { _userMessage.emit("No se pudo exportar el chat") }
        }
    }

    /**
     * Descarga el original del adjunto con el cliente autenticado (SEC-3) y lo
     * guarda en la galeria. En API 29+ usa MediaStore sin permiso; en versiones
     * anteriores el llamador debe garantizar WRITE_EXTERNAL_STORAGE.
     */
    fun saveChatImageToGallery(context: Context, url: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).get().build()
                    container.httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val bytes = response.body.bytes()
                        writeImageToGallery(context, bytes, response.header("Content-Type"))
                    }
                }
            }
                .onSuccess { _userMessage.emit("Imagen guardada en la galeria") }
                .onFailure { _userMessage.emit("No se pudo guardar la imagen") }
        }
    }

    private fun writeImageToGallery(context: Context, bytes: ByteArray, contentType: String?) {
        val extension = when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val mime = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "recetas-chat-${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/RecetasFamiliares"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el registro en la galeria")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("No se pudo abrir el destino")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                if (resolver.update(uri, values, null, null) == 0) {
                    error("No se pudo finalizar el guardado en la galeria")
                }
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun startChatPolling() {
        chatPollingJob?.cancel()
        chatPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(CHAT_POLL_MS)
                if (!_chatConnected.value) {
                    runCatching { container.chatRepository.loadHistory() }
                        .onSuccess { history -> _chatMessages.update { mergeChat(it, history.items) } }
                }
            }
        }
    }

    /** Une mensajes evitando duplicados por id y mantiene orden cronologico ascendente. */
    private fun mergeChat(
        existing: List<ChatMessageDto>,
        incoming: List<ChatMessageDto>
    ): List<ChatMessageDto> {
        val byId = LinkedHashMap<String, ChatMessageDto>(existing.size + incoming.size)
        existing.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.createdAt }
    }

    override fun onCleared() {
        closeChat()
        closePrivateChat()
        super.onCleared()
    }

    private fun compressImage(context: Context, uri: Uri): Pair<ByteArray, String> {
        val maxDim = 1080
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, info, _ ->
                // Downsample durante la decodificacion: no cargar el bitmap a
                // resolucion completa en memoria (evita OOM con fotos grandes).
                val sample = sampleSizeFor(info.size.width, info.size.height, maxDim)
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
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

    private fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        // Duplica el factor mientras reducir a la mitad siga por encima del
        // objetivo: deja el bitmap decodificado cerca de maxDim, no a resolucion full.
        while (w / 2 >= maxDim && h / 2 >= maxDim) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}

class RecetasViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecetasViewModel(container) as T
}
