package org.gipsybuho.recetasfamiliares.core

import platform.Foundation.NSUserDefaults

actual class ServerUrlPreference {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual var baseUrl: String
        get() {
            val saved = defaults.stringForKey(KEY_BASE_URL)
            if (!saved.isNullOrBlank()) {
                return runCatching { ServerUrlConfig.normalizeAndValidate(saved) }
                    .getOrElse {
                        defaults.removeObjectForKey(KEY_BASE_URL)
                        ServerUrlConfig.DEFAULT_API_BASE_URL
                    }
            }
            return ServerUrlConfig.DEFAULT_API_BASE_URL
        }
        set(value) {
            defaults.setObject(ServerUrlConfig.normalizeAndValidate(value), forKey = KEY_BASE_URL)
        }

    actual fun reset() {
        defaults.removeObjectForKey(KEY_BASE_URL)
    }

    private companion object {
        const val KEY_BASE_URL = "rf_api_base_url"
    }
}
