package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.gipsybuho.recetasfamiliares.core.HelpContent

/**
 * Ayuda de Android, en hoja inferior.
 *
 * Empieza en la ayuda de la pantalla en la que estas, que es la que resuelve en
 * el momento, y desde ahi se llega al indice completo. En un movil no cabe el
 * indice a la izquierda como en el ordenador, asi que se navega en dos pasos.
 *
 * El texto vive en [HelpContent], que no depende de Compose y tiene tests.
 *
 * @param screenKey clave de la pantalla, o null para abrir directamente el indice
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HelpSheet(screenKey: String?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        HelpSheetBody(screenKey)
    }
}

// Separado de HelpSheet para poder probar el contenido sin el Popup del
// ModalBottomSheet: Robolectric lo posiciona mal tras un cambio de rama del
// when (ventana desconectada de la visible), y los tests que hacen click ahi
// no encuentran nada visible.
@Composable
internal fun HelpSheetBody(screenKey: String?) {
    // null = indice; una seccion = su contenido; "topic" = ayuda contextual.
    var openSection by remember { mutableStateOf<HelpContent.Section?>(null) }
    var showingTopic by remember { mutableStateOf(screenKey != null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        when {
            showingTopic && screenKey != null -> {
                val topic = HelpContent.topicOrGeneral(screenKey)
                Text(
                    "${topic.emoji}  ${topic.title}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                topic.tips.forEach { Bullet(it) }
                HorizontalDivider()
                TextButton(onClick = { showingTopic = false }) {
                    Text("Ver todos los temas")
                }
            }

            openSection != null -> {
                val section = openSection!!
                Text(
                    "${section.emoji}  ${section.title}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                section.blocks.forEach { Bullet(it) }
                HorizontalDivider()
                TextButton(onClick = { openSection = null }) {
                    Text("Volver al índice")
                }
            }

            else -> {
                Text(
                    "Centro de ayuda",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                LazyColumn {
                    items(HelpContent.sections()) { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openSection = section }
                                .padding(vertical = Spacing.md)
                        ) {
                            Text(
                                "${section.emoji}  ${section.title}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
