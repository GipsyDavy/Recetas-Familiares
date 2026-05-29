package org.gipsybuho.recetasfamiliares.core

import androidx.compose.runtime.Composable

expect class ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onPick: (ByteArray?) -> Unit): ImagePickerLauncher
