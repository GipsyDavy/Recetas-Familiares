package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.gipsybuho.recetasfamiliares.core.HelpContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de renderizado de la ayuda, en la JVM con Robolectric: sin emulador, asi
 * que corren en la misma tarea que el resto y la CI los ejecuta sin cambios.
 *
 * Que cubren y que no: comprueban que la pantalla se compone y muestra lo que
 * debe. No comprueban pixeles ni recortes de texto, que es donde estuvieron
 * varios fallos de esta jornada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpSheetRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun laAyudaDeUnaPantallaMuestraSuTituloYSusConsejos() {
        compose.setContent { HelpSheet(screenKey = "recipes", onDismiss = {}) }

        val topic = HelpContent.topic("recipes")!!
        compose.onNodeWithText(topic.title, substring = true).assertIsDisplayed()
        compose.onNodeWithText(topic.tips.first(), substring = true).assertIsDisplayed()
    }

    /** El paso de la ayuda de pantalla al indice completo. */
    @Test
    fun desdeLaAyudaSeLlegaAlIndiceCompleto() {
        compose.setContent { HelpSheet(screenKey = "recipes", onDismiss = {}) }

        compose.onNodeWithText("Ver todos los temas").performClick()

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
        compose.onNodeWithText("Primeros pasos", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Problemas frecuentes", substring = true).assertIsDisplayed()
    }

    /** Con clave nula se abre directamente el indice, que es como entra desde Perfil. */
    @Test
    fun sinPantallaSeAbreDirectamenteElIndice() {
        compose.setContent { HelpSheet(screenKey = null, onDismiss = {}) }

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }

    @Test
    fun alTocarUnaSeccionSeVeSuContenidoYSePuedeVolver() {
        compose.setContent { HelpSheet(screenKey = null, onDismiss = {}) }

        compose.onNodeWithText("Problemas frecuentes", substring = true).performClick()

        val seccion = HelpContent.sections().last()
        compose.onNodeWithText(seccion.blocks.first(), substring = true).assertIsDisplayed()

        compose.onNodeWithText("Volver al índice").performClick()
        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }
}
