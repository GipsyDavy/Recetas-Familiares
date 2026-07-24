# Chat Privado Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android (Kotlin + Jetpack Compose) client for the already-shipped private 1:1 chat backend — TopAppBar icon with badge, conversation inbox, per-conversation screen, and a "Mensaje" entry point from Miembros.

**Architecture:** New `PrivateChatRepository` mirrors the existing `ChatRepository`'s REST pattern (suspend functions over `RecetasApi`, MockK-testable). The existing `ChatSocket` (already used twice independently in this app — `chatBadgeSocket` for the always-on badge, `chatSocket` for the open `ChatScreen`) is extended, not duplicated: it gains a fixed inbox-topic subscription (for the badge) and an optional `conversationId` fixed at construction time (for a private conversation's own ephemeral socket) — no dynamic subscribe/unsubscribe needed, since Android already opens a fresh socket per screen visit rather than keeping one shared connection. `ConversationsScreen`/`PrivateChatScreen` are new composables mirroring `ChatScreen.kt`'s structure. No Jetpack Navigation Compose exists in this app — screens are shown via boolean state flags with early return (the `chatOpen` pattern), and `ConversationsScreen` gets an `initialConversation` parameter mirroring the already-established `initialRecipeId` pattern.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Retrofit + Gson, OkHttp (manual STOMP over WebSocket), MockK + JUnit4 + kotlinx-coroutines-test.

---

## File Manifest

| File | Change |
|---|---|
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt` | Modify (add DTOs) |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt` | Modify (add endpoints) |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.kt` | Create |
| `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryTest.kt` | Create |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt` | Modify |
| `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContainer.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsScreen.kt` | Create |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatScreen.kt` | Create |

---

### Task 1: DTOs — `ApiDtos.kt`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt`

Backend contract already verified (matches `PrivateConversationResponse`, `PrivateMessageResponse`, `PrivateMessageAttachmentResponse`, `PrivateMessageHistoryResponse`, `PrivateMessageExportResponse`, `SendPrivateMessageRequest`, `EditPrivateMessageRequest`, `PrivateInboxPing` from the backend `dm/` package).

- [ ] **Step 1: Append these data classes** at the end of the file (after the existing `PresenceResponseDto` at line 556):

```kotlin

// ── Chat privado 1:1 ─────────────────────────────────────────────────────────

data class PrivateAttachmentDto(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null
)

data class PrivateMessageDto(
    val id: String,
    val conversationId: String,
    val authorUserId: String,
    val authorDisplayName: String,
    val body: String?,
    val attachments: List<PrivateAttachmentDto>? = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean = false
)

data class PrivateConversationDto(
    val conversationId: String,
    val otherUserId: String,
    val otherUserDisplayName: String,
    val otherUserAvatarUrl: String?,
    val lastMessagePreview: String?,
    val lastMessageAt: String?
)

data class PrivateMessageHistoryDto(
    val items: List<PrivateMessageDto>,
    val hasMore: Boolean,
    val nextBefore: String? = null
)

data class PrivateMessageExportDto(
    val conversationId: String,
    val exportedAt: String,
    val totalMessages: Int,
    val messages: List<PrivateMessageDto>
)

data class SendPrivateMessageRequestDto(
    val id: String,
    val body: String
)

data class EditPrivateMessageRequestDto(
    val body: String
)

data class PrivateInboxPingDto(
    val conversationId: String,
    val senderUserId: String,
    val sentAt: String
)
```

- [ ] **Step 2: Compile to verify no syntax errors**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt
git commit -m "feat(android): DTOs del chat privado (mirror de ChatMessageDto/ChatHistoryDto)"
```

---

### Task 2: Endpoints — `RecetasApi.kt`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt`

- [ ] **Step 1: Add the 4 new DTO imports** (alongside the existing `dto.ChatMessageDto` etc. imports, in the same import block):

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.EditPrivateMessageRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendPrivateMessageRequestDto
```

- [ ] **Step 2: Add the 8 endpoint methods**, right after the existing `presence()` method (last method in the interface, just before the closing `}`):

```kotlin

    // ── Chat privado 1:1 ─────────────────────────────────────────────────────

    @POST("api/v1/families/{familyId}/conversations/with/{otherUserId}")
    suspend fun createOrGetConversation(
        @Path("familyId") familyId: String,
        @Path("otherUserId") otherUserId: String
    ): PrivateConversationDto

    @GET("api/v1/families/{familyId}/conversations")
    suspend fun conversations(
        @Path("familyId") familyId: String
    ): List<PrivateConversationDto>

    @GET("api/v1/families/{familyId}/conversations/{conversationId}/messages")
    suspend fun privateMessages(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null
    ): PrivateMessageHistoryDto

    @POST("api/v1/families/{familyId}/conversations/{conversationId}/messages")
    suspend fun sendPrivateMessage(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String,
        @Body request: SendPrivateMessageRequestDto
    ): PrivateMessageDto

    @Multipart
    @POST("api/v1/families/{familyId}/conversations/{conversationId}/messages/images")
    suspend fun sendPrivateImageMessage(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String,
        @Part("id") id: RequestBody,
        @Part("body") body: RequestBody? = null,
        @Part files: List<MultipartBody.Part>
    ): PrivateMessageDto

    @PUT("api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}")
    suspend fun editPrivateMessage(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body request: EditPrivateMessageRequestDto
    ): PrivateMessageDto

    @DELETE("api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}")
    suspend fun deletePrivateMessage(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String
    ): PrivateMessageDto

    @POST("api/v1/families/{familyId}/conversations/{conversationId}/clear")
    suspend fun clearPrivateConversation(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String
    ): Unit

    @GET("api/v1/families/{familyId}/conversations/{conversationId}/export")
    suspend fun exportPrivateConversation(
        @Path("familyId") familyId: String,
        @Path("conversationId") conversationId: String
    ): PrivateMessageExportDto
```

- [ ] **Step 3: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt
git commit -m "feat(android): endpoints de chat privado en RecetasApi"
```

---

### Task 3: `PrivateChatRepository` — REST + tests

**Files:**
- Create: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.kt`
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryTest.kt`

Mirrors `ChatRepository.kt`'s REST methods (`loadHistory`, `send`, `sendImages`, `edit`, `delete`, `clear`, `export`), scoped to a `conversationId` instead of the whole family, plus `listConversations`/`createOrGetConversation`. Includes its own attachment URL rewriting (backend stores DM images under `/uploads/dm/` and `/uploads/dm_thumbnails/`, confirmed in `PrivateChatService.java:404`) — same reasoning as `ChatRepository.normalizeAttachments`: the backend's `app.upload.base-url` isn't reachable from an emulator/device, so URLs must be rewritten to the actual configured API host.

