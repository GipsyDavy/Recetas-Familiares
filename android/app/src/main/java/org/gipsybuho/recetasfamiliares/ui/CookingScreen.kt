package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CookingScreen(
    recipe: RecipeEntity,
    viewModel: RecetasViewModel,
    onExit: () -> Unit
) {
    val steps by viewModel.stepsFor(recipe.id).collectAsState(initial = emptyList())
    val currentIndexState = remember { mutableStateOf(0) }
    var currentIndex by currentIndexState
    var timerSecondsLeft by remember(currentIndex, steps) {
        mutableStateOf(steps.getOrNull(currentIndex)?.timerMinutes?.let { it * 60 })
    }
    var timerRunning by remember(currentIndex) { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            val hadTimer = timerSecondsLeft != null
            while ((timerSecondsLeft ?: 0) > 0) {
                delay(1_000L)
                timerSecondsLeft = (timerSecondsLeft ?: 0) - 1
            }
            timerRunning = false
            if (hadTimer) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val currentStep = steps.getOrNull(currentIndex)
    val finished = steps.isNotEmpty() && currentIndex >= steps.size

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val stepsSize = steps.size
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        val idx = currentIndexState.value
                        if (idx < stepsSize - 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = idx + 1
                        } else if (idx == stepsSize - 1 && stepsSize > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = stepsSize
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val idx = currentIndexState.value
                        if (idx > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = idx - 1
                        }
                        true
                    }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Salir")
                }
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.md)
                )
            }

            if (steps.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (finished) "¡Receta completada!" else "Paso ${currentIndex + 1} de ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(top = Spacing.md))
                    LinearProgressIndicator(
                        progress = { minOf(1f, (currentIndex + 1).toFloat() / steps.size) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                when {
                    steps.isEmpty() -> Text(
                        "Esta receta no tiene pasos registrados",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    finished -> Text(
                        "¡Buen provecho!",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    else -> Text(
                        currentStep!!.instruction,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                    )
                }
            }

            if (currentStep?.timerMinutes != null && !finished) {
                val secs = timerSecondsLeft ?: (currentStep.timerMinutes * 60)
                val mm = secs / 60
                val ss = secs % 60
                val timerDone = secs == 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            timerDone -> MaterialTheme.colorScheme.errorContainer
                            timerRunning -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (timerDone) "⏱ ¡Listo!" else "⏱ %02d:%02d".format(mm, ss),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (timerDone) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = {
                                if (timerDone) {
                                    timerSecondsLeft = currentStep.timerMinutes * 60
                                    timerRunning = false
                                } else {
                                    timerRunning = !timerRunning
                                }
                            }) {
                                Icon(
                                    imageVector = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (timerRunning) "Pausar" else "Iniciar temporizador"
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndex--; timerRunning = false
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("← Anterior") }

                if (finished) {
                    Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                } else if (currentIndex < steps.size - 1) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndex++; timerRunning = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Siguiente →") }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndex = steps.size
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("¡Finalizar!") }
                }
            }
        }
    }
}
