package org.gipsybuho.recetasfamiliares.core

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareText(text: String) {
    val controller = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    UIApplication.sharedApplication.keyWindow
        ?.rootViewController
        ?.presentViewController(controller, animated = true, completion = null)
}
