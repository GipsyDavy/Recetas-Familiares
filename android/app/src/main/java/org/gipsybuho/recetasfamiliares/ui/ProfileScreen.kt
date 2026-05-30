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
    var editing     by remember { mutableStateOf(false) }
    var editName    by remember { mutableStateOf("") }
    var showInvite  by remember { mutableStateOf(false) }
    val context     = LocalContext.current

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