- [ ] **Step 1: Write the failing tests** (MockK pattern, mirrors `FamilyMemberRepositoryPresenceTest.kt`):

```kotlin
package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
            runTest { repository.listConversations() }
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
            runTest { repository.send("c1", "   ") }
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.PrivateChatRepositoryTest"`
Expected: compile error, `PrivateChatRepository` does not exist yet.

- [ ] **Step 3: Write `PrivateChatRepository.kt`**

```kotlin
package org.gipsybuho.recetasfamiliares.data.repository

import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.EditPrivateMessageRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendPrivateMessageRequestDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

const val PRIVATE_CHAT_MAX_BODY_LENGTH = 2_000

/**
 * Chat privado 1:1 fase Android: envio/historial por REST. El tiempo real llega
 * por el ChatSocket compartido (ver ChatRepository.openRealtime), no aqui.
 */
class PrivateChatRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore,
    private val baseUrlProvider: () -> String
) {

    suspend fun listConversations(): List<PrivateConversationDto> =
        api.conversations(requireFamily())

    suspend fun createOrGetConversation(otherUserId: String): PrivateConversationDto =
        api.createOrGetConversation(requireFamily(), otherUserId)

    suspend fun loadHistory(
        conversationId: String,
        before: String? = null,
        limit: Int = PAGE_SIZE
    ): PrivateMessageHistoryDto {
        val history = api.privateMessages(requireFamily(), conversationId, before, limit)
        return history.copy(items = history.items.map(::normalizeAttachments))
    }

    suspend fun send(conversationId: String, body: String): PrivateMessageDto {
        val text = body.trim()
        require(text.isNotEmpty()) { "Private message body is blank" }
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        val request = SendPrivateMessageRequestDto(id = UUID.randomUUID().toString(), body = text)
        return normalizeAttachments(api.sendPrivateMessage(requireFamily(), conversationId, request))
    }

    suspend fun sendImages(
        conversationId: String,
        body: String?,
        images: List<Pair<ByteArray, String>>
    ): PrivateMessageDto {
        val text = body?.trim().orEmpty()
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        require(images.isNotEmpty()) { "At least one private image is required" }

        val parts = images.mapIndexed { index, (bytes, contentType) ->
            require(bytes.isNotEmpty()) { "Private image is empty" }
            val safeContentType = normalizeImageContentType(contentType)
            MultipartBody.Part.createFormData(
                "files",
                "dm-image-${index + 1}${extensionFor(safeContentType)}",
                bytes.toRequestBody(safeContentType.toMediaType())
            )
        }
        val id = UUID.randomUUID().toString().toRequestBody(TEXT_PLAIN)
        val bodyPart = text.takeIf { it.isNotEmpty() }?.toRequestBody(TEXT_PLAIN)
        return normalizeAttachments(
            api.sendPrivateImageMessage(requireFamily(), conversationId, id, bodyPart, parts)
        )
    }

    suspend fun edit(conversationId: String, messageId: String, body: String): PrivateMessageDto {
        val text = body.trim()
        require(text.isNotEmpty()) { "Private message body is blank" }
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        return normalizeAttachments(
            api.editPrivateMessage(requireFamily(), conversationId, messageId, EditPrivateMessageRequestDto(text))
        )
    }

    suspend fun delete(conversationId: String, messageId: String): PrivateMessageDto =
        normalizeAttachments(api.deletePrivateMessage(requireFamily(), conversationId, messageId))

    suspend fun clear(conversationId: String) {
        api.clearPrivateConversation(requireFamily(), conversationId)
    }

    suspend fun export(conversationId: String): PrivateMessageExportDto {
        val export = api.exportPrivateConversation(requireFamily(), conversationId)
        return export.copy(messages = export.messages.map(::normalizeAttachments))
    }

    private fun requireFamily(): String =
        sessionStore.familyId ?: throw IllegalStateException("No family in session")

    /** Mismo criterio que ChatRepository.normalizeAttachments, para las rutas de DM. */
    fun normalizeAttachments(message: PrivateMessageDto): PrivateMessageDto {
        val attachments = message.attachments
        if (attachments.isNullOrEmpty()) return message
        return message.copy(
            attachments = attachments.map { attachment ->
                attachment.copy(
                    url = rewriteUploadUrl(attachment.url),
                    thumbnailUrl = attachment.thumbnailUrl?.let(::rewriteUploadUrl)
                )
            }
        )
    }

    private fun rewriteUploadUrl(raw: String): String {
        val path = uploadPathOrNull(raw) ?: return raw
        return baseUrlProvider().trimEnd('/') + path
    }

    private fun uploadPathOrNull(raw: String): String? {
        val path = try {
            java.net.URI(raw).path
        } catch (e: Exception) {
            return null
        }
        if (path.isNullOrBlank() || path.contains("..")) return null
        val allowed = path.startsWith("/uploads/dm/") || path.startsWith("/uploads/dm_thumbnails/")
        return if (allowed) path else null
    }

    private fun normalizeImageContentType(contentType: String): String {
        val normalized = contentType.lowercase().trim()
        require(normalized in ALLOWED_IMAGE_TYPES) { "Unsupported private image type" }
        return normalized
    }

    private fun extensionFor(contentType: String): String = when (contentType) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ".jpg"
    }

    companion object {
        const val PAGE_SIZE = 30
        private val TEXT_PLAIN = "text/plain".toMediaType()
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.PrivateChatRepositoryTest"`
Expected: `10 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.kt android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryTest.kt
git commit -m "feat(android): PrivateChatRepository - REST completo con tests"
```

---

### Task 4: Extender `ChatSocket` — topic de inbox y conversacion opcional

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt`
- Modify: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt`

**Context (current code):** `ChatSocket`'s constructor takes `(httpClient, baseUrl, sessionStore, familyId, gson, onMessage, onConnectionChange, onPresenceUpdate)`. Unlike Desktop, this class does **not** need dynamic subscribe/unsubscribe: Android already opens a fresh `ChatSocket` per use (`chatBadgeSocket` at login, a separate `chatSocket` per `ChatScreen` visit) rather than keeping one shared connection — so a private conversation gets its own fresh socket too, with `conversationId` fixed at construction. This task must not change the existing chat/presence behavior for the two current callers (`startChatBadge()`, `openChat()`), since they'll keep calling `ChatRepository.openRealtime(...)` with new parameters defaulted to no-ops (Task 5).

- [ ] **Step 1: Add failing routing tests** (append to `ChatSocketFrameParsingTest.kt`, before the closing `}`):

