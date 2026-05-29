package org.gipsybuho.recetasfamiliares.core

import platform.Foundation.NSUserDefaults

actual class OnboardingPreference {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual var onboardingDone: Boolean
        get() = defaults.boolForKey(KEY)
        set(value) { defaults.setBool(value, forKey = KEY) }

    companion object {
        private const val KEY = "rf_onboarding_done"
    }
}
