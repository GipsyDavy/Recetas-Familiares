package org.gipsybuho.recetasfamiliares.cooking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.gipsybuho.recetasfamiliares.core.ScreenWakeLock
import org.gipsybuho.recetasfamiliares.core.formatTimerMinutesSeconds
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.RecipeDto
import org.gipsybuho.recetasfamiliares.network.RecipeStepDto
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository

@Composable
fun CookingScreen(
    recipe: RecipeDto,
    repository: RecipeRepository,
    onExit: () -> Unit
) {
    var steps         by remember { mutableStateOf<List<RecipeStepDto>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }
    var currentIndex  by remember { mutableStateOf(0) }
    var timerLeft     by remember { mutableStateOf<Int?>(null) }
    var timerRunning  by remember { mutableStateOf(false) }
    val haptic        = rememberHapticFeedback()
    val wakeLock      = remember { ScreenWakeLock() }
    var totalDrag     by remember { mutableStateOf(0f) }
    var showSwipeHint by remember { mutableStateOf(true) }

    LaunchedEffect("hint") {
        kotlinx.coroutines.delay(3_000L)
        showSwipeHint = false
    }

    LaunchedEffect(recipe.id) {
        steps = repository.loadSteps(recipe.id).sortedBy { it.position }
        loading = false
    }

    LaunchedEffect(currentIndex, steps) {
        timerRunning = false
        timerLeft = steps.getOrNull(currentIndex)?.timerMinutes?.let { it * 60 }
        if (currentIndex > 0 || steps.isNotEmpty()) haptic.selection()
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while ((timerLeft ?: 0) > 0) {
                delay(1_000L)
                timerLeft = (timerLeft ?: 0) - 1
            }
            timerRunning = false
            if (timerLeft == 0) haptic.success()
        }
    }

    DisposableEffect(Unit) {
        wakeLock.acquire()
        onDispose { wakeLock.release() }
    }

    val currentStep = steps.getOrNull(currentIndex)
    val finished    = steps.isNotEmpty() && currentIndex >= steps.size
    val swipeThresh = 80f

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(steps.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val idx = currentIndex
                        if (totalDrag < -swipeThresh) {
                            when {
                                idx < steps.size - 1 -> { haptic.selection(); currentIndex = idx + 1; timerRunning = false }
                                idx == steps.size - 1 && steps.isNotEmpty() -> { haptic.selection(); currentIndex = steps.size }
                            }
                        } else if (totalDrag > swipeThresh && idx > 0) {
                            haptic.selection(); currentIndex = idx - 1; timerRunning = false
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, delta -> totalDrag += delta }
                )
            },
        color = MaterialTheme.colorScheme.background
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir")
                }
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }

            // Progress bar
            if (steps.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (finished) "¡Receta completada!" else "Paso ${currentIndex + 1} de ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { minOf(1f, (currentIndex + 1).toFloat() / steps.size) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Instrucción central
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp),
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
                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            currentStep!!.instruction,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                            modifier = Modifier.padding(bottom = 40.dp)
                        )
                        if (showSwipeHint) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            ) {
                                Text(
                                    "← Desliza para navegar →",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Temporizador (solo si el paso tiene timer)
            if (currentStep?.timerMinutes != null && !finished) {
                val totalSeconds = currentStep.timerMinutes * 60
                val secs = timerLeft ?: totalSeconds
                val mm = secs / 60
                val ss = secs % 60
                val timerDone = secs == 0
                val progress = if (totalSeconds > 0) secs.toFloat() / totalSeconds else 0f
                val timerColor = when {
                    timerDone    -> MaterialTheme.colorScheme.error
                    timerRunning -> MaterialTheme.colorScheme.primary
                    else         -> MaterialTheme.colorScheme.outline
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress    = { progress },
                            modifier    = Modifier.size(96.dp),
                            strokeWidth = 6.dp,
                            color       = timerColor,
                            trackColor  = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedContent(
                                targetState = if (timerDone) "done" else formatTimerMinutesSeconds(mm, ss),
                                transitionSpec = {
                                    slideInVertically { -it } + fadeIn() togetherWith
                                    slideOutVertically { it } + fadeOut()
                                },
                                label = "timer"
                            ) { state ->
                                Text(
                                    if (state == "done") "¡Listo!" else state,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = timerColor
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (timerDone) {
                            timerLeft = totalSeconds; timerRunning = false
                        } else {
                            timerRunning = !timerRunning
                        }
                    }) {
                        Icon(
                            if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (timerRunning) "Pausar" else "Iniciar"
                        )
                    }
                }
            }

            // Botones navegación
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) { haptic.selection(); currentIndex--; timerRunning = false }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("← Anterior") }
                when {
                    finished -> Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                    currentIndex < steps.size - 1 -> Button(
                        onClick = { haptic.selection(); currentIndex++; timerRunning = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Siguiente →") }
                    else -> Button(
                        onClick = { haptic.selection(); currentIndex = steps.size },
                        modifier = Modifier.weight(1f)
                    ) { Text("¡Finalizar!") }
                }
            }
        }
    }
}
