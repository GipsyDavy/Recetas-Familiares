package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.EditPrivateMessageRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendPrivateMessageRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateChatRepositoryTest {

    private val api = mockk<RecetasApi>()
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val repository = PrivateChatRepository(api, sessionStore) { "https://recetas.test/" }

    @Test
    fun listConversationsDevuelveLaBandeja() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val conversation = PrivateConversationDto(
            conversationId = "c1", otherUserId = "u2", otherUserDisplayName = "Ana",
            otherUserAvatarUrl = null, lastMessagePreview = "Hola", lastMessageAt = "2026-07-23T10:00:00Z"
        )
        coEvery { api.conversations("fam-1") } returns listOf(conversation)

        val result = repository.listConversations()

        assertEquals(1, result.size)
        assertEquals("c1", result[0].conversationId)
    }

    @Test
    fun listConversationsSinFamiliaLanzaIllegalState() = runTest {
        every { sessionStore.familyId } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.listConversations() }
        }
    }

    @Test
    fun createOrGetConversationLlamaAlEndpointWith() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val conversation = PrivateConversationDto(
            conversationId = "c1", otherUserId = "u2", otherUserDisplayName = "Ana",
            otherUserAvatarUrl = null, lastMessagePreview = null, lastMessageAt = null
        )
        coEvery { api.createOrGetConversation("fam-1", "u2") } returns conversation

        val result = repository.createOrGetConversation("u2")

        assertEquals("c1", result.conversationId)
    }

    @Test
    fun loadHistoryPideElCursorYElLimite() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        coEvery { api.privateMessages("fam-1", "c1", "msg-9", PrivateChatRepository.PAGE_SIZE) } returns
            PrivateMessageHistoryDto(items = emptyList(), hasMore = false, nextBefore = null)

        val result = repository.loadHistory("c1", before = "msg-9")

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun sendEnviaIdYBodyYDevuelveElMensaje() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val message = PrivateMessageDto(
            id = "m1", conversationId = "c1", authorUserId = "u1", authorDisplayName = "Yo",
            body = "Hola", attachments = emptyList(), createdAt = "2026-07-23T10:00:00Z",
            updatedAt = "2026-07-23T10:00:00Z", syncVersion = 1, deleted = false
        )
        coEvery { api.sendPrivateMessage("fam-1", "c1", any()) } returns message

        val result = repository.send("c1", "Hola")

        assertEquals("m1", result.id)
    }

    @Test
    fun sendVacioLanzaIllegalArgument() = runTest {
        every { sessionStore.familyId } returns "fam-1"

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.send("c1", "   ") }
        }
    }

    @Test
    fun sendImagesConMasDeCincoImagenesLanzaIllegalArgument() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val images = (1..6).map { byteArrayOf(1, 2, 3) to "image/jpeg" }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.sendImages("c1", "texto", images) }
        }
    }

    @Test
    fun editLlamaConElCuerpoNuevo() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val message = PrivateMessageDto(
            id = "m1", conversationId = "c1", authorUserId = "u1", authorDisplayName = "Yo",
            body = "Editado", attachments = emptyList(), createdAt = "2026-07-23T10:00:00Z",
            updatedAt = "2026-07-23T10:05:00Z", syncVersion = 2, deleted = false
        )
        coEvery {
            api.editPrivateMessage("fam-1", "c1", "m1", EditPrivateMessageRequestDto("Editado"))
        } returns message

        val result = repository.edit("c1", "m1", "Editado")

        assertEquals("Editado", result.body)
    }

    @Test
    fun deleteDevuelveElMensajeBorrado() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val message = PrivateMessageDto(
            id = "m1", conversationId = "c1", authorUserId = "u1", authorDisplayName = "Yo",
            body = null, attachments = emptyList(), createdAt = "2026-07-23T10:00:00Z",
            updatedAt = "2026-07-23T10:06:00Z", syncVersion = 3, deleted = true
        )
        coEvery { api.deletePrivateMessage("fam-1", "c1", "m1") } returns message

        val result = repository.delete("c1", "m1")

        assertTrue(result.deleted)
    }

    @Test
    fun exportDevuelveElHistorialCompleto() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        coEvery { api.exportPrivateConversation("fam-1", "c1") } returns
            PrivateMessageExportDto(
                conversationId = "c1", exportedAt = "2026-07-23T10:10:00Z",
                totalMessages = 0, messages = emptyList()
            )

        val result = repository.export("c1")

        assertEquals("c1", result.conversationId)
    }

    @Test
    fun attachmentUrlDeUploadsDmSeReescribeAlHostConfigurado() = runTest {
        every { sessionStore.familyId } returns "fam-1"
        val message = PrivateMessageDto(
            id = "m1", conversationId = "c1", authorUserId = "u1", authorDisplayName = "Yo",
            body = "Mira", attachments = listOf(
                org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateAttachmentDto(
                    id = "a1", url = "http://localhost:8080/uploads/dm/foo.jpg",
                    thumbnailUrl = "http://localhost:8080/uploads/dm_thumbnails/foo.jpg",
                    contentType = "image/jpeg", sizeBytes = 100
                )
            ),
            createdAt = "2026-07-23T10:00:00Z", updatedAt = "2026-07-23T10:00:00Z",
            syncVersion = 1, deleted = false
        )
        coEvery { api.sendPrivateMessage("fam-1", "c1", any()) } returns message

        val result = repository.send("c1", "Mira")

        assertEquals("https://recetas.test/uploads/dm/foo.jpg", result.attachments!![0].url)
        assertEquals("https://recetas.test/uploads/dm_thumbnails/foo.jpg", result.attachments!![0].thumbnailUrl)
    }
}
