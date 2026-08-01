package org.gipsybuho.recetasfamiliares.ui

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
}
