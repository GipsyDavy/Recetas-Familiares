package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "recetas_session_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _familyId = MutableStateFlow(preferences.getString("family_id", null))
    val familyIdFlow: StateFlow<String?> = _familyId.asStateFlow()

    private val _familyRole = MutableStateFlow(preferences.getString("family_role", null))
    val familyRoleFlow: StateFlow<String?> = _familyRole.asStateFlow()

    init {
        val activeFamilyId = _familyId.value
        val legacyLastSync = preferences.getString("last_sync_time", null)
        if (!activeFamilyId.isNullOrBlank() && !legacyLastSync.isNullOrBlank()) {
            preferences.edit {
                putString(lastSyncKey(activeFamilyId), legacyLastSync)
                remove("last_sync_time")
            }
        }
    }

    var accessToken: String?
        get() = preferences.getString("access_token", null)
        set(value) = preferences.edit { putString("access_token", value) }

    var refreshToken: String?
        get() = preferences.getString("refresh_token", null)
        set(value) = preferences.edit { putString("refresh_token", value) }

    var familyId: String?
        get() = preferences.getString("family_id", null)
        set(value) {
            preferences.edit { putString("family_id", value) }
            _familyId.value = value
        }

    var userId: String?
        get() = preferences.getString("user_id", null)
        set(value) = preferences.edit { putString("user_id", value) }

    var displayName: String?
        get() = preferences.getString("display_name", null)
        set(value) = preferences.edit { putString("display_name", value) }

    var email: String?
        get() = preferences.getString("email", null)
        set(value) = preferences.edit { putString("email", value) }

    var avatarUrl: String?
        get() = preferences.getString("avatar_url", null)
        set(value) = preferences.edit { putString("avatar_url", value) }

    var familyRole: String?
        get() = preferences.getString("family_role", null)
        set(value) {
            preferences.edit { putString("family_role", value) }
            _familyRole.value = value
        }

    fun lastSyncTimeFor(familyId: String): String? =
        preferences.getString(lastSyncKey(familyId), null)

    /** El cursor se escribe bajo la familia del pull: un sync en vuelo de la
     *  familia anterior no puede corromper el cursor de la nueva. */
    fun setLastSyncTime(familyId: String, value: String?) {
        preferences.edit { putString(lastSyncKey(familyId), value) }
    }

    fun setActiveFamily(id: String, role: String?) {
        preferences.edit {
            putString("family_id", id)
            putString("family_role", role)
        }
        _familyId.value = id
        _familyRole.value = role
    }

    /** Ultimo usuario con sesion en este dispositivo. Sobrevive a clear()
     *  para que el siguiente login detecte cambio de usuario y vacie la
     *  cache local (privacidad en dispositivos compartidos). */
    val lastKnownUserId: String?
        get() = preferences.getString("user_id", null)
            ?: preferences.getString("last_user_id", null)

    /** true mientras un vaciado de cache local esta pendiente o fallo a
     *  mitad; el siguiente login debe forzar el vaciado aunque el usuario
     *  coincida. Sobrevive a clear(). */
    var pendingWipe: Boolean
        get() = preferences.getBoolean("pending_wipe", false)
        set(value) = preferences.edit { putBoolean("pending_wipe", value) }

    fun clear() {
        val lastUser = lastKnownUserId
        val wipePending = pendingWipe
        preferences.edit {
            clear()
            if (lastUser != null) putString("last_user_id", lastUser)
            if (wipePending) putBoolean("pending_wipe", true)
        }
        _familyId.value = null
        _familyRole.value = null
    }

    private fun lastSyncKey(familyId: String): String = "last_sync_time_$familyId"
}
