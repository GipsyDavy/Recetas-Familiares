package org.gipsybuho.recetasfamiliares.families

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.FamilyDto

class FamilyViewModel(
    private val repository: FamilyMemberRepository,
    private val session: SessionStore,
    private val scope: CoroutineScope
) {
    private val _families = MutableStateFlow<List<FamilyDto>>(emptyList())
    val families: StateFlow<List<FamilyDto>> = _families.asStateFlow()

    private val _activeFamily = MutableStateFlow<FamilyDto?>(null)
    val activeFamily: StateFlow<FamilyDto?> = _activeFamily.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadFamilies() {
        scope.launch {
            runCatching { repository.families() }
                .onSuccess { list ->
                    _families.value = list
                    val currentId = session.familyId
                    _activeFamily.value = list.firstOrNull { it.id == currentId } ?: list.firstOrNull()
                }
                .onFailure { _errorMessage.value = "No se pudieron cargar las familias" }
        }
    }

    fun switchActiveFamily(familyId: String) {
        if (familyId == session.familyId) return
        val target = _families.value.firstOrNull { it.id == familyId }
        if (target == null) {
            _errorMessage.value = "No se pudo cambiar de familia"
            return
        }
        repository.setActiveFamily(target)
        _activeFamily.value = target
        _errorMessage.value = null
    }

    fun createFamily(name: String) {
        scope.launch {
            runCatching { repository.createFamily(name) }
                .onSuccess { loadFamilies() }
                .onFailure { _errorMessage.value = "No se pudo crear la familia" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
