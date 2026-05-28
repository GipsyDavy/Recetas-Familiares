package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto

private data class IngredientDraft(val name: String = "", val quantity: String = "", val unit: String = "")
private data class StepDraft(val instruction: String = "", val timerMinutes: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipeForm(
    initial: RecipeEntity?,
    initialIngredients: List<RecipeIngredientEntity>,
    initialSteps: List<RecipeStepEntity>,
    viewModel: RecetasViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var servings by remember(initial) { mutableStateOf(initial?.servings?.toString() ?: "") }
    var prepMinutes by remember(initial) { mutableStateOf(initial?.prepMinutes?.toString() ?: "") }
    var cookMinutes by remember(initial) { mutableStateOf(initial?.cookMinutes?.toString() ?: "") }
    var difficulty by remember(initial) { mutableStateOf(initial?.difficulty ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val ingredients: SnapshotStateList<IngredientDraft> = remember(initialIngredients) {
        if (initialIngredients.isNotEmpty())
            initialIngredients.map { IngredientDraft(it.name, it.quantity?.toString() ?: "", it.unit ?: "") }.toMutableStateList()
        else mutableListOf(IngredientDraft()).toMutableStateList()
    }
    val steps: SnapshotStateList<StepDraft> = remember(initialSteps) {
        if (initialSteps.isNotEmpty())
            initialSteps.map { StepDraft(it.instruction, it.timerMinutes?.toString() ?: "") }.toMutableStateList()
        else mutableListOf(StepDraft()).toMutableStateList()
    }

    val difficulties = listOf("EASY" to "Fácil", "MEDIUM" to "Media", "HARD" to "Difícil")

    LazyColumn(
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            Text(if (initial == null) "Nueva receta" else "Editar receta", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(value = title, onValueChange = { title = it; titleError = false },
                label = { Text("Título *") }, isError = titleError, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = description, onValueChange = { description = it },
                label = { Text("Descripción") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(value = servings, onValueChange = { servings = it },
                    label = { Text("Porciones") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = prepMinutes, onValueChange = { prepMinutes = it },
                    label = { Text("Prep (min)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = cookMinutes, onValueChange = { cookMinutes = it },
                    label = { Text("Cocción (min)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                difficulties.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = difficulty == value,
                        onClick = { difficulty = if (difficulty == value) "" else value },
                        shape = SegmentedButtonDefaults.itemShape(index, difficulties.size)
                    ) { Text(label) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ingredientes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { ingredients.add(IngredientDraft()) }) { Text("+ Añadir") }
            }
        }
        items(ingredients.size) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = ingredients[i].name,
                    onValueChange = { ingredients[i] = ingredients[i].copy(name = it) },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(value = ingredients[i].quantity,
                    onValueChange = { ingredients[i] = ingredients[i].copy(quantity = it) },
                    label = { Text("Cant.") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredients[i].unit,
                    onValueChange = { ingredients[i] = ingredients[i].copy(unit = it) },
                    label = { Text("Ud.") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(i) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Eliminar fila", modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Pasos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { steps.add(StepDraft()) }) { Text("+ Añadir") }
            }
        }
        items(steps.size) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                OutlinedTextField(value = steps[i].instruction,
                    onValueChange = { steps[i] = steps[i].copy(instruction = it) },
                    label = { Text("Instrucción") }, maxLines = 3, modifier = Modifier.weight(1f))
                OutlinedTextField(value = steps[i].timerMinutes,
                    onValueChange = { steps[i] = steps[i].copy(timerMinutes = it) },
                    label = { Text("Min.") }, singleLine = true, modifier = Modifier.width(70.dp))
                IconButton(onClick = { if (steps.size > 1) steps.removeAt(i) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Eliminar fila", modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = {
                    if (title.isBlank()) { titleError = true; return@Button }
                    val ingDtos = ingredients.filter { it.name.isNotBlank() }
                        .map { RecipeIngredientItemDto(it.name.trim(), it.quantity.toDoubleOrNull(), it.unit.trim().ifBlank { null }, null) }
                    val stepDtos = steps.filter { it.instruction.isNotBlank() }
                        .map { RecipeStepItemDto(it.instruction.trim(), it.timerMinutes.toIntOrNull()) }
                    if (initial == null) {
                        viewModel.createRecipe(title.trim(), description.trim().ifBlank { null },
                            servings.toIntOrNull(), prepMinutes.toIntOrNull(), cookMinutes.toIntOrNull(),
                            difficulty.ifBlank { null }, ingDtos, stepDtos) { error = it }
                    } else {
                        viewModel.updateRecipe(initial, title.trim(), description.trim().ifBlank { null },
                            servings.toIntOrNull(), prepMinutes.toIntOrNull(), cookMinutes.toIntOrNull(),
                            difficulty.ifBlank { null }, ingDtos, stepDtos) { error = it }
                    }
                    onSaved()
                }) { Text(if (initial == null) "Guardar receta" else "Guardar cambios") }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}
