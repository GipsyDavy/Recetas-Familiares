package org.gipsybuho.recetasfamiliares.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateUserRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserResponseDto
import java.io.ByteArrayOutputStream

class UserRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    /** Perfil propio con el estado de verificacion del correo (CRIT-2). */
    suspend fun me(): UserResponseDto = api.getMe()

    suspend fun updateDisplayName(newName: String): UserResponseDto {
        val response = api.updateMe(UpdateUserRequestDto(newName.trim()))
        sessionStore.displayName = response.displayName
        return response
    }

    suspend fun uploadAvatar(uri: Uri, context: Context): UserResponseDto {
        val bytes = compressImage(context, uri)
        val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "avatar.jpg", requestBody)
        val response = api.uploadAvatar(part)
        sessionStore.avatarUrl = response.avatarUrl
        return response
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray =
        compressAvatarImage(context, uri)
}

/** Compresion compartida para avatares (usuario y familia): 512px max, JPEG 80. */
internal fun compressAvatarImage(context: Context, uri: Uri): ByteArray {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        @Suppress("DEPRECATION")
        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val maxDim = 512
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
    } else bitmap
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
    if (scaled !== bitmap) scaled.recycle()
    return baos.toByteArray()
}
