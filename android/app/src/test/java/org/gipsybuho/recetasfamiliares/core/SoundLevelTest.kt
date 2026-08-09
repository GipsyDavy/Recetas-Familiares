package org.gipsybuho.recetasfamiliares.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLevelTest {

    @Test
    fun enSilencioNoSuenaNadaEnAbsoluto() {
        SoundEffect.entries.forEach {
            assertFalse("no debe sonar $it", SoundLevel.SILENCIO.allows(it))
        }
    }

    @Test
    fun conTodosSuenaTodo() {
        SoundEffect.entries.forEach {
            assertTrue("debe sonar $it", SoundLevel.TODOS.allows(it))
        }
    }

    @Test
    fun soloImportantesDejaPasarLoQueTieneConsecuencias() {
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.SUCCESS))
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.ERROR))
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.DELETE))
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.ALERT))
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.TIMER))
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.STEP))
    }

    @Test
    fun soloImportantesCallaElRuidoDeNavegacion() {
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.NAVIGATE))
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.TOGGLE))
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.OPEN))
    }

    @Test
    fun seLeeDeLaPreferenciaPorSuNombre() {
        assertEquals(SoundLevel.TODOS, SoundLevel.fromPreference("TODOS", SoundLevel.SILENCIO))
    }

    /** Una preferencia corrupta o de una version futura no debe romper la aplicacion. */
    @Test
    fun anteUnaPreferenciaIlegibleSeUsaElValorPorDefecto() {
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference("ruido", SoundLevel.SILENCIO))
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference(null, SoundLevel.SILENCIO))
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference("", SoundLevel.SILENCIO))
    }

    /** Las dos plataformas deben coincidir: mismo catalogo y misma clasificacion. */
    @Test
    fun elCatalogoCoincideConElDeDesktop() {
        assertEquals(9, SoundEffect.entries.size)
        assertEquals(6, SoundEffect.entries.count { it.important })
    }
}
