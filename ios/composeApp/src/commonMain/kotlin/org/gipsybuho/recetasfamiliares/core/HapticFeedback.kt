package org.gipsybuho.recetasfamiliares.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

expect class HapticFeedback {
    fun impact()
    fun selection()
    fun success()
    fun error()
}

@Composable
fun rememberHapticFeedback(): HapticFeedback = remember { HapticFeedback() }
