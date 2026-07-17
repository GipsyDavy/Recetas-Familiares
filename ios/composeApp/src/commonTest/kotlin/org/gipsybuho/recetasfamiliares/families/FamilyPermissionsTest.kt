package org.gipsybuho.recetasfamiliares.families

import org.gipsybuho.recetasfamiliares.network.FamilyDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyPermissionsTest {

    private fun family(id: String, role: String?) = FamilyDto(id, "Familia $id", role, null)

    @Test
    fun `copyTargets excludes active family and non editor roles`() {
        val families = listOf(
            family("f1", "OWNER"),   // activa: fuera
            family("f2", "ADMIN"),   // destino válido
            family("f3", "MEMBER"),  // sin permiso de escritura: fuera
            family("f4", "OWNER"),   // destino válido
            family("f5", null)       // sin rol: fuera
        )

        val targets = copyTargets(families, "f1")

        assertEquals(listOf("f2", "f4"), targets.map { it.id })
    }

    @Test
    fun `copyTargets is empty when user only belongs to active family`() {
        assertTrue(copyTargets(listOf(family("f1", "OWNER")), "f1").isEmpty())
    }

    @Test
    fun `canCreateFamily requires editor role in some family`() {
        assertTrue(canCreateFamily(listOf(family("f1", "MEMBER"), family("f2", "ADMIN"))))
        assertFalse(canCreateFamily(listOf(family("f1", "MEMBER"))))
    }

    @Test
    fun `canCreateFamily allows user without families`() {
        assertTrue(canCreateFamily(emptyList()))
    }
}
