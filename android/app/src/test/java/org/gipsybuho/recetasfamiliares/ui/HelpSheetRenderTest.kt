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

/**
 * Tests de renderizado de la ayuda, en la JVM con Robolectric: sin emulador, asi
 * que corren en la misma tarea que el resto y la CI los ejecuta sin cambios.
 *
 * Que cubren y que no: comprueban que la pantalla se compone y muestra lo que
 * debe. NO comprueban pixeles, contraste ni texto recortado, que es donde
 * estuvieron varios de los fallos de la jornada del 2026-08-09. Abrir la
 * aplicacion sigue siendo necesario.
 *
 * La configuracion comun (SDK y Application vacia) vive en
 * src/test/resources/robolectric.properties.
 */
@RunWith(RobolectricTestRunner::class)
class HelpSheetRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** Con clave nula se abre directamente el indice, que es como entra desde Perfil. */
    @Test
    fun sinPantallaSeAbreDirectamenteElIndice() {
        compose.setContent { HelpSheetBody(screenKey = null) }

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }

    @Test
    fun laAyudaDeUnaPantallaMuestraSuTituloYSusConsejos() {
        compose.setContent { HelpSheetBody(screenKey = "recipes") }

        val topic = HelpContent.topic("recipes")!!
        compose.onNodeWithText(topic.title, substring = true).assertIsDisplayed()
        compose.onNodeWithText(topic.tips.first(), substring = true).assertIsDisplayed()
    }

    /** El paso de la ayuda de la pantalla al indice completo. */
    @Test
    fun desdeLaAyudaSeLlegaAlIndiceCompleto() {
        compose.setContent { HelpSheetBody(screenKey = "recipes") }

        compose.onNodeWithText("Ver todos los temas").performClick()

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
        // Solo la primera seccion: el resto del indice es un LazyColumn y en la
        // pantalla pequena de Robolectric no llega a componerse.
        compose.onNodeWithText(HelpContent.sections().first().title, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun alTocarUnaSeccionSeVeSuContenidoYSePuedeVolver() {
        compose.setContent { HelpSheetBody(screenKey = null) }

        val seccion = HelpContent.sections().first()
        compose.onNodeWithText(seccion.title, substring = true).performClick()

        compose.onNodeWithText(seccion.blocks.first(), substring = true).assertIsDisplayed()

        compose.onNodeWithText("Volver al índice").performClick()
        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }
}
