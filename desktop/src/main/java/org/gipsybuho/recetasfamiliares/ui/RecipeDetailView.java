package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.List;

public class RecipeDetailView extends VBox {

    private final AppContext context;
    private final Runnable onRecipeChanged;   // called after edit or delete so list refreshes

    private final Text titleText = new Text();
    private final Text metaText = new Text();
    private final Text descText = new Text();
    private final VBox ingredientsList = new VBox(6);
    private final VBox stepsList = new VBox(10);
    private final Label statusLabel = new Label();
    private final HBox actionBar = new HBox(10);

    private RecipeDtos.RecipeDto currentRecipe;

    public RecipeDetailView(AppContext context, Runnable onRecipeChanged) {
        this.context = context;
        this.onRecipeChanged = onRecipeChanged;
        build();
    }

    private void build() {
        getStyleClass().add("detail-view");
        setSpacing(0);
        setPadding(new Insets(0));

        titleText.getStyleClass().add("recipe-title");
        metaText.getStyleClass().add("recipe-meta");
        descText.getStyleClass().add("recipe-desc");
        descText.setWrappingWidth(480);

        // Action bar (edit + delete) — hidden until a recipe is selected
        Button editBtn = new Button("Editar");
        editBtn.getStyleClass().add("action-button-secondary");
        editBtn.setOnAction(e -> openEditForm());

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().addAll("action-button-secondary", "delete-button");
        deleteBtn.setOnAction(e -> confirmDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actionBar.getChildren().addAll(spacer, editBtn, deleteBtn);
        actionBar.setPadding(new Insets(12, 16, 8, 16));
        actionBar.setVisible(false);
        actionBar.setManaged(false);

        Label ingLabel = new Label("Ingredientes");
        ingLabel.getStyleClass().add("section-header");

        Label stepsLabel = new Label("Pasos");
        stepsLabel.getStyleClass().add("section-header");

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setVisible(false);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox content = new VBox(16,
                titleText, metaText, descText,
                new Separator(),
                ingLabel, ingredientsList,
                new Separator(),
                stepsLabel, stepsList,
                statusLabel
        );
        content.setPadding(new Insets(16, 24, 24, 24));
        scroll.setContent(content);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(actionBar, scroll);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void load(RecipeDtos.RecipeDto recipe) {
        currentRecipe = recipe;
        titleText.setText(recipe.title());
        metaText.setText(buildMeta(recipe));
        descText.setText(recipe.description() != null ? recipe.description() : "");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        statusLabel.setText("Cargando...");
        statusLabel.setVisible(true);
        actionBar.setVisible(true);
        actionBar.setManaged(true);

        Thread.ofVirtual().start(() -> {
            try {
                var ingredients = context.getRecipeRepository().loadIngredients(recipe.id());
                var steps = context.getRecipeRepository().loadSteps(recipe.id());
                Platform.runLater(() -> renderContent(ingredients, steps));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudieron cargar los detalles.");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    public void showEmpty() {
        currentRecipe = null;
        titleText.setText("Selecciona una receta");
        metaText.setText("");
        descText.setText("");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        statusLabel.setVisible(false);
        actionBar.setVisible(false);
        actionBar.setManaged(false);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void renderContent(List<RecipeDtos.RecipeIngredientDto> ingredients,
                               List<RecipeDtos.RecipeStepDto> steps) {
        statusLabel.setVisible(false);
        ingredientsList.getChildren().clear();
        for (var ing : ingredients) {
            String qty = ing.quantity() != null ? ing.quantity() : "";
            String unit = ing.unit() != null ? " " + ing.unit() : "";
            Label l = new Label("• " + ing.name() + (qty.isBlank() ? "" : " — " + qty + unit));
            l.getStyleClass().add("ingredient-item");
            ingredientsList.getChildren().add(l);
        }
        if (ingredients.isEmpty())
            ingredientsList.getChildren().add(noDataLabel("Sin ingredientes."));

        stepsList.getChildren().clear();
        int num = 1;
        for (var step : steps) {
            VBox stepBox = new VBox(4);
            stepBox.getStyleClass().add("step-box");
            Label numLabel = new Label("Paso " + num++);
            numLabel.getStyleClass().add("step-number");
            Label desc = new Label(step.description());
            desc.setWrapText(true);
            desc.getStyleClass().add("step-desc");
            if (step.durationMinutes() != null) {
                Label dur = new Label(step.durationMinutes() + " min");
                dur.getStyleClass().add("recipe-meta");
                stepBox.getChildren().addAll(numLabel, desc, dur);
            } else {
                stepBox.getChildren().addAll(numLabel, desc);
            }
            stepsList.getChildren().add(stepBox);
        }
        if (steps.isEmpty())
            stepsList.getChildren().add(noDataLabel("Sin pasos."));
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    private void openEditForm() {
        if (currentRecipe == null) return;
        statusLabel.setText("Cargando datos para editar...");
        statusLabel.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                var ingredients = context.getRecipeRepository().loadIngredients(currentRecipe.id());
                var steps = context.getRecipeRepository().loadSteps(currentRecipe.id());
                Platform.runLater(() -> {
                    statusLabel.setVisible(false);
                    var dialog = RecipeFormDialog.forEdit(
                            getScene().getWindow(), context,
                            currentRecipe, ingredients, steps,
                            updated -> {
                                load(updated);
                                onRecipeChanged.run();
                            }
                    );
                    dialog.show();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudieron cargar los datos.");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void confirmDelete() {
        if (currentRecipe == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar receta");
        confirm.setHeaderText("¿Eliminar \"" + currentRecipe.title() + "\"?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) doDelete();
        });
    }

    private void doDelete() {
        String recipeId = currentRecipe.id();
        statusLabel.setText("Eliminando...");
        statusLabel.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                context.getRecipeRepository().delete(recipeId);
                Platform.runLater(() -> {
                    showEmpty();
                    onRecipeChanged.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al eliminar: " + ex.getMessage());
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Label noDataLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("no-data-label");
        return l;
    }

    private String buildMeta(RecipeDtos.RecipeDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.servings() != null) sb.append(r.servings()).append(" personas  ");
        if (r.prepMinutes() != null) sb.append("Prep: ").append(r.prepMinutes()).append(" min  ");
        if (r.cookMinutes() != null) sb.append("Cocción: ").append(r.cookMinutes()).append(" min  ");
        if (r.difficulty() != null) sb.append(r.difficulty());
        return sb.toString().trim();
    }
}
