package org.gipsybuho.recetasfamiliares.core

import kotlinx.coroutines.flow.StateFlow

expect class SessionStore() {
    var accessToken: String?
    var refreshToken: String?
    var familyId: String?
    var userId: String?
    var displayName: String?
    var email: String?
    var avatarUrl: String?
    var familyRole: String?
    val familyRoleFlow: StateFlow<String?>
    val isLoggedIn: Boolean
    fun clear()
}
