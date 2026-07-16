package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.core.ServerUrlPreference
import org.gipsybuho.recetasfamiliares.core.rememberImagePickerLauncher
import org.gipsybuho.recetasfamiliares.families.FamilyListSheet
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository
import org.gipsybuho.recetasfamiliares.families.FamilyViewModel
import org.gipsybuho.recetasfamiliares.network.UserRecipeRankingDto
import org.gipsybuho.recetasfamiliares.theme.AppTheme
import org.gipsybuho.recetasfamiliares.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.theme.darkColors
import org.gipsybuho.recetasfamiliares.theme.lightColors
import org.gipsybuho.recetasfamiliares.users.UserRepository

@Composable
fun SettingsScreen(
    selectedTheme: AppTheme,
    themeMode: ThemeMode,
    hapticsEnabled: Boolean,
    reduceMotion: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onModeChange: (ThemeMode) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    session: SessionStore? = null,
    serverUrlPreference: ServerUrlPreference? = null,
    userRepository: UserRepository? = null,
    familyMemberRepository: FamilyMemberRepository? = null,
    familyViewModel: FamilyViewModel? = null
) {
    val scope = rememberCoroutineScope()
    var showInviteDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showFamilyList by androidx.compose.runtime.remember { mutableStateOf(false) }
    var inviteMessage by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val familyRole by (session?.familyRoleFlow ?: flowOf(null)).collectAsState(initial = session?.familyRole)
    val isAdmin = familyRole == "ADMIN" || familyRole == "OWNER"
    var serverUrlInput by androidx.compose.runtime.remember(serverUrlPreference) {
        mutableStateOf(serverUrlPreference?.baseUrl.orEmpty())
    }
    var serverUrlMessage by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var recipeRanking by androidx.compose.runtime.remember(session?.familyId, familyMemberRepository) {
        mutableStateOf<List<UserRecipeRankingDto>>(emptyList())
    }
    val systemDark = isSystemInDarkTheme()
    val previewDark = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && systemDark)

    LaunchedEffect(familyMemberRepository, session?.familyId) {
        recipeRanking = if (familyMemberRepository != null && session?.familyId != null) {
            runCatching { familyMemberRepository.recipeRanking() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    val imagePicker = rememberImagePickerLauncher { bytes ->
        if (bytes != null && userRepository != null) {
            scope.launch {
                runCatching { userRepository.uploadAvatar(bytes) }
            }
        }
    }

    if (showInviteDialog && familyMemberRepository != null) {
        InviteMemberDialog(
            onDismiss = { showInviteDialog = false },
            onConfirm = { email, role ->
                scope.launch {
                    val result = runCatching { familyMemberRepository.invite(email, role) }
                    inviteMessage = if (result.isSuccess) {
                        showInviteDialog = false
                        "Miembro invitado correctamente"
                    } else {
                        "No se pudo invitar al miembro"
                    }
                }
            }
        )
    }

    if (showFamilyList && familyViewModel != null) {
        FamilyListSheet(
            viewModel = familyViewModel,
            onDismiss = { showFamilyList = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp))

        if (session != null) {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        val avatarUrl = session.avatarUrl
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Foto de perfil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            val initials = session.displayName
                                ?.split(" ")?.filter { it.isNotBlank() }?.take(2)
                                ?.map { it.first().uppercaseChar() }?.joinToString("")
                            if (!initials.isNullOrBlank()) {
                                Text(initials, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    if (userRepository != null) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { imagePicker.launch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Cambiar foto",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Column {
                    Text(session.displayName ?: "—", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(session.email ?: "—", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (recipeRanking.isNotEmpty()) {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            RecipeRankingPreview(recipeRanking.take(5))
            Spacer(Modifier.height(8.dp))
        }

        if (serverUrlPreference != null) {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Servidor", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = {
                    serverUrlInput = it
                    serverUrlMessage = null
                },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            serverUrlMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val before = serverUrlPreference.baseUrl
                        serverUrlPreference.reset()
                        serverUrlInput = serverUrlPreference.baseUrl
                        serverUrlMessage = "Servidor por defecto restaurado"
                        if (before != serverUrlPreference.baseUrl) onLogout()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Por defecto")
                }
                Button(
                    onClick = {
                        val before = serverUrlPreference.baseUrl
                        runCatching { serverUrlPreference.baseUrl = serverUrlInput }
                            .onSuccess {
                                serverUrlInput = serverUrlPreference.baseUrl
                                serverUrlMessage = "Servidor actualizado"
                                if (before != serverUrlPreference.baseUrl) onLogout()
                            }
                            .onFailure { serverUrlMessage = it.message ?: "URL no valida" }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = serverUrlInput.isNotBlank()
                ) {
                    Text("Guardar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (familyViewModel != null) {
            OutlinedButton(
                onClick = { showFamilyList = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Familias")
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isAdmin && familyMemberRepository != null) {
            OutlinedButton(
                onClick = {
                    showInviteDialog = true
                    inviteMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Invitar miembro a la familia")
            }
            inviteMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Text("Hápticos", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = hapticsEnabled, onCheckedChange = onHapticsChange)
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Modo de pantalla", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick  = { onModeChange(mode) },
                    label    = {
                        Text(when (mode) {
                            ThemeMode.LIGHT  -> "Claro"
                            ThemeMode.DARK   -> "Oscuro"
                            ThemeMode.SYSTEM -> "Sistema"
                        })
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Color del tema", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val columnCount = if (maxWidth < 420.dp || fontScale >= 1.35f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTheme.entries.chunked(columnCount).forEach { themes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        themes.forEach { theme ->
                            ThemePreviewCard(
                                theme = theme,
                                selected = selectedTheme == theme,
                                previewDark = previewDark,
                                reduceMotion = reduceMotion,
                                onClick = { onThemeChange(theme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            "Cerrar sesión",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout)
                .padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun RecipeRankingPreview(ranking: List<UserRecipeRankingDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Ranking de recetas", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ranking.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("#${item.rank}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${item.recipesCreated} recetas · ${item.ratingsReceived} valoraciones · ${rankingAverageLabel(item.averageStars)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${item.score} pts", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InviteMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var email by androidx.compose.runtime.remember { mutableStateOf("") }
    var selectedRole by androidx.compose.runtime.remember { mutableStateOf("MEMBER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitar miembro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email del miembro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Rol",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRole == "MEMBER",
                        onClick = { selectedRole = "MEMBER" },
                        label = { Text("Miembro") }
                    )
                    FilterChip(
                        selected = selectedRole == "ADMIN",
                        onClick = { selectedRole = "ADMIN" },
                        label = { Text("Administrador") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email.trim(), selectedRole) },
                enabled = email.isNotBlank()
            ) {
                Text("Invitar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun rankingAverageLabel(value: Double?): String {
    if (value == null) return "sin media"
    val rounded = (value * 10.0).roundToInt() / 10.0
    return "media $rounded"
}

@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    selected: Boolean,
    previewDark: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = if (theme.recommendedDark || previewDark) theme.darkColors() else theme.lightColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && pressed) 0.975f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "theme_card_scale_${theme.name}"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 2.dp else if (selected) 10.dp else 5.dp,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "theme_card_elevation_${theme.name}"
    )
    val shape = MaterialTheme.shapes.large
    val selectedBorder = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.outline.copy(alpha = 0.42f)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (!reduceMotion && pressed) 2.dp.toPx() else 0f
            }
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.surface, colors.primaryContainer.copy(alpha = if (previewDark || theme.recommendedDark) 0.72f else 0.58f))
                )
            )
            .border(selectedBorder, borderColor, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = "${theme.displayName}. ${theme.description}"
                this.selected = selected
                this.stateDescription = if (selected) "Tema seleccionado" else "Tema disponible"
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(theme.emoji, style = MaterialTheme.typography.titleLarge)
            if (theme.isFeatured) {
                Surface(
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = CircleShape
                ) {
                    Text(
                        "Principal",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            theme.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
        )
        Text(
            theme.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(colors.primary, colors.secondary, colors.tertiary).forEach { color ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, colors.outline.copy(alpha = 0.45f), CircleShape)
                )
            }
        }
    }
}
