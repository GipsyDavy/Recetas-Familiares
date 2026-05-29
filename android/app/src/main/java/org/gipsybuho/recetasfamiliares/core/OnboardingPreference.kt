package org.gipsybuho.recetasfamiliares.core

import android.content.Context

class OnboardingPreference(context: Context) {
    private val prefs = context.getSharedPreferences("rf_onboarding", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) { prefs.edit().putBoolean(KEY, value).apply() }

    companion object {
        private const val KEY = "done"
    }
}
