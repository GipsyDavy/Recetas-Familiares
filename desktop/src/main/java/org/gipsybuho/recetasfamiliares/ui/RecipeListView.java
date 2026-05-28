package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import java.util.ArrayList;

public class RecipeListView extends SplitPane {

    private static final int PAGE_SIZE = 30;

    private final AppContext context;
    private final Runnable onSync;
    private final ListView<RecipeDtos.RecipeDto> listView = new ListView<>();
    private final RecipeDetailView detailView;
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label();
    private final Button loadMoreBtn = new Button("Cargar más recetas");
    private boolean loadingRecipes;
    private int currentPage = 0;
    private boolean hasMore = false;

    public RecipeListView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        this.detailView = new RecipeDetailView(context, this::refresh);
        build();
    }

    private void build() {
        getStyleClass().add("recipe-list-view");

        // Left panel: search + list
        searchField.setPromptText("Buscar recetas...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterList(newVal));

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> onSync.run());

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
        context.getRecipeRepository().getCache().getItems()
                .addListener((ListChangeListener<RecipeDtos.RecipeDto>) change -> updateRecipeCount());

        loadMoreBtn.getStyleClass().add("action-button-secondary");
        loadMoreBtn.setMaxWidth(Double.MAX_VALUE);
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
        loadMoreBtn.setOnAction(e -> loadNextPage());

        statusLabel.getStyleClass().add("status-label");
        updateRecipeCount();

        VBox leftPanel = new VBox(10, searchField, statusLabel, refreshBtn, newRecipeBtn, listView, loadMoreBtn);
        leftPanel.setPadding(new Insets(16));
        VBox.setVgrow(listView, Priority.ALWAYS);
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(340);

        detailView.showEmpty();

        getItems().addAll(leftPanel, detailView);
        setDividerPositions(0.35);
    }

    public void filterBy(String query) {
        searchField.setText(query != null ? query : "");
    }

    public void refresh() {
        loadingRecipes = true;
        currentPage = 0;
        hasMore = false;
        updateLoadMoreBtn();
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(0, PAGE_SIZE);
                Platform.runLater(() -> {
                    context.getRecipeRepository().getCache().replaceAll(
                            page.items().stream().filter(r -> !r.deleted()).toList());
                    hasMore = page.totalPages() > 1;
                    loadingRecipes = false;
                    updateRecipeCount();
                    updateLoadMoreBtn();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingRecipes = false;
                    statusLabel.setText("Error al cargar recetas.");
                });
            }
        });
    }

    private void loadNextPage() {
        loadMoreBtn.setDisable(true);
        int nextPage = currentPage + 1;
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(nextPage, PAGE_SIZE);
                Platform.runLater(() -> {
                    var appended = new ArrayList<>(context.getRecipeRepository().getCache().getItems());
                    page.items().stream().filter(r -> !r.deleted()).forEach(appended::add);
                    context.getRecipeRepository().getCache().replaceAll(appended);
                    currentPage = nextPage;
                    hasMore = nextPage < page.totalPages() - 1;
                    loadMoreBtn.setDisable(false);
                    updateRecipeCount();
                    updateLoadMoreBtn();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadMoreBtn.setDisable(false);
                    statusLabel.setText("Error al cargar más recetas.");
                });
            }
        });
    }

    private void updateLoadMoreBtn() {
        boolean show = hasMore && (searchField.getText() == null || searchField.getText().isBlank());
        loadMoreBtn.setVisible(show);
        loadMoreBtn.setManaged(show);
        if (show) loadMoreBtn.setText("Cargar más  (página " + (currentPage + 2) + " de " + "...)");
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
            updateRecipeCount();
            updateLoadMoreBtn();
            return;
        }
        String lower = query.toLowerCase();
        var filtered = context.getRecipeRepository().getCache().getItems()
                .filtered(r -> r.title() != null && r.title().toLowerCase().contains(lower));
        listView.setItems(filtered);
        updateRecipeCount();
        // Hide "load more" while filtering
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
    }

    private void updateRecipeCount() {
        if (loadingRecipes) return;

        int total = context.getRecipeRepository().getCache().getItems().size();
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            statusLabel.setText(total + " recetas");
            return;
        }

        statusLabel.setText("Mostrando " + listView.getItems().size() + " de " + total);
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
