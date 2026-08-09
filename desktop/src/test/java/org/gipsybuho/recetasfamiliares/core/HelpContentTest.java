package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class HelpContentTest {

    /**
     * Las claves que maneja MainWindow.navigateTo, mas "profile", que es la
     * pestana de Ajustes con ayuda propia. Si se anade una pantalla y se olvida
     * su ayuda, este test lo dice.
     */
    private static final List<String> PANTALLAS = List.of(
            "dashboard", "recipes", "stock", "menu", "shopping", "notes",
            "chat", "conversations", "members", "settings", "profile");

    @Test
    void todaPantallaTieneSuAyudaContextual() {
        for (String pantalla : PANTALLAS) {
            assertNotNull(HelpContent.topic(pantalla), "falta la ayuda de " + pantalla);
        }
    }

    @Test
    void ningunTemaSeQuedaSinConsejos() {
        for (String pantalla : PANTALLAS) {
            HelpContent.Topic topic = HelpContent.topic(pantalla);
            assertFalse(topic.title().isBlank(), "tema sin titulo: " + pantalla);
            assertTrue(topic.tips().size() >= 3, "el tema " + pantalla + " necesita 3 consejos o mas");
            topic.tips().forEach(tip ->
                    assertFalse(tip.isBlank(), "consejo vacio en " + pantalla));
        }
    }

    /** Ante una clave desconocida se responde algo util, no null. */
    @Test
    void unaPantallaSinTemaCaeEnLaAyudaGeneral() {
        HelpContent.Topic fallback = HelpContent.topicOrGeneral("pantalla-que-no-existe");

        assertNotNull(fallback);
        assertFalse(fallback.tips().isEmpty());
    }

    @Test
    void elCentroDeAyudaTieneLasTreceSecciones() {
        assertTrue(HelpContent.sections().size() == 13,
                "se esperaban 13 secciones y hay " + HelpContent.sections().size());
    }

    @Test
    void ningunaSeccionSeQuedaVacia() {
        for (HelpContent.Section section : HelpContent.sections()) {
            assertFalse(section.title().isBlank(), "seccion sin titulo");
            assertTrue(section.blocks().size() >= 3,
                    "la seccion " + section.title() + " necesita 3 parrafos o mas");
            section.blocks().forEach(block ->
                    assertFalse(block.isBlank(), "parrafo vacio en " + section.title()));
        }
    }

    /** Los titulos se usan como indice: repetirlos confundiria. */
    @Test
    void losTitulosDeSeccionNoSeRepiten() {
        long distintos = HelpContent.sections().stream()
                .map(HelpContent.Section::title)
                .distinct()
                .count();

        assertTrue(distintos == HelpContent.sections().size(), "hay titulos de seccion repetidos");
    }
}
