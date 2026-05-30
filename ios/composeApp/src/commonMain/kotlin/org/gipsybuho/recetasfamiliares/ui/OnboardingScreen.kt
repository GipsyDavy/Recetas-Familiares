package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gipsybuho.recetasfamiliares.Spacing

private data class OnboardingPage(
    val icon: String,
    val title: String,
    val subtitle: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val deco: List<String>
)

private val PAGES = listOf(
    OnboardingPage(
        icon     = "🍳",
        title    = "Bienvenido a Recetas Familiares",
        subtitle = "Guarda y comparte las recetas que conectan a tu familia",
        bgTop    = Color(0xFFF6E7D8),
        bgBottom = Color(0xFFEDD5BB),
        accent   = Color(0xFFC17D52),
        deco     = listOf("🥕", "🧅", "🫙")
    ),
    OnboardingPage(
        icon     = "📅",
        title    = "Planifica y organiza",
        subtitle = "Menús semanales, stock familiar y listas de la compra, sincronizados siempre",
        bgTop    = Color(0xFFDDEDE0),
        bgBottom = Color(0xFFC4DBC9),
        accent   = Color(0xFF6B8F71),
        deco     = listOf("🥦", "🫑", "🧄")
    ),
    OnboardingPage(
        icon     = "✨",
        title    = "Tu legado culinario",
        subtitle = "Cocina juntos desde cualquier dispositivo. ¡Empieza hoy!",
        bgTop    = Color(0xFFFDF3D0),
        bgBottom = Color(0xFFF5E4A0),
        accent   = Color(0xFFD4A017),
        deco     = listOf("🍰", "🎂", "🍮")
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var page      by remember { mutableStateOf(0) }
    var totalDrag by remember { mutableStateOf(0f) }
    val swipeThresh = 72f

    val bgColor by animateColorAsState(
        targetValue   = PAGES[page].bgTop.copy(alpha = 0.18f),
        animationSpec = tween(350),
        label         = "onboard_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, Color.Transparent)))
            .pointerInput(page) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            totalDrag < -swipeThresh && page < PAGES.size - 1 -> page++
                            totalDrag < -swipeThresh && page == PAGES.size - 1 -> onFinished()
                            totalDrag > swipeThresh && page > 0 -> page--
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, delta -> totalDrag += delta }
                )
            }
    ) {
        if (page < PAGES.size - 1) {
            TextButton(
                onClick  = onFinished,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md)
            ) {
                Text(
                    "Omitir",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(36.dp))

            AnimatedContent(
                targetState  = page,
                transitionSpec = {
                    if (targetState > initialState) {
                        (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it })
                    } else {
                        (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { -it }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it })
                    }
                },
                modifier = Modifier.weight(1f),
                label    = "onboarding_page"
            ) { p ->
                val data = PAGES[p]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier            = Modifier.fillMaxSize()
                ) {
                    IllustrationCard(page = data)
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(
                        data.title,
                        style     = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        data.subtitle,
                        style     = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier              = Modifier.padding(bottom = Spacing.xl)
                ) {
                    PAGES.indices.forEach { i ->
                        val dotWidth by animateDpAsState(
                            targetValue   = if (i == page) 24.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label         = "dot_w_$i"
                        )
                        val dotColor by animateColorAsState(
                            targetValue   = if (i == page) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                            animationSpec = tween(250),
                            label         = "dot_c_$i"
                        )
                        Box(Modifier.size(dotWidth, 8.dp).clip(CircleShape).background(dotColor))
                    }
                }

                Button(
                    onClick  = { if (page < PAGES.size - 1) page++ else onFinished() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (page < PAGES.size - 1) "Siguiente" else "¡Comenzar!")
                }

                if (page > 0) {
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) {
                        Text("Anterior")
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun IllustrationCard(page: OnboardingPage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(listOf(page.bgTop, page.bgBottom)))
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset((-40).dp, (-40).dp)
                .clip(CircleShape)
                .background(page.accent.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.BottomEnd)
                .offset(28.dp, 28.dp)
                .clip(CircleShape)
                .background(page.accent.copy(alpha = 0.10f))
        )
        Text(
            page.deco[0], fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 32.dp)
        )
        Text(
            page.deco[1], fontSize = 18.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 52.dp)
        )
        Text(
            page.deco[2], fontSize = 16.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 44.dp, bottom = 38.dp)
        )
        Text(
            page.icon, fontSize = 80.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