```kotlin

    @Test
    fun extractsDestinationFromInboxFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/users/u1/inbox\n" +
            "subscription:sub-inbox\n" +
            "\n" +
            "{\"conversationId\":\"c1\",\"senderUserId\":\"u2\",\"sentAt\":\"2026-07-23T10:00:00Z\"}"

        assertEquals("/topic/users/u1/inbox", extractStompHeader(frame, "destination"))
    }

    @Test
    fun extractsDestinationFromConversationFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/conversations/c1\n" +
            "subscription:sub-conversation\n" +
            "\n" +
            "{\"id\":\"m1\"}"

        assertEquals("/topic/conversations/c1", extractStompHeader(frame, "destination"))
    }
```

These exercise the already-tested `extractStompHeader` top-level function against the new topic shapes — pass immediately, document the frame format Step 3 depends on.

- [ ] **Step 2: Run to verify they pass already**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.ChatSocketFrameParsingTest"`
Expected: `5 tests completed, 0 failed` (3 existing + 2 new)

- [ ] **Step 3: Extend `ChatSocket.kt`**

Replace the constructor and field block (lines 26-39):

```kotlin
class ChatSocket(
    private val httpClient: OkHttpClient,
    baseUrl: String,
    private val sessionStore: SessionStore,
    private val familyId: String,
    private val myUserId: String,
    private val gson: Gson,
    private val onMessage: (ChatMessageDto) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onPresenceUpdate: (Set<String>) -> Unit,
    private val conversationId: String? = null,
    private val onInboxPing: (PrivateInboxPingDto) -> Unit = {},
    private val onPrivateMessage: (PrivateMessageDto) -> Unit = {}
) {

    private val wsUrl: String = toWebSocketUrl(baseUrl)
    private val topic: String = "/topic/families/$familyId/chat"
    private val presenceTopic: String = "/topic/families/$familyId/presence"
    private val inboxTopic: String = "/topic/users/$myUserId/inbox"
    private val conversationTopic: String? = conversationId?.let { "/topic/conversations/$it" }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable {
        if (!closedByClient) {
            connect()
        }
    }
```

Add the 2 new DTO imports at the top of the file (alongside the existing `dto.ChatMessageDto`/`dto.PresenceResponseDto` imports):

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
```

Modify the `CONNECTED` case inside `handleFrame` (replace lines 128-143):

```kotlin
            "CONNECTED" -> {
                val subscribeChat = "SUBSCRIBE\n" +
                    "id:sub-chat\n" +
                    "destination:$topic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeChat)
                val subscribePresence = "SUBSCRIBE\n" +
                    "id:sub-presence\n" +
                    "destination:$presenceTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribePresence)
                val subscribeInbox = "SUBSCRIBE\n" +
                    "id:sub-inbox\n" +
                    "destination:$inboxTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeInbox)
                conversationTopic?.let { topic ->
                    val subscribeConversation = "SUBSCRIBE\n" +
                        "id:sub-conversation\n" +
                        "destination:$topic\n" +
                        "\n" +
                        NUL
                    webSocket.send(subscribeConversation)
                }
                reconnectAttempt = 0
                onConnectionChange(true)
            }
```

Modify the `MESSAGE` case (replace lines 144-154):

```kotlin
            "MESSAGE" -> {
                val destination = extractStompHeader(frame, "destination")
                val body = frame.substringAfter("\n\n", "").trim()
                if (body.isEmpty()) {
                    // no-op
                } else if (destination == presenceTopic) {
                    handlePresenceMessage(body)
                } else if (destination == inboxTopic) {
                    handleInboxPing(body)
                } else if (destination != null && destination == conversationTopic) {
                    handlePrivateMessage(body)
                } else if (destination == topic) {
                    handleChatMessage(body)
                }
            }
```

Add the 2 new handlers (near `handlePresenceMessage`):

```kotlin
    private fun handleInboxPing(body: String) {
        runCatching { gson.fromJson(body, PrivateInboxPingDto::class.java) }
            .getOrNull()
            ?.let(onInboxPing)
    }

    private fun handlePrivateMessage(body: String) {
        runCatching { gson.fromJson(body, PrivateMessageDto::class.java) }
            .getOrNull()
            ?.takeIf { it.id.isNotBlank() && it.conversationId == conversationId && it.authorUserId.isNotBlank() }
            ?.let(onPrivateMessage)
    }
```

- [ ] **Step 4: Compile** (this WILL fail — `ChatRepository.kt`'s call to `ChatSocket(...)` doesn't pass `myUserId` yet, since that constructor param has no default. This is expected and fixed in Task 5, a coupled task committed together with this one.)

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: exactly one error, in `ChatRepository.kt`'s `openRealtime` method — missing `myUserId` argument.

- [ ] **Step 5: Do NOT commit yet** — leave staged/unstaged, Task 5 commits both together.

---

### Task 5: `ChatRepository` — inbox y conversacion opcional en `openRealtime`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt`

Kotlin default parameters make this a **non-breaking** change to `openRealtime`'s public signature: the two existing call sites (`RecetasViewModel.startChatBadge()`, `RecetasViewModel.openChat()`) keep compiling unchanged, since the 3 new parameters all have defaults.

- [ ] **Step 1: Add the 2 new DTO imports** (alongside the existing `dto.ChatMessageDto` etc.):

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
```

- [ ] **Step 2: Replace `openRealtime`'s signature and body** (lines 103-121):

```kotlin
    fun openRealtime(
        onMessage: (ChatMessageDto) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onPresenceUpdate: (Set<String>) -> Unit,
        conversationId: String? = null,
        onInboxPing: (PrivateInboxPingDto) -> Unit = {},
        onPrivateMessage: (PrivateMessageDto) -> Unit = {}
    ): ChatSocket? {
        val family = familyId ?: return null
        val user = myUserId ?: return null
        val socket = ChatSocket(
            httpClient = httpClient,
            baseUrl = baseUrlProvider(),
            sessionStore = sessionStore,
            familyId = family,
            myUserId = user,
            gson = gson,
            onMessage = { msg -> onMessage(normalizeAttachments(msg)) },
            onConnectionChange = onConnectionChange,
            onPresenceUpdate = onPresenceUpdate,
            conversationId = conversationId,
            onInboxPing = onInboxPing,
            onPrivateMessage = { msg -> onPrivateMessage(normalizePrivateMessage(msg)) }
        )
        socket.connect()
        return socket
    }

    /** Mismo criterio que normalizeAttachments, para adjuntos de chat privado (rutas /uploads/dm/). */
    private fun normalizePrivateMessage(message: PrivateMessageDto): PrivateMessageDto {
        val attachments = message.attachments
        if (attachments.isNullOrEmpty()) return message
        return message.copy(
            attachments = attachments.map { attachment ->
                attachment.copy(
                    url = rewritePrivateUploadUrl(attachment.url),
                    thumbnailUrl = attachment.thumbnailUrl?.let(::rewritePrivateUploadUrl)
                )
            }
        )
    }

    private fun rewritePrivateUploadUrl(raw: String): String {
        val path = try {
            java.net.URI(raw).path
        } catch (e: Exception) {
            return raw
        }
        if (path.isNullOrBlank() || path.contains("..")) return raw
        val allowed = path.startsWith("/uploads/dm/") || path.startsWith("/uploads/dm_thumbnails/")
        return if (allowed) baseUrlProvider().trimEnd('/') + path else raw
    }
