package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FamilyViewModelTest {

    @Test
    fun `loadFamilies selects the family matching session familyId as active`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f2" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)

        viewModel.loadFamilies()
        advanceUntilIdle()

        assertEquals("f2", viewModel.activeFamily.value?.id)
        assertEquals(2, viewModel.families.value.size)
    }

    @Test
    fun `loadFamilies falls back to the first family when session familyId is stale`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "familia-ya-no-existe" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)

        viewModel.loadFamilies()
        advanceUntilIdle()

        assertEquals("f1", viewModel.activeFamily.value?.id)
    }

    @Test
    fun `switchActiveFamily rejects an id not present in loaded families`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f1" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)
        viewModel.loadFamilies()
        advanceUntilIdle()

        viewModel.switchActiveFamily("unknown")

        assertEquals("f1", session.familyId)
        assertEquals("No se pudo cambiar de familia", viewModel.errorMessage.value)
    }

    @Test
    fun `switchActiveFamily updates session and active family for a valid id`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f1" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)
        viewModel.loadFamilies()
        advanceUntilIdle()

        viewModel.switchActiveFamily("f2")

        assertEquals("f2", session.familyId)
        assertEquals("MEMBER", session.familyRole)
        assertEquals("f2", viewModel.activeFamily.value?.id)
        assertNull(viewModel.errorMessage.value)
    }
}
