package org.gipsybuho.recetasfamiliares.core

expect class SessionStore() {
    var accessToken: String?
    var refreshToken: String?
    var familyId: String?
    var userId: String?
    var displayName: String?
    var email: String?
    var avatarUrl: String?
    val isLoggedIn: Boolean
    fun clear()
}
