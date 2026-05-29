package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeCreateDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Modal dialog for creating or editing a recipe. */
public class RecipeFormDialog {

    // null = create mode; non-null = edit mode
    private final String editRecipeId;
    private final Stage dialog;
    private final AppContext context;
    private final Consumer<RecipeDtos.RecipeDto> onSaved;

    // Metadata fields
    private final TextField titleField = new TextField();
    private final TextArea descriptionField = new TextArea();
    private final TextField servingsField = new TextField();
    private final TextField prepField = new TextField();
    private final TextField cookField = new TextField();
    private final ComboBox<String> difficultyBox = new ComboBox<>();

    // Ingredients + steps
    private final VBox ingredientsContainer = new VBox(6);
    private final List<IngredientRow> ingredientRows = new ArrayList<>();
    private final VBox stepsContainer = new VBox(6);
    private final List<StepRow> stepRows = new ArrayList<>();

    private final Label statusLabel = new Label();

    // ── Factory methods ───────────────────────────────────────────────────────

    public static RecipeFormDialog forCreate(Window owner, AppContext context,
                                             Consumer<RecipeDtos.RecipeDto> onCreated) {
        return new RecipeFormDialog(owner, context, null, onCreated);
    }

    public static RecipeFormDialog forEdit(Window owner, AppContext context,
                                           RecipeDtos.RecipeDto recipe,
                                           List<RecipeDtos.RecipeIngredientDto> ingredients,
                                           List<RecipeDtos.RecipeStepDto> steps,
                                           Consumer<RecipeDtos.RecipeDto> onUpdated) {
        RecipeFormDialog dlg = new RecipeFormDialog(owner, context, recipe.id(), onUpdated);
        dlg.prefill(recipe, ingredients, steps);
        return dlg;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private RecipeFormDialog(Window owner, AppContext context,
                             String editRecipeId, Consumer<RecipeDtos.RecipeDto> onSaved) {
        this.context = context;
        this.editRecipeId = editRecipeId;
        this.onSaved = onSaved;
        this.dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(editRecipeId == null ? "Nueva receta" : "Editar receta");
        dialog.setMinWidth(560);
        dialog.setMinHeight(600);
        build();
        dialog.setOnShown(e -> {
            Node dialogRoot = dialog.getScene().getRoot();
            dialogRoot.setScaleX(0.95);
            dialogRoot.setScaleY(0.95);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), dialogRoot);
            st.setFromX(0.95);
            st.setFromY(0.95);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
    }

    public void show() {
        dialog.show();
    }

    // ── Pre-fill (edit mode) ──────────────────────────────────────────────────

    private void prefill(RecipeDtos.RecipeDto recipe,
                         List<RecipeDtos.RecipeIngredientDto> ingredients,
                         List<RecipeDtos.RecipeStepDto> steps) {
        titleField.setText(recipe.title() != null ? recipe.title() : "");
        descriptionField.setText(recipe.description() != null ? recipe.description() : "");
        if (recipe.servings() != null) servingsField.setText(recipe.servings().toString());
        if (recipe.prepMinutes() != null) prepField.setText(recipe.prepMinutes().toString());
        if (recipe.cookMinutes() != null) cookField.setText(recipe.cookMinutes().toString());
        if (recipe.difficulty() != null) difficultyBox.setValue(recipe.difficulty());

        // Replace the default empty rows with server data
        ingredientRows.clear();
        ingredientsContainer.getChildren().clear();
        if (ingredients != null && !ingredients.isEmpty()) {
            for (var ing : ingredients) addIngredientRow(ing.name(),
                    ing.quantity() != null ? ing.quantity().toString() : "", ing.unit());
        } else {
            addIngredientRow(null, null, null);
        }

        stepRows.clear();
        stepsContainer.getChildren().clear();
        if (steps != null && !steps.isEmpty()) {
            for (var step : steps) addStepRow(step.instruction(),
                    step.timerMinutes() != null ? step.timerMinutes().toString() : "");
        } else {
            addStepRow(null, null);
        }
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void build() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("form-content");
        content.getChildren().addAll(
                buildMetadataSection(),
                buildIngredientsSection(),
                buildStepsSection(),
                buildFooter()
        );
        scroll.setContent(content);

        Scene scene = new Scene(scroll, 580, 680);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        dialog.setScene(scene);
    }

    private VBox buildMetadataSection() {
        titleField.setPromptText("Título de la receta *");
        titleField.getStyleClass().add("form-field");

        descriptionField.setPromptText("Descripción (opcional)");
        descriptionField.setPrefRowCount(3);
        descriptionField.setWrapText(true);
        descriptionField.getStyleClass().add("form-field");

        servingsField.setPromptText("Porciones");
        servingsField.getStyleClass().add("form-field-small");
        prepField.setPromptText("Prep. (min)");
        prepField.getStyleClass().add("form-field-small");
        cookField.setPromptText("Cocción (min)");
        cookField.getStyleClass().add("form-field-small");

        difficultyBox.getItems().addAll("EASY", "MEDIUM", "HARD");
        difficultyBox.setPromptText("Dificultad");
        difficultyBox.getStyleClass().add("form-field-small");

        HBox numericRow = new HBox(10, servingsField, prepField, cookField, difficultyBox);
        numericRow.setFillHeight(true);
        HBox.setHgrow(servingsField, Priority.ALWAYS);
        HBox.setHgrow(prepField, Priority.ALWAYS);
        HBox.setHgrow(cookField, Priority.ALWAYS);
        HBox.setHgrow(difficultyBox, Priority.ALWAYS);

        return buildFormSection("Información básica", titleField, descriptionField, numericRow);
    }

    private VBox buildIngredientsSection() {
        Button addBtn = new Button("+ Añadir ingrediente");
        addBtn.getStyleClass().add("form-add-button");
        addBtn.setOnAction(e -> addIngredientRow(null, null, null));

        if (editRecipeId == null) addIngredientRow(null, null, null); // create: start with one empty

        VBox wrapper = buildFormSection("Ingredientes", ingredientsContainer);
        wrapper.getChildren().add(addBtn);
        return wrapper;
    }

    private VBox buildStepsSection() {
        Button addBtn = new Button("+ Añadir paso");
        addBtn.getStyleClass().add("form-add-button");
        addBtn.setOnAction(e -> addStepRow(null, null));

        if (editRecipeId == null) addStepRow(null, null); // create: start with one empty

        VBox wrapper = buildFormSection("Pasos", stepsContainer);
        wrapper.getChildren().add(addBtn);
        return wrapper;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status-label");

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("action-button-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        String saveLabel = editRecipeId == null ? "Guardar receta" : "Guardar cambios";
        Button saveBtn = new Button(saveLabel);
        saveBtn.getStyleClass().add("action-button-primary");
        saveBtn.setOnAction(e -> doSave(saveBtn));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(10, statusLabel, spacer, cancelBtn, saveBtn);
        footer.setPadding(new Insets(8, 0, 0, 0));
        return footer;
    }

    // ── Row helpers ───────────────────────────────────────────────────────────

    private void addIngredientRow(String name, String qty, String unit) {
        IngredientRow row = new IngredientRow(name, qty, unit);
        ingredientRows.add(row);
        ingredientsContainer.getChildren().add(row.build(() -> {
            int idx = ingredientRows.indexOf(row);
            ingredientRows.remove(row);
            if (idx >= 0 && idx < ingredientsContainer.getChildren().size())
                ingredientsContainer.getChildren().remove(idx);
        }));
    }

    private void addStepRow(String description, String duration) {
        int num = stepRows.size() + 1;
        StepRow row = new StepRow(num, description, duration);
        stepRows.add(row);
        stepsContainer.getChildren().add(row.build(() -> {
            int idx = stepRows.indexOf(row);
            stepRows.remove(row);
            if (idx >= 0 && idx < stepsContainer.getChildren().size())
                stepsContainer.getChildren().remove(idx);
        }));
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void doSave(Button saveBtn) {
        String title = titleField.getText().trim();
        if (title.isBlank()) { statusLabel.setText("El título es obligatorio."); return; }

        saveBtn.setDisable(true);
        statusLabel.setText("Guardando...");

        String desc = descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim();
        Integer servings = parseIntOrNull(servingsField.getText());
        Integer prep = parseIntOrNull(prepField.getText());
        Integer cook = parseIntOrNull(cookField.getText());
        String diff = difficultyBox.getValue();

        List<RecipeCreateDtos.CreateIngredientRequest> ingredients = ingredientRows.stream()
                .filter(r -> !r.getName().isBlank())
                .map(r -> new RecipeCreateDtos.CreateIngredientRequest(
                        r.getName(), r.getQuantity(), r.getUnit(), ingredientRows.indexOf(r) + 1))
                .toList();

        List<RecipeCreateDtos.CreateStepRequest> steps = stepRows.stream()
                .filter(s -> !s.getDescription().isBlank())
                .map(s -> new RecipeCreateDtos.CreateStepRequest(
                        stepRows.indexOf(s) + 1, s.getDescription(), parseIntOrNull(s.getDuration())))
                .toList();

        Thread.ofVirtual().start(() -> {
            try {
                RecipeDtos.RecipeDto saved;
                if (editRecipeId == null) {
                    // Create
                    saved = context.getRecipeRepository().create(
                            new RecipeCreateDtos.CreateRecipeRequest(title, desc, servings, prep, cook, diff));
                } else {
                    // Update
                    saved = context.getRecipeRepository().update(editRecipeId,
                            new RecipeCreateDtos.UpdateRecipeRequest(title, desc, servings, prep, cook, diff));
                }

                if (!ingredients.isEmpty())
                    context.getRecipeRepository().replaceIngredients(saved.id(),
                            new RecipeCreateDtos.ReplaceIngredientsRequest(ingredients));

                if (!steps.isEmpty())
                    context.getRecipeRepository().replaceSteps(saved.id(),
                            new RecipeCreateDtos.ReplaceStepsRequest(steps));

                Platform.runLater(() -> { onSaved.accept(saved); dialog.close(); });
            } catch (Exception ex) {
                Platform.runLater(() -> { saveBtn.setDisable(false); statusLabel.setText("Error: " + ex.getMessage()); });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox buildFormSection(String title, javafx.scene.Node... nodes) {
        Label header = new Label(title);
        header.getStyleClass().add("form-section-header");
        VBox section = new VBox(10);
        section.getStyleClass().add("form-section");
        section.setPadding(new Insets(16));
        section.getChildren().add(header);
        section.getChildren().addAll(nodes);
        return section;
    }

    private Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    // ── Inner row types ───────────────────────────────────────────────────────

    private static class IngredientRow {
        private final TextField nameField = new TextField();
        private final TextField qtyField = new TextField();
        private final TextField unitField = new TextField();
        private HBox node;

        IngredientRow(String name, String qty, String unit) {
            if (name != null) nameField.setText(name);
            if (qty != null) qtyField.setText(qty);
            if (unit != null) unitField.setText(unit);
        }

        HBox build(Runnable onRemove) {
            if (node != null) return node;
            nameField.setPromptText("Nombre *");
            nameField.getStyleClass().add("form-field");
            HBox.setHgrow(nameField, Priority.ALWAYS);
            qtyField.setPromptText("Cant.");
            qtyField.setPrefWidth(70);
            qtyField.getStyleClass().add("form-field-small");
            unitField.setPromptText("Ud.");
            unitField.setPrefWidth(60);
            unitField.getStyleClass().add("form-field-small");
            Button remove = new Button("×");
            remove.getStyleClass().add("form-remove-button");
            remove.setOnAction(e -> onRemove.run());
            node = new HBox(8, nameField, qtyField, unitField, remove);
            return node;
        }

        String getName() { return nameField.getText().trim(); }
        String getQuantity() { return qtyField.getText().trim(); }
        String getUnit() { return unitField.getText().trim().isEmpty() ? null : unitField.getText().trim(); }
    }

    private static class StepRow {
        private final int number;
        private final TextArea descArea = new TextArea();
        private final TextField durationField = new TextField();
        private HBox node;

        StepRow(int number, String description, String duration) {
            this.number = number;
            if (description != null) descArea.setText(description);
            if (duration != null) durationField.setText(duration);
        }

        HBox build(Runnable onRemove) {
            if (node != null) return node;
            Label numLabel = new Label(number + ".");
            numLabel.getStyleClass().add("step-number");
            numLabel.setMinWidth(20);
            descArea.setPromptText("Descripción del paso *");
            descArea.setPrefRowCount(2);
            descArea.setWrapText(true);
            descArea.getStyleClass().add("form-field");
            HBox.setHgrow(descArea, Priority.ALWAYS);
            durationField.setPromptText("Min.");
            durationField.setPrefWidth(60);
            durationField.getStyleClass().add("form-field-small");
            Button remove = new Button("×");
            remove.getStyleClass().add("form-remove-button");
            remove.setOnAction(e -> onRemove.run());
            node = new HBox(8, numLabel, descArea, durationField, remove);
            return node;
        }

        String getDescription() { return descArea.getText().trim(); }
        String getDuration() { return durationField.getText().trim(); }
    }
}