```

## Your Job

Read the current `ChatRepository.kt` first to confirm `familyId`/`myUserId` are exactly as described (they're already-existing `get()` properties reading `sessionStore.familyId`/`sessionStore.userId`, per Task 3's context) before editing — don't assume, verify.

- [ ] **Step 3: Compile and run the full Android unit test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (confirms Task 4's `ChatSocket` extension didn't regress family chat/presence/badge).

- [ ] **Step 4: Commit** (Tasks 4 + 5 together, since the module only compiles once both land)

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt
git commit -m "feat(android): extiende ChatSocket/ChatRepository con inbox y conversacion privada"
```

---

### Task 6: Cablear `PrivateChatRepository` en `AppContainer`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContainer.kt`

- [ ] **Step 1: Add the import and the wiring line**

Add import (alongside `data.repository.ChatRepository`):

```kotlin
import org.gipsybuho.recetasfamiliares.data.repository.PrivateChatRepository
```

Add the instance (right after `val chatRepository = ChatRepository(api, httpClient, sessionStore, baseUrlProvider)`, line 92):

```kotlin
    val privateChatRepository = PrivateChatRepository(api, sessionStore, baseUrlProvider)
```

- [ ] **Step 2: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContainer.kt
git commit -m "feat(android): expone PrivateChatRepository desde AppContainer"
```

---

### Task 7: Boton "Mensaje" en `FamilyMembersSection`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt`

`FamilyMembersSection` is already visible to all roles (no visibility fix needed, unlike Desktop). Add a "Mensaje" `IconButton` visible whenever `member.userId != myUserId`, independent of `canManage` (which stays admin-gated for edit/role/remove).

- [ ] **Step 1: Add the new callback parameter to `FamilyMembersSection`'s signature** (lines 756-764):

```kotlin
@Composable
private fun FamilyMembersSection(
    members: List<FamilyMemberDto>,
    onlineUserIds: Set<String>,
    isAdmin: Boolean,
    myUserId: String?,
    onChangeRole: (FamilyMemberDto, String) -> Unit,
    onEditMember: (FamilyMemberDto) -> Unit,
    onRemoveMember: (FamilyMemberDto) -> Unit,
    onMessageMember: (FamilyMemberDto) -> Unit
) {
```

- [ ] **Step 2: Add the "Mensaje" `IconButton`** in the member row, right after the role `Text` (line 830-834) and before the `if (canManage)` block (line 835):

```kotlin
                    if (member.userId != myUserId) {
                        IconButton(onClick = { onMessageMember(member) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = "Enviar mensaje privado a ${member.displayName}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
```

Add the import for the icon (alongside the existing `Icons.Filled.*`/`Icons.AutoMirrored.Filled.*` imports at the top of the file):

```kotlin
import androidx.compose.material.icons.automirrored.filled.Message
```

- [ ] **Step 3: Update the call site** (lines 426-440, where `FamilyMembersSection` is invoked from the main `ProfileScreen` composable) — add the new parameter:

```kotlin
        val familyMembers by viewModel.familyMembers.collectAsState()
        val onlineUserIds by viewModel.onlineUserIds.collectAsState()
        if (familyMembers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            FamilyMembersSection(
                members = familyMembers,
                onlineUserIds = onlineUserIds,
                isAdmin = isAdmin,
                myUserId = myUserId,
                onChangeRole = { member, newRole ->
                    memberRoleChange = MemberRoleChange(member, newRole)
                },
                onEditMember = { member ->
                    memberToEdit = member
                },
                onRemoveMember = { member ->
                    memberToRemove = member
                },
                onMessageMember = { member ->
                    onMessageMember(member)
                }
            )
        }
```

**Note:** `onMessageMember` here refers to a new parameter on the top-level `ProfileScreen` composable itself (added in Task 9, when `RecetasApp.kt` is wired) — Task 7 only wires the plumbing inside `ProfileScreen.kt`; the actual callback logic (create/get conversation, open `ConversationsScreen`) is added to `ProfileScreen`'s own signature and to `RecetasApp.kt`'s call site in Task 9. Until Task 9 lands, `ProfileScreen`'s compiler will show one error ("unresolved reference: onMessageMember") — expected, both tasks are committed together per Step 5 below.

- [ ] **Step 4: Do NOT compile/commit standalone** — this task's change alone does not compile (needs `ProfileScreen`'s own new parameter from Task 9). Proceed directly to Task 9's implementer, who compiles and commits both together. (If executing tasks in strict isolation via subagent-driven-development, treat Tasks 7+9 as one unit — dispatch them to the same implementer in sequence before compiling.)

---

### Task 8: `RecetasViewModel` — estado de bandeja, no-leidos y conversacion abierta

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt`

Mirrors the existing chat family state exactly (`_chatMessages`, `_chatConnected`, `_chatLoading`, `_chatHasMoreOlder`, `chatSocket`, `startChatBadge()`/`stopChatBadge()`, `openChat()`/`closeChat()`), scoped per-conversation where needed.

- [ ] **Step 1: Add the imports** (alongside the existing `data.remote.dto.ChatMessageDto` etc. imports):

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.repository.PRIVATE_CHAT_MAX_BODY_LENGTH
```

- [ ] **Step 2: Add the state fields**, right after the existing chat-badge block (after line 912, the `chatBadgeSeenIds` declaration, before `fun startChatBadge()`):

```kotlin

    // ── Chat privado 1:1 ─────────────────────────────────────────────────────

    private val _conversations = MutableStateFlow<List<PrivateConversationDto>>(emptyList())
    val conversations: StateFlow<List<PrivateConversationDto>> = _conversations.asStateFlow()

    private val _privateChatUnread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val privateChatUnread: StateFlow<Map<String, Int>> = _privateChatUnread.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<PrivateMessageDto>>(emptyList())
    val privateMessages: StateFlow<List<PrivateMessageDto>> = _privateMessages.asStateFlow()

    private val _privateChatConnected = MutableStateFlow(false)
    val privateChatConnected: StateFlow<Boolean> = _privateChatConnected.asStateFlow()

    private val _privateChatLoading = MutableStateFlow(false)
    val privateChatLoading: StateFlow<Boolean> = _privateChatLoading.asStateFlow()

    private val _privateChatHasMoreOlder = MutableStateFlow(false)
    val privateChatHasMoreOlder: StateFlow<Boolean> = _privateChatHasMoreOlder.asStateFlow()

    private var privateChatOldestCursor: String? = null
    private var privateChatSocket: ChatSocket? = null
    private var activePrivateConversationId: String? = null
```

