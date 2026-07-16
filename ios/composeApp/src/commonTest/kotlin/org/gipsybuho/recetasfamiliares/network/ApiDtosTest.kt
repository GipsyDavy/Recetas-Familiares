package org.gipsybuho.recetasfamiliares.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `FamilyDto decodes avatarUrl from backend shape`() {
        val raw = """{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":"https://x/avatar.png"}"""
        val dto = json.decodeFromString<FamilyDto>(raw)
        assertEquals("https://x/avatar.png", dto.avatarUrl)
    }

    @Test
    fun `FamilyDto tolerates missing avatarUrl`() {
        val raw = """{"id":"f1","name":"Casa","role":"OWNER"}"""
        val dto = json.decodeFromString<FamilyDto>(raw)
        assertNull(dto.avatarUrl)
    }

    @Test
    fun `CreateFamilyRequestDto encodes name field`() {
        val encoded = json.encodeToString(CreateFamilyRequestDto("Casa Nueva"))
        assertEquals("""{"name":"Casa Nueva"}""", encoded)
    }
}
