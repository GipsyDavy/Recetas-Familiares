package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import kotlinx.coroutines.delay
import org.gipsybuho.recetasfamiliares.core.SoundEffect
import org.gipsybuho.recetasfamiliares.core.SoundPlayer
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
    val hapticsEnabled = LocalHapticsEnabled.current
    val focusRequester = remember { FocusRequester() }
    var totalDrag by remember { mutableStateOf(0f) }
    val swipeThresh = 80f
    var showHint by remember { mutableStateOf(true) }
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
            if (hadTimer) if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (hadTimer) SoundPlayer.play(SoundEffect.TIMER)
        }
    }
    LaunchedEffect("hint") { delay(3_000L); showHint = false }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as android.app.Activity
        val window   = activity.window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentStep = steps.getOrNull(currentIndex)
    val finished    = steps.isNotEmpty() && currentIndex >= steps.size
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(steps.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val idx = currentIndexState.value
                        val n   = steps.size
                        when {
                            totalDrag < -swipeThresh && idx < n - 1 -> {
                                SoundPlayer.play(SoundEffect.STEP)
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentIndexState.value = idx + 1; timerRunning = false
                            }
                            totalDrag < -swipeThresh && idx == n - 1 && n > 0 -> {
                                SoundPlayer.play(SoundEffect.STEP)
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentIndexState.value = n
                            }
                            totalDrag > swipeThresh && idx > 0 -> {
                                SoundPlayer.play(SoundEffect.STEP)
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentIndexState.value = idx - 1; timerRunning = false
                            }
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, delta -> totalDrag += delta }
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val stepsSize = steps.size
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        val idx = currentIndexState.value
                        if (idx < stepsSize - 1) {
                            SoundPlayer.play(SoundEffect.STEP)
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = idx + 1
                        } else if (idx == stepsSize - 1 && stepsSize > 0) {
                            SoundPlayer.play(SoundEffect.STEP)
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = stepsSize
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val idx = currentIndexState.value
                        if (idx > 0) {
                            SoundPlayer.play(SoundEffect.STEP)
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentIndexState.value = idx - 1
                        }
                        true
                    }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isLandscape) {
                // ── Landscape: paso izquierda, controles derecha ──────────
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.sm)
                ) {
                    // Left column — header + step text
                    Column(
                        modifier            = Modifier
                            .weight(1.5f)
                            .fillMaxHeight()
                            .padding(end = Spacing.xl),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onExit) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Salir")
                            }
                            Text(
                                recipe.title,
                                style    = MaterialTheme.typography.titleMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.md)
                            )
                        }
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                steps.isEmpty() -> Text(
                                    "Esta receta no tiene pasos registrados",
                                    style     = MaterialTheme.typography.headlineSmall,
                                    color     = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                                finished -> Text(
                                    "¡Buen provecho!",
                                    style     = MaterialTheme.typography.displaySmall,
                                    color     = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                else -> Text(
                                    currentStep!!.instruction,
                                    style      = MaterialTheme.typography.headlineMedium,
                                    textAlign  = TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                                )
                            }
                        }
                    }

                    // Right column — progress + timer + nav buttons
                    Column(
                        modifier            = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (steps.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.fillMaxWidth()
                            ) {
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
                            modifier         = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentStep?.timerMinutes != null && !finished) {
                                val totalSeconds = currentStep.timerMinutes * 60
                                val secs         = timerSecondsLeft ?: totalSeconds
                                val mm           = secs / 60
                                val ss           = secs % 60
                                val timerDone    = secs == 0
                                val progress     = if (totalSeconds > 0) secs.toFloat() / totalSeconds else 0f
                                val timerColor   = when {
                                    timerDone    -> MaterialTheme.colorScheme.error
                                    timerRunning -> MaterialTheme.colorScheme.primary
                                    else         -> MaterialTheme.colorScheme.outline
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier            = Modifier.fillMaxWidth()
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
                                                targetState    = if (timerDone) "done" else "%02d:%02d".format(mm, ss),
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
                                            timerSecondsLeft = totalSeconds
                                            timerRunning = false
                                        } else {
                                            timerRunning = !timerRunning
                                        }
                                    }) {
                                        Icon(
                                            imageVector        = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (timerRunning) "Pausar" else "Iniciar temporizador"
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (currentIndex > 0) {
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentIndex--; timerRunning = false
                                    }
                                },
                                enabled  = currentIndex > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("← Anterior") }
                            if (finished) {
                                Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                            } else if (currentIndex < steps.size - 1) {
                                Button(
                                    onClick = {
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentIndex++; timerRunning = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Siguiente →") }
                            } else {
                                Button(
                                    onClick = {
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentIndex = steps.size
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("¡Finalizar!") }
                            }
                        }
                    }
                }
            } else {
                // ── Portrait: layout original ──────────────────────────────
                Column(
                    modifier            = Modifier.fillMaxSize().padding(
                        horizontal = Spacing.xxl,
                        vertical   = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Salir")
                        }
                        Text(
                            recipe.title,
                            style    = MaterialTheme.typography.titleMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        modifier         = Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            steps.isEmpty() -> Text(
                                "Esta receta no tiene pasos registrados",
                                style     = MaterialTheme.typography.headlineSmall,
                                color     = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                            finished -> Text(
                                "¡Buen provecho!",
                                style     = MaterialTheme.typography.displaySmall,
                                color     = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            else -> Text(
                                currentStep!!.instruction,
                                style      = MaterialTheme.typography.headlineMedium,
                                textAlign  = TextAlign.Center,
                                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                            )
                        }
                    }

                    if (currentStep?.timerMinutes != null && !finished) {
                        val totalSeconds = currentStep.timerMinutes * 60
                        val secs         = timerSecondsLeft ?: totalSeconds
                        val mm           = secs / 60
                        val ss           = secs % 60
                        val timerDone    = secs == 0
                        val progress     = if (totalSeconds > 0) secs.toFloat() / totalSeconds else 0f
                        val timerColor   = when {
                            timerDone    -> MaterialTheme.colorScheme.error
                            timerRunning -> MaterialTheme.colorScheme.primary
                            else         -> MaterialTheme.colorScheme.outline
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)
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
                                        targetState    = if (timerDone) "done" else "%02d:%02d".format(mm, ss),
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
                                    timerSecondsLeft = totalSeconds
                                    timerRunning = false
                                } else {
                                    timerRunning = !timerRunning
                                }
                            }) {
                                Icon(
                                    imageVector        = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (timerRunning) "Pausar" else "Iniciar temporizador"
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (currentIndex > 0) {
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentIndex--; timerRunning = false
                                }
                            },
                            enabled  = currentIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("← Anterior") }

                        if (finished) {
                            Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                        } else if (currentIndex < steps.size - 1) {
                            Button(
                                onClick = {
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentIndex++; timerRunning = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Siguiente →") }
                        } else {
                            Button(
                                onClick = {
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentIndex = steps.size
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("¡Finalizar!") }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible  = showHint,
                enter    = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
                exit     = fadeOut(tween(500)) + slideOutVertically(tween(500)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
            ) {
                Surface(
                    shape          = MaterialTheme.shapes.large,
                    color          = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 28.dp, vertical = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Desliza para navegar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
