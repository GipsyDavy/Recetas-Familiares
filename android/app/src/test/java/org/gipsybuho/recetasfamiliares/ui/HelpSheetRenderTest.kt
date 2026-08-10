package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        compose.setContent { HelpSheet(screenKey = null, onDismiss = {}) }

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }
}
