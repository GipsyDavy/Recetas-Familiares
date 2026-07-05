package org.gipsybuho.recetasfamiliares.core

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

actual class HapticFeedback actual constructor() {
    private val prefs          = org.gipsybuho.recetasfamiliares.theme.ThemePreference()
    private val impactGen      = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val selectionGen   = UISelectionFeedbackGenerator()
    private val notificationGen = UINotificationFeedbackGenerator()

    actual fun impact()    { if (prefs.hapticsEnabled) impactGen.impactOccurred() }
    actual fun selection() { if (prefs.hapticsEnabled) selectionGen.selectionChanged() }
    actual fun success()   { if (prefs.hapticsEnabled) notificationGen.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess) }
    actual fun error()     { if (prefs.hapticsEnabled) notificationGen.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError) }
}
