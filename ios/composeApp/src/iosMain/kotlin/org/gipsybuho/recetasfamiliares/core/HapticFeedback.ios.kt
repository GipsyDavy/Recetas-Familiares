package org.gipsybuho.recetasfamiliares.core

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyleMedium
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackTypeError
import platform.UIKit.UINotificationFeedbackTypeSuccess
import platform.UIKit.UISelectionFeedbackGenerator

actual class HapticFeedback {
    private val prefs          = org.gipsybuho.recetasfamiliares.theme.ThemePreference()
    private val impactGen      = UIImpactFeedbackGenerator(UIImpactFeedbackStyleMedium)
    private val selectionGen   = UISelectionFeedbackGenerator()
    private val notificationGen = UINotificationFeedbackGenerator()

    actual fun impact()    { if (prefs.hapticsEnabled) impactGen.impactOccurred() }
    actual fun selection() { if (prefs.hapticsEnabled) selectionGen.selectionChanged() }
    actual fun success()   { if (prefs.hapticsEnabled) notificationGen.notificationOccurred(UINotificationFeedbackTypeSuccess) }
    actual fun error()     { if (prefs.hapticsEnabled) notificationGen.notificationOccurred(UINotificationFeedbackTypeError) }
}
