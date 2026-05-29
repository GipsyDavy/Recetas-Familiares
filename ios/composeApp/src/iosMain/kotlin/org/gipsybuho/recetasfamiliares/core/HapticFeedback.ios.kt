package org.gipsybuho.recetasfamiliares.core

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyleMedium
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackTypeError
import platform.UIKit.UINotificationFeedbackTypeSuccess
import platform.UIKit.UISelectionFeedbackGenerator

actual class HapticFeedback {
    private val impactGen      = UIImpactFeedbackGenerator(UIImpactFeedbackStyleMedium)
    private val selectionGen   = UISelectionFeedbackGenerator()
    private val notificationGen = UINotificationFeedbackGenerator()

    actual fun impact()    = impactGen.impactOccurred()
    actual fun selection() = selectionGen.selectionChanged()
    actual fun success()   = notificationGen.notificationOccurred(UINotificationFeedbackTypeSuccess)
    actual fun error()     = notificationGen.notificationOccurred(UINotificationFeedbackTypeError)
}
