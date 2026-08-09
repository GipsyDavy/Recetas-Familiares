package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundLevelTest {

    @Test
    void enSilencioNoSuenaNadaEnAbsoluto() {
        for (SoundEffect effect : SoundEffect.values()) {
            assertFalse(SoundLevel.SILENCIO.allows(effect), "no debe sonar " + effect);
        }
    }

    @Test
    void conTodosSuenaTodo() {
        for (SoundEffect effect : SoundEffect.values()) {
            assertTrue(SoundLevel.TODOS.allows(effect), "debe sonar " + effect);
        }
    }

    /** Lo que le importa a quien cocina: que salio bien, que fallo, y los avisos. */
    @Test
    void soloImportantesDejaPasarLoQueTieneConsecuencias() {
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.SUCCESS));
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.ERROR));
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.DELETE));
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.ALERT));
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.TIMER));
        // El cambio de paso en modo cocina cuenta como importante: se cocina con
        // las manos ocupadas y sin mirar la pantalla.
        assertTrue(SoundLevel.IMPORTANTES.allows(SoundEffect.STEP));
    }

    /** Y lo que solo es ruido de navegacion, no. */
    @Test
    void soloImportantesCallaElRuidoDeNavegacion() {
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.NAVIGATE));
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.TOGGLE));
        assertFalse(SoundLevel.IMPORTANTES.allows(SoundEffect.OPEN));
    }

    @Test
    void seLeeDeLaPreferenciaPorSuNombre() {
        assertEquals(SoundLevel.TODOS, SoundLevel.fromPreference("TODOS", SoundLevel.SILENCIO));
        assertEquals(SoundLevel.IMPORTANTES,
                SoundLevel.fromPreference("IMPORTANTES", SoundLevel.SILENCIO));
    }

    /** Una preferencia corrupta o de una version futura no debe romper la aplicacion. */
    @Test
    void anteUnaPreferenciaIlegibleSeUsaElValorPorDefecto() {
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference("ruido", SoundLevel.SILENCIO));
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference(null, SoundLevel.SILENCIO));
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromPreference("", SoundLevel.SILENCIO));
    }

    /**
     * Antes la preferencia era un booleano "sound". Quien lo tuviera activado no
     * debe quedarse en silencio de golpe al actualizar, ni pasar a oirlo todo.
     */
    @Test
    void migraLaPreferenciaBooleanaAntigua() {
        assertEquals(SoundLevel.IMPORTANTES, SoundLevel.fromLegacyEnabled(true));
        assertEquals(SoundLevel.SILENCIO, SoundLevel.fromLegacyEnabled(false));
    }
}
