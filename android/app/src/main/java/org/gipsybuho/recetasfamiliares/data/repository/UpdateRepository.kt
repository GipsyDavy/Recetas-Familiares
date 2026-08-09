package org.gipsybuho.recetasfamiliares.data.repository

import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.PlatformReleaseDto

/** Version recomendada de la aplicacion Android segun el servidor. */
class UpdateRepository(private val api: RecetasApi) {

    /** null si no hay nada publicado o si la consulta falla. */
    suspend fun latestAndroidRelease(): PlatformReleaseDto? =
        runCatching { api.appVersion().androidApp }.getOrNull()
}
