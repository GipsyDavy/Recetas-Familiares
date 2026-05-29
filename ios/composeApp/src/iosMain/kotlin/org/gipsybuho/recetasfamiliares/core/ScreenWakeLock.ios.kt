package org.gipsybuho.recetasfamiliares.core

import platform.UIKit.UIApplication

actual class ScreenWakeLock actual constructor() {
    actual fun acquire() {
        UIApplication.sharedApplication.idleTimerDisabled = true
    }
    actual fun release() {
        UIApplication.sharedApplication.idleTimerDisabled = false
    }
}
