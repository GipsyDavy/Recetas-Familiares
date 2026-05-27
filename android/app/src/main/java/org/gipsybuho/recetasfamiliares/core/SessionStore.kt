package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.core.content.edit

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("recetas_session", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = preferences.getString("access_token", null)
        set(value) = preferences.edit { putString("access_token", value) }

    var refreshToken: String?
        get() = preferences.getString("refresh_token", null)
        set(value) = preferences.edit { putString("refresh_token", value) }

    var familyId: String?
        get() = preferences.getString("family_id", null)
        set(value) = preferences.edit { putString("family_id", value) }

    fun clear() {
        preferences.edit { clear() }
    }
}
