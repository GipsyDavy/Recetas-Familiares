package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeCreateDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Modal dialog for creating a new recipe with basic metadata, ingredients and steps. */
public class RecipeFormDialog {

    private final Stage dialog;
    private final AppContext context;
    private final Consumer<RecipeDtos.RecipeDto> onCreated;

    // Metadata fields
    private final TextField titleField = new TextField();
    private final TextArea descriptionField = new TextArea();
    private final TextField servingsField = new TextField();
    private final TextField prepField = new TextField();
    private final TextField cookField = new TextField();
    private final ComboBox<String> difficultyBox = new ComboBox<>();

    // Ingredients
    private final VBox ingredientsContainer = new VBox(6);
    private final List<IngredientRow> ingredientRows = new ArrayList<>();

    // Steps
    private final VBox stepsContainer = new VBox(6);
    private final List<StepRow> stepRows = new ArrayList<>();

    private final Label statusLabel = new Label();

    public RecipeFormDialog(Window owner, AppContext context, Consumer<RecipeDtos.RecipeDto> onCreated) {
        this.context = context;
        this.onCreated = onCreated;
        this.dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Nueva receta");
        dialog.setMinWidth(560);
        dialog.setMinHeight(600);
        build();
    }

    public void show() {
        dialog.show();
    }

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

    // ── Metadata ─────────────────────────────────────────────────────────────

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

    // ── Ingredients ───────────────────────────────────────────────────────────

    private VBox buildIngredientsSection() {
        Button addBtn = new Button("+ Añadir ingrediente");
        addBtn.getStyleClass().add("form-add-button");
        addBtn.setOnAction(e -> addIngredientRow());

        addIngredientRow(); // Start with one empty row

        VBox wrapper = buildFormSection("Ingredientes", ingredientsContainer);
        wrapper.getChildren().add(addBtn);
        return wrapper;
    }

    private void addIngredientRow() {
        IngredientRow row = new IngredientRow();
        ingredientRows.add(row);
        ingredientsContainer.getChildren().add(row.build(() -> {
            ingredientRows.remove(row);
            ingredientsContainer.getChildren().remove(row.build(() -> {}));
        }));
    }

    // ── Steps ──────────────────────────────────────────────────────────────────

    private VBox buildStepsSection() {
        Button addBtn = new Button("+ Añadir paso");
        addBtn.getStyleClass().add("form-add-button");
        addBtn.setOnAction(e -> addStepRow());

        addStepRow(); // Start with one empty row

        VBox wrapper = buildFormSection("Pasos", stepsContainer);
        wrapper.getChildren().add(addBtn);
        return wrapper;
    }

    private void addStepRow() {
        int num = stepRows.size() + 1;
        StepRow row = new StepRow(num);
        stepRows.add(row);
        stepsContainer.getChildren().add(row.build(() -> {
            stepRows.remove(row);
            stepsContainer.getChildren().remove(row.build(() -> {}));
        }));
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status-label");

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("action-button-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("Guardar receta");
        saveBtn.getStyleClass().add("action-button-primary");
        saveBtn.setOnAction(e -> doSave(saveBtn));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(10, statusLabel, spacer, cancelBtn, saveBtn);
        footer.setPadding(new Insets(8, 0, 0, 0));
        return footer;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void doSave(Button saveBtn) {
        String title = titleField.getText().trim();
        if (title.isBlank()) {
            statusLabel.setText("El título es obligatorio.");
            return;
        }

        saveBtn.setDisable(true);
        statusLabel.setText("Guardando...");

        var request = new RecipeCreateDtos.CreateRecipeRequest(
                title,
                descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim(),
                parseIntOrNull(servingsField.getText()),
                parseIntOrNull(prepField.getText()),
                parseIntOrNull(cookField.getText()),
                difficultyBox.getValue()
        );

        Thread.ofVirtual().start(() -> {
            try {
                RecipeDtos.RecipeDto created = context.getRecipeRepository().create(request);

                List<RecipeCreateDtos.CreateIngredientRequest> ingredients = ingredientRows.stream()
                        .filter(r -> !r.getName().isBlank())
                        .map(r -> new RecipeCreateDtos.CreateIngredientRequest(
                                r.getName(), r.getQuantity(), r.getUnit(), ingredientRows.indexOf(r) + 1))
                        .toList();

                if (!ingredients.isEmpty()) {
                    context.getRecipeRepository().replaceIngredients(
                            created.id(), new RecipeCreateDtos.ReplaceIngredientsRequest(ingredients));
                }

                List<RecipeCreateDtos.CreateStepRequest> steps = stepRows.stream()
                        .filter(s -> !s.getDescription().isBlank())
                        .map(s -> new RecipeCreateDtos.CreateStepRequest(
                                stepRows.indexOf(s) + 1, s.getDescription(), parseIntOrNull(s.getDuration())))
                        .toList();

                if (!steps.isEmpty()) {
                    context.getRecipeRepository().replaceSteps(
                            created.id(), new RecipeCreateDtos.ReplaceStepsRequest(steps));
                }

                Platform.runLater(() -> {
                    onCreated.accept(created);
                    dialog.close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    statusLabel.setText("Error: " + ex.getMessage());
                });
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

        StepRow(int number) { this.number = number; }

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