- [ ] **Step 3: Extend `startChatBadge()`** to also wire the inbox ping (replace the existing method, lines 918-933):

```kotlin
    fun startChatBadge() {
        if (chatBadgeSocket != null || !_isLoggedIn.value) return
        chatBadgeSocket = container.chatRepository.openRealtime(
            onMessage = { msg ->
                val firstTime = chatBadgeSeenIds.add(msg.id)
                val fromOther = msg.authorUserId != null && msg.authorUserId != myUserId
                if (firstTime && !chatScreenOpen && fromOther && !msg.deleted) {
                    _chatUnread.update { it + 1 }
                }
            },
            onConnectionChange = {},
            onPresenceUpdate = { online -> _onlineUserIds.value = online },
            onInboxPing = { ping -> handlePrivateInboxPing(ping) }
        )
    }

    private fun handlePrivateInboxPing(ping: PrivateInboxPingDto) {
        if (ping.conversationId == activePrivateConversationId) return
        _privateChatUnread.update { current ->
            current + (ping.conversationId to ((current[ping.conversationId] ?: 0) + 1))
        }
    }
```

- [ ] **Step 4: Extend `stopChatBadge()`** to also clear private-chat unread state (replace lines 935-940):

```kotlin
    fun stopChatBadge() {
        chatBadgeSocket?.disconnect()
        chatBadgeSocket = null
        chatBadgeSeenIds.clear()
        _chatUnread.value = 0
        _privateChatUnread.value = emptyMap()
        _conversations.value = emptyList()
    }
```

- [ ] **Step 5: Add the conversation-list and private-chat methods**, right after `stopChatBadge()`:

```kotlin

    /** Bandeja de conversaciones privadas del usuario en la familia activa. */
    fun loadConversations() {
        viewModelScope.launch {
            runCatching { container.privateChatRepository.listConversations() }
                .onSuccess { _conversations.value = it }
                .onFailure { _userMessage.emit("No se pudieron cargar las conversaciones") }
        }
    }

    /** Crea o recupera la conversacion con otro miembro. Llamado desde el boton Mensaje en Miembros. */
    fun createOrGetConversation(otherUserId: String, onResult: (PrivateConversationDto) -> Unit) {
        viewModelScope.launch {
            runCatching { container.privateChatRepository.createOrGetConversation(otherUserId) }
                .onSuccess { conversation ->
                    loadConversations()
                    onResult(conversation)
                }
                .onFailure { _userMessage.emit("No se pudo abrir la conversacion") }
        }
    }

    /** Marca una conversacion como leida (limpia su contador) al abrirla, sin conectar el socket todavia. */
    fun markConversationRead(conversationId: String) {
        _privateChatUnread.update { it - conversationId }
    }

    /** Abre una conversacion: conexion propia y efimera (a diferencia del chat familiar, sin polling de respaldo). */
    fun openPrivateChat(conversationId: String) {
        activePrivateConversationId = conversationId
        markConversationRead(conversationId)
        _privateMessages.value = emptyList()
        _privateChatLoading.value = true
        viewModelScope.launch {
            runCatching { container.privateChatRepository.loadHistory(conversationId) }
                .onSuccess { history ->
                    _privateMessages.value = history.items.sortedBy { it.createdAt }
                    _privateChatHasMoreOlder.value = history.hasMore
                    privateChatOldestCursor = history.nextBefore
                }
                .onFailure { _userMessage.emit("No se pudo cargar la conversacion") }
            _privateChatLoading.value = false
        }
        privateChatSocket = container.chatRepository.openRealtime(
            onMessage = {},
            onConnectionChange = { connected -> _privateChatConnected.value = connected },
            onPresenceUpdate = {},
            conversationId = conversationId,
            onPrivateMessage = { msg -> _privateMessages.update { mergePrivateMessages(it, listOf(msg)) } }
        )
    }

    fun closePrivateChat() {
        activePrivateConversationId = null
        privateChatSocket?.disconnect()
        privateChatSocket = null
        _privateChatConnected.value = false
    }

    fun loadOlderPrivateChat() {
        val conversationId = activePrivateConversationId ?: return
        val before = privateChatOldestCursor ?: return
        if (_privateChatLoading.value) return
        _privateChatLoading.value = true
        viewModelScope.launch {
            runCatching { container.privateChatRepository.loadHistory(conversationId, before) }
                .onSuccess { history ->
                    _privateMessages.update { mergePrivateMessages(history.items, it) }
                    _privateChatHasMoreOlder.value = history.hasMore
                    privateChatOldestCursor = history.nextBefore
                }
                .onFailure { _userMessage.emit("No se pudieron cargar mensajes anteriores") }
            _privateChatLoading.value = false
        }
    }

    fun sendPrivateMessage(body: String, onSent: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.privateChatRepository.send(conversationId, text) }
                .onSuccess { msg ->
                    _privateMessages.update { mergePrivateMessages(it, listOf(msg)) }
                    onSent()
                }
                .onFailure { _userMessage.emit("No se pudo enviar el mensaje") }
        }
    }

    fun sendPrivateImage(context: Context, uri: Uri, caption: String, onSent: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = caption.trim()
        if (!_privateChatConnected.value) {
            viewModelScope.launch { _userMessage.emit("Sin conexión en tiempo real") }
            return
        }
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val image = compressImage(context, uri)
                    container.privateChatRepository.sendImages(conversationId, text, listOf(image))
                }
            }.onSuccess { msg ->
                _privateMessages.update { mergePrivateMessages(it, listOf(msg)) }
                onSent()
            }.onFailure {
                _userMessage.emit("No se pudo enviar la imagen")
            }
        }
    }

    fun editPrivateMessage(message: PrivateMessageDto, body: String, onDone: () -> Unit = {}) {
        val conversationId = activePrivateConversationId ?: return
        val text = body.trim()
        if (message.deleted || message.authorUserId != myUserId) return
        if (text.isEmpty()) return
        if (text.length > PRIVATE_CHAT_MAX_BODY_LENGTH) {
            viewModelScope.launch { _userMessage.emit("El mensaje no puede superar $PRIVATE_CHAT_MAX_BODY_LENGTH caracteres") }
            return
        }
        viewModelScope.launch {
            runCatching { container.privateChatRepository.edit(conversationId, message.id, text) }
                .onSuccess { updated ->
                    _privateMessages.update { mergePrivateMessages(it, listOf(updated)) }
                    onDone()
                }
                .onFailure { _userMessage.emit("No se pudo editar el mensaje") }
        }
    }

    fun deletePrivateMessage(message: PrivateMessageDto) {
        val conversationId = activePrivateConversationId ?: return
        if (message.authorUserId != myUserId || message.deleted) return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.delete(conversationId, message.id) }
                .onSuccess { updated -> _privateMessages.update { mergePrivateMessages(it, listOf(updated)) } }
                .onFailure { _userMessage.emit("No se pudo eliminar el mensaje") }
        }
    }

    fun clearPrivateChat() {
        val conversationId = activePrivateConversationId ?: return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.clear(conversationId) }
                .onSuccess { _privateMessages.value = emptyList() }
                .onFailure { _userMessage.emit("No se pudo borrar la conversacion") }
        }
    }

    fun exportPrivateChat(onExported: (String) -> Unit) {
        val conversationId = activePrivateConversationId ?: return
        viewModelScope.launch {
            runCatching { container.privateChatRepository.export(conversationId) }
                .onSuccess { export -> onExported(buildPrivateChatExportText(export)) }
                .onFailure { _userMessage.emit("No se pudo exportar la conversacion") }
        }
    }

    private fun mergePrivateMessages(
        base: List<PrivateMessageDto>,
        incoming: List<PrivateMessageDto>
    ): List<PrivateMessageDto> {
        val byId = base.associateBy { it.id }.toMutableMap()
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.createdAt }
    }

    private fun buildPrivateChatExportText(export: org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto): String {
        val builder = StringBuilder("Conversacion privada - export\n\n")
        export.messages.forEach { message ->
            val attachments = message.attachments.orEmpty()
            val body = message.body ?: if (attachments.isEmpty()) "(mensaje eliminado)" else ""
            builder.append("[").append(message.createdAt).append("] ")
                .append(message.authorDisplayName).append(": ").append(body)
            if (attachments.isNotEmpty()) {
                if (body.isNotBlank()) builder.append(' ')
                builder.append('[').append(attachments.size)
                    .append(if (attachments.size == 1) " imagen]" else " imagenes]")
            }
            builder.append('\n')
        }
        return builder.toString()
    }
```

