package org.gipsybuho.recetasfamiliares.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyMemberDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyStatsDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserRecipeRankingDto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    viewModel: RecetasViewModel,
    modifier: Modifier = Modifier,
    onMessageMember: (FamilyMemberDto) -> Unit = {}
) {
    val displayName by viewModel.displayName.collectAsState()
    val email       by viewModel.email.collectAsState()
    val avatarUrl   by viewModel.avatarUrl.collectAsState()
    val isAdmin     by viewModel.isAdmin.collectAsState()
    val serverBaseUrl by viewModel.serverBaseUrl.collectAsState()
    var editing     by remember { mutableStateOf(false) }
    var editName    by remember { mutableStateOf("") }
    var serverUrlInput by remember(serverBaseUrl) { mutableStateOf(serverBaseUrl) }
    var serverUrlError by remember { mutableStateOf<String?>(null) }
    var showInvite  by remember { mutableStateOf(false) }
    var showVerifyCode by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showHelpCenter by remember { mutableStateOf(false) }
    var showFamilySelector by remember { mutableStateOf(false) }
    var showCreateFamily by remember { mutableStateOf(false) }
    var memberRoleChange by remember { mutableStateOf<MemberRoleChange?>(null) }
    var memberToEdit by remember { mutableStateOf<FamilyMemberDto?>(null) }
    var memberToRemove by remember { mutableStateOf<FamilyMemberDto?>(null) }
    val emailVerified by viewModel.emailVerified.collectAsState()
    val families by viewModel.families.collectAsState()
    val activeFamilyId by viewModel.activeFamilyId.collectAsState()
    val context     = LocalContext.current
    val haptic      = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val myUserId    = viewModel.myUserId

    if (showHelpCenter) {
        // screenKey nulo: se abre directamente en el indice, no en la ayuda de Perfil,
        // que ya esta a un toque en el icono de la barra de arriba.
        HelpSheet(screenKey = null, onDismiss = { showHelpCenter = false })
    }

    if (showVerifyCode) {
        VerifyEmailCodeDialog(
            onDismiss = { showVerifyCode = false },
            onConfirm = { code ->
                viewModel.confirmEmailVerification(code)
                showVerifyCode = false
            }
        )
    }

    if (showDeleteAccount) {
        DeleteAccountDialog(
            viewModel = viewModel,
            onDismiss = { showDeleteAccount = false }
        )
    }

    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showLogoutConfirm = false
                viewModel.logout()
            }
        )
    }

    if (showInvite) {
        InviteMemberDialog(
            onDismiss = { showInvite = false },
            onConfirm = { inviteEmail, role ->
                viewModel.inviteMember(inviteEmail, role)
                showInvite = false
            }
        )
    }

    if (showFamilySelector) {
        FamilySelectorDialog(
            families = families,
            activeFamilyId = activeFamilyId,
            onDismiss = { showFamilySelector = false },
            onSelect = { family ->
                viewModel.switchActiveFamily(family.id)
                showFamilySelector = false
            }
        )
    }

    if (showCreateFamily) {
        CreateFamilyDialog(
            onDismiss = { showCreateFamily = false },
            onConfirm = { name ->
                viewModel.createFamily(name)
                showCreateFamily = false
            }
        )
    }

    memberRoleChange?.let { change ->
        ChangeMemberRoleDialog(
            member = change.member,
            newRole = change.newRole,
            onDismiss = { memberRoleChange = null },
            onConfirm = {
                viewModel.updateMemberRole(change.member.userId, change.newRole)
                memberRoleChange = null
            }
        )
    }

    memberToEdit?.let { member ->
        EditMemberDialog(
            member = member,
            onDismiss = { memberToEdit = null },
            onConfirm = { displayName, memberEmail, passwordAction, temporaryPassword ->
                viewModel.updateMember(
                    userId = member.userId,
                    displayName = displayName,
                    email = memberEmail,
                    passwordAction = passwordAction,
                    temporaryPassword = temporaryPassword
                )
                memberToEdit = null
            }
        )
    }

    memberToRemove?.let { member ->
        RemoveMemberDialog(
            member = member,
            onDismiss = { memberToRemove = null },
            onConfirm = {
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.removeMember(member.userId)
                memberToRemove = null
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadAvatar(context, it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.lg))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!avatarUrl.isNullOrBlank()) {
                            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
                            AsyncImage(
                                model           = avatarUrl,
                                contentDescription = "Foto de perfil",
                                contentScale    = ContentScale.Crop,
                                onState         = { imageState = it },
                                modifier        = Modifier.fillMaxSize().clip(CircleShape)
                            )
                            if (imageState is AsyncImagePainter.State.Loading) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(24.dp),
                                    color     = MaterialTheme.colorScheme.onPrimaryContainer,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (!displayName.isNullOrBlank()) {
                            Text(
                                text  = displayName!!.split(" ").filter { it.isNotBlank() }.take(2)
                                    .map { it.first().uppercaseChar() }.joinToString(""),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                IconButton(
                    onClick  = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Cambiar foto de perfil",
                            modifier = Modifier.size(16.dp).padding(4.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = {
                    editName = displayName ?: ""
                    editing = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar nombre",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                email ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (editing) {
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value         = editName,
                onValueChange = { editName = it },
                label         = { Text("Nombre") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateDisplayName(editName)
                            editing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled  = editName.isNotBlank()
                ) {
                    Text("Guardar")
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.loadFamilyStats()
            viewModel.loadFamilyActivity()
            viewModel.loadAccountStatus()
            viewModel.loadFamilyMembers()
            viewModel.loadPresence()
            viewModel.loadUserRecipeRankings()
            viewModel.loadFamilyInfo()
        }
        val familyInfo by viewModel.familyInfo.collectAsState()
        val familyPhotoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.uploadFamilyAvatar(context, it) }
        }
        familyInfo?.let { family ->
            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!family.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = family.avatarUrl,
                                    contentDescription = "Imagen del grupo familiar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    family.name.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(family.name, style = MaterialTheme.typography.titleMedium)
                        family.role?.let {
                            Text(
                                familyRoleLabel(it),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isAdmin) {
                        IconButton(onClick = { familyPhotoLauncher.launch("image/*") }) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Cambiar imagen del grupo familiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (families.size > 1) {
                    OutlinedButton(
                        onClick = { showFamilySelector = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cambiar familia activa")
                    }
                }
                if (isAdmin) {
                    OutlinedButton(
                        onClick = { showCreateFamily = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Crear familia")
                    }
                }
            }
        }

        val recipes by viewModel.recipes.collectAsState()
        val familyStats by viewModel.familyStats.collectAsState()
        if (familyStats != null || recipes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            FamilyStatsSection(recipes = recipes, stats = familyStats)
        }

        val userRecipeRankings by viewModel.userRecipeRankings.collectAsState()
        if (userRecipeRankings.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            UserRecipeRankingSection(rankings = userRecipeRankings)
        }

        val familyMembers by viewModel.familyMembers.collectAsState()
        val onlineUserIds by viewModel.onlineUserIds.collectAsState()
        if (familyMembers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            FamilyMembersSection(
                members = familyMembers,
                onlineUserIds = onlineUserIds,
                isAdmin = isAdmin,
                myUserId = myUserId,
                onChangeRole = { member, newRole ->
                    memberRoleChange = MemberRoleChange(member, newRole)
                },
                onEditMember = { member ->
                    memberToEdit = member
                },
                onRemoveMember = { member ->
                    memberToRemove = member
                },
                onMessageMember = onMessageMember
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Servidor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = serverUrlInput,
            onValueChange = {
                serverUrlInput = it
                serverUrlError = null
            },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        serverUrlError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    serverUrlError = null
                    viewModel.resetServerBaseUrl()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Por defecto")
            }
            Button(
                onClick = {
                    viewModel.saveServerBaseUrl(serverUrlInput) { serverUrlError = it }
                },
                modifier = Modifier.weight(1f),
                enabled = serverUrlInput.isNotBlank()
            ) {
                Text("Guardar")
            }
        }

        if (isAdmin) {
            Spacer(Modifier.height(Spacing.lg))
            OutlinedButton(
                onClick  = { showInvite = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Invitar miembro a la familia")
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Cuenta",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        when (emailVerified) {
            true -> Text(
                "✓ Correo verificado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            false -> {
                Text(
                    "Tu correo aún no está verificado. Verifícalo para poder recuperar tu cuenta si olvidas la contraseña.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.requestEmailVerification() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Enviar correo") }
                    OutlinedButton(
                        onClick = { showVerifyCode = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ya tengo el código") }
                }
            }
            null -> Text(
                "Estado del correo no disponible sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { showDeleteAccount = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Eliminar cuenta")
        }

        Spacer(Modifier.height(Spacing.xl))

        // ── Version instalada y busqueda de actualizaciones ──────────────────
        Text(
            "Aplicación",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Versión instalada: ${org.gipsybuho.recetasfamiliares.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = {
                viewModel.checkForUpdateManually(
                    org.gipsybuho.recetasfamiliares.BuildConfig.VERSION_NAME
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buscar actualizaciones")
        }
        OutlinedButton(
            onClick = { showHelpCenter = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Centro de ayuda")
        }

        Spacer(Modifier.height(Spacing.xl))

        Button(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor   = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("Cerrar sesión")
        }
        OutlinedButton(
            onClick = { (context as? android.app.Activity)?.finish() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salir de la aplicación")
        }
    }
}

@Composable
private fun FamilySelectorDialog(
    families: List<FamilyDto>,
    activeFamilyId: String?,
    onDismiss: () -> Unit,
    onSelect: (FamilyDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar familia activa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                families.forEach { family ->
                    val selected = family.id == activeFamilyId
                    if (selected) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${family.name} · actual")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(family) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(family.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

private data class MemberRoleChange(val member: FamilyMemberDto, val newRole: String)

private enum class MemberPasswordAction(val apiValue: String?, val label: String) {
    NONE(null, "No cambiar"),
    SEND_RESET("SEND_RESET", "Enviar email de recuperación"),
    SET_TEMPORARY("SET_TEMPORARY", "Definir temporal")
}

@Composable
private fun CreateFamilyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear familia") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la nueva familia") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun FamilyStatsSection(
    recipes: List<RecipeEntity>,
    stats: FamilyStatsDto?
) {
    // Con red usa /stats del servidor; offline cae al cache local de recetas
    val totalRecipes = stats?.totalRecipes ?: recipes.size.toLong()
    val lastActivity = stats?.lastActivityAt?.take(10)
        ?: recipes.maxByOrNull { it.updatedAt }?.updatedAt?.take(10)
        ?: "—"
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape     = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "Familia",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                StatItem(
                    modifier = Modifier.weight(1f),
                    value    = "$totalRecipes",
                    label    = if (totalRecipes == 1L) "receta" else "recetas"
                )
                if (stats != null) {
                    StatItem(
                        modifier = Modifier.weight(1f),
                        value    = "${stats.totalMembers}",
                        label    = if (stats.totalMembers == 1L) "miembro" else "miembros"
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        value    = "${stats.totalStockItems}",
                        label    = "en despensa"
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            StatItem(
                modifier = Modifier.fillMaxWidth(),
                value    = lastActivity,
                label    = "última actividad"
            )
        }
    }
}

@Composable
private fun UserRecipeRankingSection(rankings: List<UserRecipeRankingDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Ranking de recetas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rankings.take(10).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                item.rank.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.recipesCreated} recetas · ${item.ratingsReceived} valoraciones · ${averageLabel(item.averageStars)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${item.score} pts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyMembersSection(
    members: List<FamilyMemberDto>,
    onlineUserIds: Set<String>,
    isAdmin: Boolean,
    myUserId: String?,
    onChangeRole: (FamilyMemberDto, String) -> Unit,
    onEditMember: (FamilyMemberDto) -> Unit,
    onRemoveMember: (FamilyMemberDto) -> Unit,
    onMessageMember: (FamilyMemberDto) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape    = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Miembros (${members.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            members.forEach { member ->
                val role = member.role?.uppercase() ?: "MEMBER"
                val canManage = isAdmin && member.userId != myUserId && role != "OWNER"
                var menuExpanded by remember(member.userId) { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canManage) { onEditMember(member) }
                ) {
                    Box {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!member.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member.avatarUrl,
                                        contentDescription = "Foto de ${member.displayName}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        member.displayName.split(" ").filter { it.isNotBlank() }.take(2)
                                            .map { it.first().uppercaseChar() }.joinToString(""),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        val online = member.userId in onlineUserIds
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .background(
                                    color = if (online) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .semantics {
                                    contentDescription = if (online) "En línea" else "Desconectado"
                                }
                        )
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            member.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        familyRoleLabel(role),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (member.userId != myUserId) {
                        IconButton(onClick = { onMessageMember(member) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = "Enviar mensaje privado a ${member.displayName}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (canManage) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Gestionar ${member.displayName}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onEditMember(member)
                                    }
                                )
                                val newRole = if (role == "ADMIN") "MEMBER" else "ADMIN"
                                DropdownMenuItem(
                                    text = { Text(if (newRole == "ADMIN") "Hacer administrador" else "Hacer miembro") },
                                    onClick = {
                                        menuExpanded = false
                                        onChangeRole(member, newRole)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Expulsar",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveMember(member)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMemberDialog(
    member: FamilyMemberDto,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?) -> Unit
) {
    var displayName by remember(member.userId) { mutableStateOf(member.displayName) }
    var email by remember(member.userId) { mutableStateOf(member.email) }
    var passwordAction by remember(member.userId) { mutableStateOf(MemberPasswordAction.NONE) }
    var passwordExpanded by remember { mutableStateOf(false) }
    var temporaryPassword by remember(member.userId) { mutableStateOf("") }
    val passwordValid = passwordAction != MemberPasswordAction.SET_TEMPORARY || temporaryPassword.length >= 12
    val canSave = displayName.isNotBlank() && email.contains("@") && passwordValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar miembro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = passwordExpanded,
                    onExpandedChange = { passwordExpanded = it }
                ) {
                    OutlinedTextField(
                        value = passwordAction.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Contraseña") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(passwordExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = passwordExpanded,
                        onDismissRequest = { passwordExpanded = false }
                    ) {
                        MemberPasswordAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    passwordAction = action
                                    if (action != MemberPasswordAction.SET_TEMPORARY) {
                                        temporaryPassword = ""
                                    }
                                    passwordExpanded = false
                                }
                            )
                        }
                    }
                }
                if (passwordAction == MemberPasswordAction.SET_TEMPORARY) {
                    OutlinedTextField(
                        value = temporaryPassword,
                        onValueChange = { temporaryPassword = it },
                        label = { Text("Contraseña temporal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (temporaryPassword.isNotBlank() && temporaryPassword.length < 12) {
                        Text(
                            "Mínimo 12 caracteres",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        displayName.trim(),
                        email.trim(),
                        passwordAction.apiValue,
                        temporaryPassword.takeIf { passwordAction == MemberPasswordAction.SET_TEMPORARY }
                    )
                },
                enabled = canSave
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ChangeMemberRoleDialog(
    member: FamilyMemberDto,
    newRole: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val targetLabel = familyRoleLabel(newRole).lowercase()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar rol") },
        text = {
            Text("¿Cambiar el rol de ${member.displayName} a $targetLabel?")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Cambiar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar sesión") },
        text = {
            Text("Se cerrará tu sesión y se borrarán los datos guardados en este dispositivo, " +
                "incluidos los cambios que aún no se hayan sincronizado. " +
                "Tus recetas y datos familiares siguen a salvo en el servidor.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Cerrar sesión")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RemoveMemberDialog(
    member: FamilyMemberDto,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Expulsar miembro") },
        text = {
            Text("¿Expulsar a ${member.displayName} de la familia? Esta acción no se puede deshacer.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Expulsar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun familyRoleLabel(role: String?): String =
    when (role?.uppercase()) {
        "OWNER" -> "Propietario"
        "ADMIN" -> "Admin"
        else -> "Miembro"
    }

private fun averageLabel(averageStars: Double?): String =
    if (averageStars == null) "sin media"
    else "media ${String.format(Locale.getDefault(), "%.1f", averageStars)}"

@Composable
private fun StatItem(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        modifier = modifier,
        shape    = MaterialTheme.shapes.medium,
        color    = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier            = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style   = MaterialTheme.typography.labelSmall,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VerifyEmailCodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verificar correo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Pega el código que has recibido por correo.")
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("Código del correo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code.trim()) }, enabled = code.isNotBlank()) {
                Text("Verificar")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteAccountDialog(viewModel: RecetasViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Eliminar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Vas a eliminar tu cuenta de forma permanente. Perderás el acceso y " +
                        "tus datos personales se anonimizarán. Esta acción no se puede deshacer."
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña actual") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    dialogError = null
                    working = true
                    // Si el borrado triunfa, el ViewModel hace logout y la app vuelve al login
                    viewModel.deleteAccount(password) {
                        working = false
                        dialogError = it
                    }
                },
                enabled = !working && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Eliminar cuenta") }
        },
        dismissButton = {
            OutlinedButton(enabled = !working, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMemberDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val roles = listOf("MEMBER" to "Miembro", "ADMIN" to "Administrador")
    var inviteEmail  by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(roles[0]) }
    var roleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text("Invitar miembro") },
        text   = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value         = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label         = { Text("Email del miembro") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier      = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded         = roleExpanded,
                    onExpandedChange = { roleExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = selectedRole.second,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Rol") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded         = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text    = { Text(role.second) },
                                onClick = { selectedRole = role; roleExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(inviteEmail.trim(), selectedRole.first) },
                enabled = inviteEmail.isNotBlank()
            ) { Text("Invitar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
