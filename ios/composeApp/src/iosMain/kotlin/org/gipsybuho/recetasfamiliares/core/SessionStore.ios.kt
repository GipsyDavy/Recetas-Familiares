package org.gipsybuho.recetasfamiliares.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setObject
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual class SessionStore {

    private val SERVICE = "com.gipsybuho.recetasfamiliares"

    actual var accessToken: String?
        get()  = kcRead(KEY_ACCESS)
        set(v) = if (v != null) kcWrite(KEY_ACCESS, v) else kcDelete(KEY_ACCESS)

    actual var refreshToken: String?
        get()  = kcRead(KEY_REFRESH)
        set(v) = if (v != null) kcWrite(KEY_REFRESH, v) else kcDelete(KEY_REFRESH)

    actual var familyId: String?
        get()  = kcRead(KEY_FAMILY)
        set(v) = if (v != null) kcWrite(KEY_FAMILY, v) else kcDelete(KEY_FAMILY)

    actual var userId: String?
        get()  = kcRead(KEY_USER)
        set(v) = if (v != null) kcWrite(KEY_USER, v) else kcDelete(KEY_USER)

    actual var displayName: String?
        get()  = kcRead(KEY_DISPLAY_NAME)
        set(v) = if (v != null) kcWrite(KEY_DISPLAY_NAME, v) else kcDelete(KEY_DISPLAY_NAME)

    actual var email: String?
        get()  = kcRead(KEY_EMAIL)
        set(v) = if (v != null) kcWrite(KEY_EMAIL, v) else kcDelete(KEY_EMAIL)

    actual var avatarUrl: String?
        get()  = kcRead(KEY_AVATAR_URL)
        set(v) = if (v != null) kcWrite(KEY_AVATAR_URL, v) else kcDelete(KEY_AVATAR_URL)

    actual var familyRole: String?
        get()  = kcRead(KEY_FAMILY_ROLE)
        set(v) = if (v != null) kcWrite(KEY_FAMILY_ROLE, v) else kcDelete(KEY_FAMILY_ROLE)

    actual val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !familyId.isNullOrBlank()

    actual fun clear() {
        listOf(KEY_ACCESS, KEY_REFRESH, KEY_FAMILY, KEY_USER, KEY_DISPLAY_NAME, KEY_EMAIL, KEY_AVATAR_URL, KEY_FAMILY_ROLE).forEach { kcDelete(it) }
    }

    // ── Keychain helpers ──────────────────────────────────────────────────────

    private fun kcWrite(key: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        kcDelete(key)                               // prevent duplicate-item error
        val q = baseQuery(key)
        q.setObject(data, forKey = kSecValueData as NSCopying)
        SecItemAdd(q as platform.CoreFoundation.CFDictionaryRef, null)
    }

    private fun kcRead(key: String): String? = memScoped {
        val q = baseQuery(key)
        q.setObject(NSNumber.numberWithBool(true), forKey = kSecReturnData as NSCopying)
        q.setObject(kSecMatchLimitOne, forKey = kSecMatchLimit as NSCopying)
        val out = alloc<ObjCObjectVar<Any?>>()
        val status = SecItemCopyMatching(
            q as platform.CoreFoundation.CFDictionaryRef,
            out.ptr.reinterpret()
        )
        if (status != errSecSuccess) return@memScoped null
        (out.value as? platform.Foundation.NSData)?.let { data ->
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    private fun kcDelete(key: String) {
        SecItemDelete(baseQuery(key) as platform.CoreFoundation.CFDictionaryRef)
    }

    private fun baseQuery(key: String): NSMutableDictionary {
        val q = NSMutableDictionary()
        q.setObject(kSecClassGenericPassword, forKey = kSecClass as NSCopying)
        q.setObject(SERVICE as NSString, forKey = kSecAttrService as NSCopying)
        q.setObject(key as NSString, forKey = kSecAttrAccount as NSCopying)
        return q
    }

    companion object {
        private const val KEY_ACCESS       = "rf_access_token"
        private const val KEY_REFRESH      = "rf_refresh_token"
        private const val KEY_FAMILY       = "rf_family_id"
        private const val KEY_USER         = "rf_user_id"
        private const val KEY_DISPLAY_NAME = "rf_display_name"
        private const val KEY_EMAIL        = "rf_email"
        private const val KEY_AVATAR_URL   = "rf_avatar_url"
        private const val KEY_FAMILY_ROLE  = "rf_family_role"
    }
}