**Note:** `mergePrivateMessages` mirrors the existing `mergeChat` helper already used by `_chatMessages` — check its exact current implementation before assuming the signature matches; if `mergeChat` has different dedup/sort logic, follow that one instead of inventing a new merge strategy.

- [ ] **Step 6: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (Task 7's `ProfileScreen.kt` reference to `onMessageMember` is still unresolved until Task 9 — that's a separate file's error, not this one; this step only needs `RecetasViewModel.kt` itself to compile in isolation, which it does since it doesn't reference `ProfileScreen`).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt
git commit -m "feat(android): estado de chat privado en RecetasViewModel"
```

---

### Task 9: `RecetasApp` — icono en TopAppBar, `ConversationsScreen` y wiring de `ProfileScreen`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt` (finishes Task 7's dangling reference)

This is the task that finally makes Task 7 compile — both are committed together.

- [ ] **Step 1: Find `ProfileScreen`'s own top-level composable signature** (search for `fun ProfileScreen(` in `ProfileScreen.kt`) and add a new parameter `onMessageMember: (FamilyMemberDto) -> Unit` to it, threading it down to the `FamilyMembersSection` call added in Task 7 Step 3 (already written assuming this parameter exists).

- [ ] **Step 2: Add state to `MainShell`** (`RecetasApp.kt`, alongside `chatOpen` at line 339):

```kotlin
    var conversationsOpen by remember { mutableStateOf(false) }
    var initialConversation by remember { mutableStateOf<org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto?>(null) }
    val privateChatUnread by viewModel.privateChatUnread.collectAsState()
```

- [ ] **Step 3: Add the early-return branch** for `ConversationsScreen`, right after the existing `chatOpen` branch (lines 372-375):

```kotlin
    if (conversationsOpen) {
        ConversationsScreen(
            viewModel = viewModel,
            initialConversation = initialConversation,
            onClose = { conversationsOpen = false; initialConversation = null }
        )
        return
    }
```

- [ ] **Step 4: Add the TopAppBar icon**, right after the existing chat-familiar `TooltipBox` block (after line 452, before the "Buscar" `TooltipBox` at line 453):

```kotlin
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Chat privado") } },
                            state = rememberTooltipState()
                        ) {
                            val privateUnreadTotal = privateChatUnread.values.sum()
                            IconButton(onClick = { conversationsOpen = true }) {
                                BadgedBox(
                                    badge = {
                                        if (privateUnreadTotal > 0) {
                                            Badge {
                                                Text(if (privateUnreadTotal > 9) "9+" else "$privateUnreadTotal")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = if (privateUnreadTotal > 0) {
                                            "Chat privado, $privateUnreadTotal mensajes nuevos"
                                        } else {
                                            "Chat privado"
                                        }
                                    )
                                }
                            }
                        }
```

Add the icon import (alongside the existing `Icons.Outlined.ChatBubbleOutline` import):

```kotlin
import androidx.compose.material.icons.filled.Lock
```

- [ ] **Step 5: Wire `ProfileScreen`'s call site** (find where `ProfileScreen(...)` is invoked, inside `MainTab.PROFILE`'s branch of `MainShell`'s content) — add the new parameter:

```kotlin
                onMessageMember = { member ->
                    viewModel.createOrGetConversation(member.userId) { conversation ->
                        initialConversation = conversation
                        conversationsOpen = true
                    }
                }
```

- [ ] **Step 6: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: one remaining error — `ConversationsScreen` doesn't exist yet (Task 10, next). Confirm this is the ONLY error.

- [ ] **Step 7: Do NOT commit yet** — `ConversationsScreen`/`PrivateChatScreen` (Tasks 10-11) are needed for this to compile; commit all of Tasks 9-11 together once the module builds clean.

---

### Task 10: `ConversationsScreen` — bandeja de conversaciones

**Files:**
- Create: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsScreen.kt`

Mirrors `ChatScreen.kt`'s shell (own `Scaffold`, own `SnackbarHost`, `TopAppBar` with back arrow). Internally alternates between a list (via `LazyColumn`) and the embedded `PrivateChatScreen` detail, using local `remember { mutableStateOf(...) }` state — no navigation library involved, matching the rest of this app.

- [ ] **Step 1: Write the file**

```kotlin
package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: RecetasViewModel,
    initialConversation: PrivateConversationDto? = null,
    onClose: () -> Unit
) {
    var selectedConversation by remember(initialConversation) { mutableStateOf(initialConversation) }
    val conversations by viewModel.conversations.collectAsState()
    val unreadByConversation by viewModel.privateChatUnread.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadConversations()
    }

    val current = selectedConversation
    if (current != null) {
        PrivateChatScreen(
            viewModel = viewModel,
            conversation = current,
            onClose = { selectedConversation = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar conversaciones")
                    }
                },
                title = { Text("Conversaciones") }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Lock,
                title = "Sin conversaciones todavía",
                subtitle = "Escribe a un miembro de la familia desde Perfil para empezar una conversación privada.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { it.conversationId }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        unreadCount = unreadByConversation[conversation.conversationId] ?: 0,
                        onClick = { selectedConversation = conversation }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: PrivateConversationDto,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(end = Spacing.md)) {
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!conversation.otherUserAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = conversation.otherUserAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        conversation.otherUserDisplayName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherUserDisplayName, style = MaterialTheme.typography.bodyLarge)
            conversation.lastMessagePreview?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (unreadCount > 0) {
            Badge { Text(if (unreadCount > 9) "9+" else "$unreadCount") }
        }
    }
}
```

**Note:** verify `EmptyStateView`'s exact parameter signature (used already by `ChatScreen.kt`) before assuming it accepts a `modifier` parameter — if it doesn't, drop that argument and wrap the call in a `Box(Modifier.padding(padding))` instead.

- [ ] **Step 2: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: one remaining error — `PrivateChatScreen` doesn't exist yet (Task 11, next).

- [ ] **Step 3: Do NOT commit yet** — same reason as Task 9.

---

### Task 11: `PrivateChatScreen` — panel de una conversacion

**Files:**
- Create: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatScreen.kt`

