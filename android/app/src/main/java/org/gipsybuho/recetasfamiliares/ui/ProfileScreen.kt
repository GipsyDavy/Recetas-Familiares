package org.gipsybuho.recetasfamiliares.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyStatsDto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(viewModel: RecetasViewModel, modifier: Modifier = Modifier) {
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
    val emailVerified by viewModel.emailVerified.collectAsState()
    val context     = LocalContext.current

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

    if (showInvite) {
        InviteMemberDialog(
            onDismiss = { showInvite = false },
            onConfirm = { inviteEmail, role ->
                viewModel.inviteMember(inviteEmail, role)
                showInvite = false
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
            viewModel.loadAccountStatus()
        }
        val recipes by viewModel.recipes.collectAsState()
        val familyStats by viewModel.familyStats.collectAsState()
        if (familyStats != null || recipes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            FamilyStatsSection(recipes = recipes, stats = familyStats)
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

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor   = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("Cerrar sesión")
        }
    }
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
