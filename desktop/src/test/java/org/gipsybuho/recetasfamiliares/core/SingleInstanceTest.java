package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceTest {

    @Test
    void laPrimeraInstanciaSeQuedaConElBloqueo(@TempDir Path dir) throws IOException {
        SingleInstance instancia = new SingleInstance();

        assertTrue(instancia.acquire(dir.resolve("app.lock")));

        instancia.release();
    }

    /**
     * El caso que importa: abrir la aplicacion dos veces. La segunda no debe
     * quedarse con el bloqueo, porque cuatro instancias apiladas son lo que hizo
     * fallar la instalacion de la v1.4 con el error 32 de Windows.
     */
    @Test
    void laSegundaInstanciaNoConsigueElBloqueo(@TempDir Path dir) throws IOException {
        Path lock = dir.resolve("app.lock");
        SingleInstance primera = new SingleInstance();
        SingleInstance segunda = new SingleInstance();

        assertTrue(primera.acquire(lock));
        assertFalse(segunda.acquire(lock), "la segunda instancia no debe poder arrancar");

        primera.release();
    }

    /** Al cerrarse la primera, otra debe poder arrancar. */
    @Test
    void alSoltarElBloqueoOtraInstanciaPuedeArrancar(@TempDir Path dir) throws IOException {
        Path lock = dir.resolve("app.lock");
        SingleInstance primera = new SingleInstance();
        primera.acquire(lock);
        primera.release();

        SingleInstance siguiente = new SingleInstance();

        assertTrue(siguiente.acquire(lock));

        siguiente.release();
    }

    /** Si no se puede crear el fichero, la aplicacion debe arrancar igual. */
    @Test
    void anteUnaRutaImposibleSeDejaArrancar(@TempDir Path dir) throws IOException {
        Path ocupada = dir.resolve("soy-un-fichero");
        Files.writeString(ocupada, "x");
        SingleInstance instancia = new SingleInstance();

        // Un directorio dentro de un fichero no se puede crear.
        assertTrue(instancia.acquire(ocupada.resolve("sub").resolve("app.lock")));
    }
}
