package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

public class RecipeDetailView extends VBox {

    private final AppContext context;
    private final Text titleText = new Text();
    private final Text metaText = new Text();
    private final Text descText = new Text();
    private final VBox ingredientsList = new VBox(6);
    private final VBox stepsList = new VBox(10);
    private final Label statusLabel = new Label();

    public RecipeDetailView(AppContext context) {
        this.context = context;
        build();
    }

    private void build() {
        getStyleClass().add("detail-view");
        setSpacing(16);
        setPadding(new Insets(24));

        titleText.getStyleClass().add("recipe-title");
        metaText.getStyleClass().add("recipe-meta");
        descText.getStyleClass().add("recipe-desc");
        descText.setWrappingWidth(520);

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
        content.setPadding(new Insets(8));
        scroll.setContent(content);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().add(scroll);
    }

    public void load(RecipeDtos.RecipeDto recipe) {
        titleText.setText(recipe.title());
        String meta = buildMeta(recipe);
        metaText.setText(meta);
        descText.setText(recipe.description() != null ? recipe.description() : "");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        statusLabel.setText("Cargando...");
        statusLabel.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                var ingredients = context.getRecipeRepository().loadIngredients(recipe.id());
                var steps = context.getRecipeRepository().loadSteps(recipe.id());
                Platform.runLater(() -> {
                    statusLabel.setVisible(false);
                    for (var ing : ingredients) {
                        Label l = new Label("• " + ing.name() + " — " + ing.quantity() + " " + safeUnit(ing.unit()));
                        l.getStyleClass().add("ingredient-item");
                        ingredientsList.getChildren().add(l);
                    }
                    int num = 1;
                    for (var step : steps) {
                        VBox stepBox = new VBox(4);
                        stepBox.getStyleClass().add("step-box");
                        Label numLabel = new Label("Paso " + num++);
                        numLabel.getStyleClass().add("step-number");
                        Label desc = new Label(step.description());
                        desc.setWrapText(true);
                        desc.getStyleClass().add("step-desc");
                        stepBox.getChildren().addAll(numLabel, desc);
                        stepsList.getChildren().add(stepBox);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudieron cargar los detalles.");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    public void showEmpty() {
        titleText.setText("Selecciona una receta");
        metaText.setText("");
        descText.setText("");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        statusLabel.setVisible(false);
    }

    private String buildMeta(RecipeDtos.RecipeDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.servings() != null) sb.append(r.servings()).append(" personas  ");
        if (r.prepMinutes() != null) sb.append("Prep: ").append(r.prepMinutes()).append(" min  ");
        if (r.cookMinutes() != null) sb.append("Cocción: ").append(r.cookMinutes()).append(" min  ");
        if (r.difficulty() != null) sb.append(r.difficulty());
        return sb.toString().trim();
    }

    private String safeUnit(String unit) {
        return unit != null ? unit : "";
    }
}
