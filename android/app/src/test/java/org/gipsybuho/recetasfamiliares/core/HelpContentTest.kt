package org.gipsybuho.recetasfamiliares.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {

    /**
     * Las pantallas de Android: las pestanas de MainTab mas los dos chats. No
     * hay "dashboard" ni "members" como en Desktop. Si se anade una pantalla y
     * se olvida su ayuda, este test lo dice.
     */
    private val pantallas = listOf(
        "recipes", "stock", "shopping", "notes", "menu", "profile", "chat", "conversations"
    )

    @Test
    fun todaPantallaTieneSuAyudaContextual() {
        pantallas.forEach { assertNotNull("falta la ayuda de $it", HelpContent.topic(it)) }
    }

    @Test
    fun ningunTemaSeQuedaSinConsejos() {
        pantallas.forEach { pantalla ->
            val topic = HelpContent.topic(pantalla)!!
            assertFalse("tema sin titulo: $pantalla", topic.title.isBlank())
            assertTrue("el tema $pantalla necesita 3 consejos o mas", topic.tips.size >= 3)
            topic.tips.forEach { assertFalse("consejo vacio en $pantalla", it.isBlank()) }
        }
    }

    /** Ante una clave desconocida se responde algo util, no null. */
    @Test
    fun unaPantallaSinTemaCaeEnLaAyudaGeneral() {
        val fallback = HelpContent.topicOrGeneral("pantalla-que-no-existe")

        assertTrue(fallback.tips.isNotEmpty())
    }

    /** Las mismas 13 secciones que Desktop: el contenido no debe divergir. */
    @Test
    fun elCentroDeAyudaTieneLasTreceSecciones() {
        assertEquals(13, HelpContent.sections().size)
    }

    @Test
    fun ningunaSeccionSeQuedaVacia() {
        HelpContent.sections().forEach { section ->
            assertFalse("seccion sin titulo", section.title.isBlank())
            assertTrue(
                "la seccion ${section.title} necesita 3 parrafos o mas",
                section.blocks.size >= 3
            )
            section.blocks.forEach { assertFalse("parrafo vacio en ${section.title}", it.isBlank()) }
        }
    }

    @Test
    fun losTitulosDeSeccionNoSeRepiten() {
        val titulos = HelpContent.sections().map { it.title }

        assertEquals(titulos.size, titulos.distinct().size)
    }

    /**
     * Android no tiene teclado: mencionar atajos de Desktop seria mentir. Este
     * test evita que se copien literalmente desde el contenido de Desktop.
     */
    @Test
    fun elTextoNoHablaDeAtajosDeTecladoNiDeMenuLateral() {
        val todo = (HelpContent.sections().flatMap { it.blocks } +
                HelpContent.sections().map { it.title } +
                pantallas.flatMap { HelpContent.topic(it)!!.tips }).joinToString(" ")

        listOf("Ctrl+", "F1", "menú lateral", "menu lateral").forEach {
            assertFalse("el texto de Android menciona \"$it\"", todo.contains(it))
        }
    }
}
