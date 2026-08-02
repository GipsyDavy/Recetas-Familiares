package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

/**
 * Miniatura cuadrada de portada, para los listados donde la card grande no cabe:
 * busqueda global y filas de comida del menu. Misma receta visual que RecipeCard,
 * en pequeno.
 *
 * Con coverUrl nulo deja el placeholder: nunca un hueco vacio, porque los titulos
 * quedarian en dos margenes distintos y el ojo pierde la columna al hacer scroll.
 */
@Composable
fun RecipeThumb(coverUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(
            Icons.Outlined.Restaurant,
            contentDescription = null,
            modifier = Modifier.size(size / 2).align(Alignment.Center),
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.30f)
        )
        Crossfade(targetState = coverUrl, label = "recipeThumbCover") { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