Mirrors `ChatScreen.kt` closely (historial, envío texto/imagen, editar/borrar propio, exportar, borrar-para-mí, visor de adjuntos) — same `MessageBubble`/`ChatAttachmentImage`/`ChatImageViewer`/`ChatInputBar` composables already exist as private functions in `ChatScreen.kt`; since they're `private fun` in that file, this new screen needs its own copies scoped to `PrivateMessageDto`/`PrivateAttachmentDto` (small, bounded duplication — same criterion already accepted for the backend/Desktop chat privado work).

- [ ] **Step 1: Write the file**

```kotlin
package org.gipsybuho.recetasfamiliares.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateAttachmentDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.repository.PRIVATE_CHAT_MAX_BODY_LENGTH
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    viewModel: RecetasViewModel,
    conversation: PrivateConversationDto,
    onClose: () -> Unit
) {
    val messages by viewModel.privateMessages.collectAsState()
    val connected by viewModel.privateChatConnected.collectAsState()
    val loading by viewModel.privateChatLoading.collectAsState()
    val hasMoreOlder by viewModel.privateChatHasMoreOlder.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val myUserId = viewModel.myUserId

    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<PrivateMessageDto?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<PrivateMessageDto?>(null) }
    var viewingAttachment by remember { mutableStateOf<PrivateAttachmentDto?>(null) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val caption = draft.trim()
            viewModel.sendPrivateImage(context, uri, caption) {
                if (draft.trim() == caption) draft = ""
            }
        }
    }

    DisposableEffect(conversation.conversationId) {
        viewModel.openPrivateChat(conversation.conversationId)
        onDispose { viewModel.closePrivateChat() }
    }

    var lastId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull()
        if (newest != null && newest.id != lastId) {
            val previousLastId = lastId
            lastId = newest.id
            val visibleLastIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isInitialLoad = previousLastId == null
            val isNearBottom = visibleLastIndex >= messages.lastIndex - 2
            val isOwnMessage = newest.authorUserId == myUserId
            if (isInitialLoad || isNearBottom || isOwnMessage) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, hasMoreOlder) {
        if (hasMoreOlder && listState.firstVisibleItemIndex == 0) {
            viewModel.loadOlderPrivateChat()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar conversacion")
                    }
                },
                title = {
                    Column {
                        Text(conversation.otherUserDisplayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (connected) "En línea" else "Sin conexión en tiempo real",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar conversación") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportPrivateChat { text -> shareChatText(context, text) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar chat para mí") },
                            onClick = {
                                menuOpen = false
                                confirmClear = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    messages.isEmpty() -> {
                        EmptyStateView(
                            icon = Icons.Outlined.Lock,
                            title = "Escribe el primer mensaje",
                            subtitle = "Empieza la conversación con ${conversation.otherUserDisplayName}."
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                PrivateMessageBubble(
                                    message = message,
                                    isMine = message.authorUserId == myUserId,
                                    onEdit = { selected -> editDraft = selected.body.orEmpty(); editingMessage = selected },
                                    onDelete = { selected -> deletingMessage = selected },
                                    onImageClick = { attachment -> viewingAttachment = attachment }
                                )
                            }
                        }
                    }
                }
            }

            PrivateChatInputBar(
                draft = draft,
                connected = connected,
                onDraftChange = { draft = it.take(PRIVATE_CHAT_MAX_BODY_LENGTH) },
                onPickImage = { imagePicker.launch("image/*") },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendPrivateMessage(text) {
                            if (draft.trim() == text) draft = ""
                        }
                    }
                }
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar chat para ti") },
            text = { Text("Se ocultará tu historial de esta conversación. El otro participante conserva el suyo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.clearPrivateChat()
                    confirmClear = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }

    editingMessage?.let { message ->
        val text = editDraft.trim()
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Editar mensaje") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it.take(PRIVATE_CHAT_MAX_BODY_LENGTH) },
                    supportingText = { Text("${editDraft.length}/$PRIVATE_CHAT_MAX_BODY_LENGTH") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotEmpty() && text != message.body.orEmpty(),
                    onClick = { viewModel.editPrivateMessage(message, text) { editingMessage = null } }
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingMessage = null }) { Text("Cancelar") } }
        )
    }

    deletingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("Eliminar mensaje") },
            text = { Text("Se mostrará como mensaje eliminado.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deletePrivateMessage(message)
                    deletingMessage = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deletingMessage = null }) { Text("Cancelar") } }
        )
    }

    viewingAttachment?.let { attachment ->
        PrivateChatImageViewer(
            attachment = attachment,
            onDismiss = { viewingAttachment = null },
            onSave = { viewModel.saveChatImageToGallery(context, attachment.url) }
        )
    }
}

@Composable
private fun PrivateMessageBubble(
    message: PrivateMessageDto,
    isMine: Boolean,
    onEdit: (PrivateMessageDto) -> Unit,
    onDelete: (PrivateMessageDto) -> Unit,
    onImageClick: (PrivateAttachmentDto) -> Unit
) {
    var actionsOpen by remember(message.id) { mutableStateOf(false) }
    val time = formatTime(message.createdAt)
    val attachments = message.attachments.orEmpty()
    val bodyText = message.body?.takeIf { it.isNotBlank() }
    val deletedText = if (message.deleted) "(mensaje eliminado)" else null
    val canDelete = isMine && !message.deleted
    val canEdit = canDelete && bodyText != null
    val speaker = if (isMine) "Tú" else message.authorDisplayName
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "$speaker, $time" },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                if (canDelete) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = { actionsOpen = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones del mensaje")
                        }
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            if (canEdit) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { actionsOpen = false; onEdit(message) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { actionsOpen = false; onDelete(message) }
                            )
                        }
                    }
                }
                attachments.forEachIndexed { index, attachment ->
                    if (index > 0) Spacer(Modifier.height(Spacing.sm))
                    PrivateChatAttachmentImage(attachment, onClick = { onImageClick(attachment) })
                }
                val visibleText = deletedText ?: bodyText
                if (visibleText != null) {
                    if (attachments.isNotEmpty()) Spacer(Modifier.height(Spacing.sm))
                    Text(
                        visibleText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal
                    )
                }
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PrivateChatAttachmentImage(attachment: PrivateAttachmentDto, onClick: () -> Unit) {
    SubcomposeAsyncImage(
        model = attachment.thumbnailUrl ?: attachment.url,
        contentDescription = "Imagen compartida. Toca para ampliar.",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        loading = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text("Imagen no disponible", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun PrivateChatImageViewer(attachment: PrivateAttachmentDto, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onSave() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            SubcomposeAsyncImage(
                model = attachment.url,
                contentDescription = "Imagen a tamaño completo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No se pudo cargar la imagen", color = Color.White) } }
            )
            Row(
                Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
                TextButton(onClick = {
                    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else onSave()
                }) { Text("Guardar", color = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateChatInputBar(
    draft: String,
    connected: Boolean,
    onDraftChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        val nearLimit = draft.length >= PRIVATE_CHAT_MAX_BODY_LENGTH - 160
        val canSend = connected && draft.isNotBlank() && draft.trim().length <= PRIVATE_CHAT_MAX_BODY_LENGTH
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onPickImage, enabled = connected) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = if (connected) "Añadir imagen" else "Sin conexión en tiempo real",
                        tint = if (connected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Mensaje…") },
                supportingText = if (nearLimit) { { Text("${draft.length}/$PRIVATE_CHAT_MAX_BODY_LENGTH") } } else null,
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Surface(
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (canSend) "Enviar mensaje" else if (!connected) "Sin conexión en tiempo real" else "Mensaje no listo para enviar",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val privateChatTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(privateChatTimeFormatter) }
        .getOrElse { iso }
```

