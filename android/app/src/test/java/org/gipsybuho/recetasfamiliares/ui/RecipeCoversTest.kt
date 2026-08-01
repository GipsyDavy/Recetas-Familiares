package org.gipsybuho.recetasfamiliares.ui

import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeCoversTest {

    @Test
    fun `prefiere el thumbnail cuando existe`() {
        assertEquals(
            "https://cdn.test/thumb.jpg",
            preferredCoverUrl("https://cdn.test/thumb.jpg", "https://cdn.test/original.jpg")
        )
    }

    @Test
    fun `cae al original cuando el thumbnail es nulo o esta en blanco`() {
        assertEquals("https://cdn.test/original.jpg", preferredCoverUrl(null, "https://cdn.test/original.jpg"))
        assertEquals("https://cdn.test/original.jpg", preferredCoverUrl("   ", "https://cdn.test/original.jpg"))
    }

    @Test
    fun `devuelve null cuando no hay ninguna url utilizable`() {
        assertNull(preferredCoverUrl(null, null))
        assertNull(preferredCoverUrl("  ", "  "))
    }

    private fun photo(
        id: String,
        recipeId: String,
        position: Int,
        url: String? = "https://cdn.test/$id.jpg",
        thumbnailUrl: String? = null
    ) = RecipePhotoEntity(
        id = id,
        recipeId = recipeId,
        position = position,
        url = url ?: "",
        thumbnailUrl = thumbnailUrl,
        caption = null,
        contentType = "image/jpeg",
        sizeBytes = 1_000L,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        syncVersion = 1L,
        deleted = false
    )

    @Test
    fun `coversByRecipeId se queda con la foto de menor position de cada receta`() {
        val photos = listOf(
            photo(id = "p2", recipeId = "r1", position = 1, thumbnailUrl = "https://cdn.test/p2-thumb.jpg"),
            photo(id = "p1", recipeId = "r1", position = 0, thumbnailUrl = "https://cdn.test/p1-thumb.jpg")
        )

        val covers = coversByRecipeId(photos)

        assertEquals("https://cdn.test/p1-thumb.jpg", covers["r1"])
    }

    @Test
    fun `coversByRecipeId omite una receta cuyas fotos no tienen url utilizable`() {
        val photos = listOf(
            photo(id = "p1", recipeId = "r1", position = 0, url = " ", thumbnailUrl = "   ")
        )

        val covers = coversByRecipeId(photos)

        assertEquals(emptyMap<String, String>(), covers)
    }

    @Test
    fun `coversByRecipeId agrupa fotos de varias recetas correctamente`() {
        val photos = listOf(
            photo(id = "p1", recipeId = "r1", position = 0, thumbnailUrl = "https://cdn.test/r1-thumb.jpg"),
            photo(id = "p2", recipeId = "r2", position = 0, thumbnailUrl = "https://cdn.test/r2-thumb.jpg"),
            photo(id = "p3", recipeId = "r2", position = 1, thumbnailUrl = "https://cdn.test/r2-segunda.jpg")
        )

        val covers = coversByRecipeId(photos)

        assertEquals(
            mapOf(
                "r1" to "https://cdn.test/r1-thumb.jpg",
                "r2" to "https://cdn.test/r2-thumb.jpg"
            ),
            covers
        )
    }
}
