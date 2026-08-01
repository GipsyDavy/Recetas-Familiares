package org.gipsybuho.recetasfamiliares.ui

import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity

/**
 * Regla de portada, identica a la del backend: se prefiere el thumbnail y se cae a la
 * imagen original si no lo hay. Null cuando ninguna de las dos es utilizable.
 */
fun preferredCoverUrl(thumbnailUrl: String?, url: String?): String? =
    thumbnailUrl?.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() }

/** Mismo desempate que `RecipePhotoDao.observeCovers` y el backend: menor `position`, y a
 *  igualdad de `position`, menor `id`. */
private val COVER_ORDER = compareBy<RecipePhotoEntity>({ it.position }, { it.id })

/**
 * Agrupa fotos por receta y se queda con la portada de cada una: la de menor `position`
 * (desempatando por `id`). No asume que `photos` llegue ya ordenada -- compara explicitamente,
 * asi que es correcta con cualquier orden de entrada. Recetas sin ninguna url utilizable
 * (ni thumbnail ni original) no aparecen en el mapa resultante.
 */
fun coversByRecipeId(photos: List<RecipePhotoEntity>): Map<String, String> {
    val bestPhotoByRecipeId = HashMap<String, RecipePhotoEntity>()
    photos.forEach { photo ->
        val current = bestPhotoByRecipeId[photo.recipeId]
        if (current == null || COVER_ORDER.compare(photo, current) < 0) {
            bestPhotoByRecipeId[photo.recipeId] = photo
        }
    }
    return buildMap {
        bestPhotoByRecipeId.forEach { (recipeId, photo) ->
            preferredCoverUrl(photo.thumbnailUrl, photo.url)?.let { put(recipeId, it) }
        }
    }
}