**Note:** `shareChatText` and `saveChatImageToGallery` are existing helpers (`shareChatText` is a private top-level function in `ChatScreen.kt`; `saveChatImageToGallery` is a `RecetasViewModel` method) — verify both are visible/callable from this new file before assuming (top-level `private fun` in `ChatScreen.kt` is **not** visible outside that file in Kotlin; if `shareChatText` is `private`, either make it internal/public in `ChatScreen.kt`, or duplicate the small `Intent.ACTION_SEND` helper here — check first, don't guess).

- [ ] **Step 2: Fix the `shareChatText` visibility issue found in Step 1's note.** Read `ChatScreen.kt`'s exact declaration (`private fun shareChatText(context: Context, text: String)`, confirmed private at the file's end) and change it to `internal fun shareChatText(...)` in `ChatScreen.kt` (smallest possible visibility widening, package-internal, not duplicated code) rather than copying the function.

- [ ] **Step 3: Compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (this closes out Tasks 7, 9, 10, 11's dangling references).

- [ ] **Step 4: Run the full Android unit test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass, no regressions.

- [ ] **Step 5: Commit** (Tasks 7, 9, 10, 11 together, since the module only compiles once all four land)

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsScreen.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatScreen.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ChatScreen.kt
git commit -m "feat(android): ConversationsScreen, PrivateChatScreen, boton Mensaje e icono TopAppBar"
```

---

### Task 12: VibeSec sobre el diff completo

**Files:** ninguno (revision, no cambios de codigo salvo que aparezcan hallazgos)

- [ ] **Step 1: Invocar `/VibeSec`** sobre el diff completo de este plan (Tasks 1-11), con foco en:
  - El botón "Mensaje" nunca aparece en la fila propia (`member.userId != myUserId` guard, Task 7).
  - Las imágenes de chat privado se descargan igual de autenticadas que el chat familiar (Coil ya carga `/uploads/**` con el interceptor `AuthInterceptor` existente en `AppContainer.kt` — confirmar que no se introdujo ninguna ruta nueva sin ese interceptor).
  - El cliente no implementa lógica de autorización propia — todo el filtrado real (quién puede ver qué conversación) vive en el backend ya auditado.
  - `PrivateChatRepository`/`ChatRepository`'s URL rewriting (`uploadPathOrNull`) sigue el mismo allowlist restrictivo que el chat familiar (solo `/uploads/dm/` y `/uploads/dm_thumbnails/`, bloquea `..`).
- [ ] **Step 2: Corregir cualquier hallazgo Critical/Important antes de continuar.** Si no hay hallazgos, documentarlo explícitamente.

---

### Task 13: Validacion final

**Files:** ninguno

- [ ] **Step 1: Compilación completa**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Suite completa de tests unitarios**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 0 fallos nuevos frente al baseline previo a este plan.

- [ ] **Step 3: Build de debug completo**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Prueba manual — documentar como pendiente, no simularla**

Bloqueada para el agente en este entorno (requiere dos cuentas, dos dispositivos/emuladores e interacción táctil real). Pendiente de que el usuario:
- Abra el icono de chat privado en la `TopAppBar` y confirme que el badge y la bandeja funcionan.
- Pulse "Mensaje" en un miembro desde Perfil y confirme que abre directo a esa conversación.
- Envíe un mensaje y confirme que llega en vivo a la otra sesión sin recargar.
- Confirme que un tercer miembro no participante no ve la conversación en su propia bandeja.

- [ ] **Step 5: Actualizar `CONTINUAR.md`** con el cierre de este sprint (agente líder, skills usadas, seguridad ejecutada, archivos, validación, riesgo residual de la prueba manual pendiente), siguiendo el mismo patrón que el cierre del sprint de Desktop.
