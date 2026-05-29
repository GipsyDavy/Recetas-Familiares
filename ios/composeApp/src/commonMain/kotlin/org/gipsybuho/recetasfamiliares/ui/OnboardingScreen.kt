package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.Spacing

private data class OnboardingPage(val icon: String, val title: String, val subtitle: String)

private val PAGES = listOf(
    OnboardingPage("🍳", "Bienvenido a Recetas Familiares", "Guarda y comparte las recetas que conectan a tu familia"),
    OnboardingPage("📅", "Planifica y organiza", "Menús semanales, stock familiar y listas de la compra, sincronizados siempre"),
    OnboardingPage("✨", "Tu legado culinario", "Cocina juntos desde cualquier dispositivo. ¡Empieza hoy!")
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val gradientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(gradientColor, Color.Transparent))
            )
        )
    Column(
        modifier            = Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        AnimatedContent(
            targetState  = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier     = Modifier.weight(1f),
            label        = "onboarding_page"
        ) { p ->
            val data = PAGES[p]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier            = Modifier.fillMaxSize()
            ) {
                Text(data.icon, style = MaterialTheme.typography.displayLarge)
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
                    Surface(
                        modifier = Modifier.size(if (i == page) 20.dp else 8.dp, 8.dp).clip(CircleShape),
                        color    = if (i == page) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outlineVariant
                    ) {}
                }
            }

            if (page < PAGES.size - 1) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Siguiente")
                }
            } else {
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("¡Comenzar!")
                }
            }

            if (page > 0) {
                TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) {
                    Text("Anterior")
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
    } // Box
}
