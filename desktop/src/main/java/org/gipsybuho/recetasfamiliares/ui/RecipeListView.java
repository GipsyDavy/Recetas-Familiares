package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

public class RecipeListView extends SplitPane {

    private final AppContext context;
    private final ListView<RecipeDtos.RecipeDto> listView = new ListView<>();
    private final RecipeDetailView detailView;
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label();

    public RecipeListView(AppContext context) {
        this.context = context;
        this.detailView = new RecipeDetailView(context, this::refresh);
        build();
    }

    private void build() {
        getStyleClass().add("recipe-list-view");

        // Left panel: search + list
        searchField.setPromptText("Buscar recetas...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterList(newVal));

        Button newRecipeBtn = new Button("+ Nueva receta");
        newRecipeBtn.getStyleClass().add("action-button-primary");
        newRecipeBtn.setMaxWidth(Double.MAX_VALUE);
        newRecipeBtn.setOnAction(e -> openNewRecipeForm());

        listView.getStyleClass().add("recipe-list");
        listView.setCellFactory(lv -> new RecipeCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, recipe) -> {
            if (recipe != null) detailView.load(recipe);
            else detailView.showEmpty();
        });

        // Bind to cache — updates automatically after sync
        listView.setItems(context.getRecipeRepository().getCache().getItems());

        statusLabel.getStyleClass().add("status-label");

        VBox leftPanel = new VBox(10, searchField, newRecipeBtn, listView, statusLabel);
        leftPanel.setPadding(new Insets(16));
        VBox.setVgrow(listView, Priority.ALWAYS);
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(340);

        detailView.showEmpty();

        getItems().addAll(leftPanel, detailView);
        setDividerPositions(0.35);
    }

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(0, 100);
                Platform.runLater(() -> {
                    context.getRecipeRepository().getCache().replaceAll(
                            page.items().stream().filter(r -> !r.deleted()).toList());
                    statusLabel.setText(page.totalItems() + " recetas");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar recetas."));
            }
        });
    }

    private void openNewRecipeForm() {
        var dialog = RecipeFormDialog.forCreate(
                getScene().getWindow(),
                context,
                created -> {
                    refresh();
                    statusLabel.setText("Receta creada: " + created.title());
                }
        );
        dialog.show();
    }

    private void filterList(String query) {
        if (query == null || query.isBlank()) {
            listView.setItems(context.getRecipeRepository().getCache().getItems());
            return;
        }
        String lower = query.toLowerCase();
        var filtered = context.getRecipeRepository().getCache().getItems()
                .filtered(r -> r.title().toLowerCase().contains(lower)
                        || (r.description() != null && r.description().toLowerCase().contains(lower)));
        listView.setItems(filtered);
    }

    private static class RecipeCell extends ListCell<RecipeDtos.RecipeDto> {
        @Override
        protected void updateItem(RecipeDtos.RecipeDto recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox box = new VBox(3);
                box.getStyleClass().add("recipe-cell");
                Label title = new Label(recipe.title());
                title.getStyleClass().add("recipe-cell-title");
                Label meta = new Label(buildMeta(recipe));
                meta.getStyleClass().add("recipe-cell-meta");
                box.getChildren().addAll(title, meta);
                setGraphic(box);
                setText(null);
            }
        }

        private String buildMeta(RecipeDtos.RecipeDto r) {
            StringBuilder sb = new StringBuilder();
            if (r.servings() != null) sb.append(r.servings()).append(" pers.  ");
            if (r.prepMinutes() != null) sb.append(r.prepMinutes()).append(" min");
            if (r.difficulty() != null) sb.append("  ·  ").append(r.difficulty());
            return sb.toString().trim();
        }
    }
}
