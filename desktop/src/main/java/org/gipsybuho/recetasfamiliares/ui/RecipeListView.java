package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import java.util.ArrayList;

public class RecipeListView extends ScrollPane {

    private static final int PAGE_SIZE = 30;

    private final AppContext context;
    private final Runnable onSync;
    private final SplitPane splitPane = new SplitPane();
    private final ListView<RecipeDtos.RecipeDto> listView = new ListView<>();
    private final RecipeDetailView detailView;
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label();
    private final Button loadMoreBtn = new Button("Cargar más recetas");
    private VBox skeletonPane;
    private Timeline skeletonShimmer;
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
        DesktopScroll.configurePage(this, splitPane);
        splitPane.getStyleClass().add("recipe-list-view");
        setContent(splitPane);

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

        StackPane stackPane = new StackPane();
        skeletonPane = buildSkeletonPane();
        skeletonPane.setVisible(false);
        stackPane.getChildren().addAll(listView, skeletonPane);

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

        VBox leftPanel = new VBox(10, searchField, statusLabel, refreshBtn, newRecipeBtn, stackPane, loadMoreBtn);
        leftPanel.setPadding(new Insets(16));
        VBox.setVgrow(stackPane, Priority.ALWAYS);
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(340);

        detailView.showEmpty();

        splitPane.getItems().addAll(leftPanel, detailView);
        splitPane.setDividerPositions(0.35);
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
        skeletonPane.setVisible(true);
        startSkeletonShimmer();
        String familyAtStart = context.getSession().getFamilyId();
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(familyAtStart, 0, PAGE_SIZE);
                Platform.runLater(() -> {
                    if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                        return;
                    }
                    context.getRecipeRepository().getCache().replaceAll(
                            page.items().stream().filter(r -> !r.deleted()).toList());
                    hasMore = page.totalPages() > 1;
                    loadingRecipes = false;
                    stopSkeletonShimmer();
                    updateRecipeCount();
                    updateLoadMoreBtn();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingRecipes = false;
                    stopSkeletonShimmer();
                    statusLabel.setText("Error al cargar recetas.");
                });
            }
        });
    }

    private VBox buildSkeletonPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(8));
        for (int i = 0; i < 5; i++) {
            Rectangle title = new Rectangle(200, 14, Color.web("#E0D0C0"));
            Rectangle meta = new Rectangle(120, 10, Color.web("#E0D0C0"));
            HBox row = new HBox(8, title, meta);
            pane.getChildren().add(row);
        }
        return pane;
    }

    private void startSkeletonShimmer() {
        if (skeletonShimmer != null) skeletonShimmer.stop();
        skeletonShimmer = null;
        if (MotionPreferences.isReducedMotion()) {
            skeletonPane.setOpacity(0.55);
            return;
        }
        skeletonPane.setOpacity(0.3);
        skeletonShimmer = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(skeletonPane.opacityProperty(), 0.3)),
                new KeyFrame(Duration.millis(900), new KeyValue(skeletonPane.opacityProperty(), 0.7))
        );
        skeletonShimmer.setAutoReverse(true);
        skeletonShimmer.setCycleCount(Timeline.INDEFINITE);
        skeletonShimmer.play();
    }

    private void stopSkeletonShimmer() {
        if (skeletonShimmer != null) {
            skeletonShimmer.stop();
            skeletonShimmer = null;
        }
        skeletonPane.setVisible(false);
        skeletonPane.setOpacity(1.0);
    }

    private void loadNextPage() {
        loadMoreBtn.setDisable(true);
        int nextPage = currentPage + 1;
        String familyAtStart = context.getSession().getFamilyId();
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(familyAtStart, nextPage, PAGE_SIZE);
                Platform.runLater(() -> {
                    if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                        return;
                    }
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

    /** Deja de ser static: necesita el AppContext para descargar las portadas. */
    private class RecipeCell extends ListCell<RecipeDtos.RecipeDto> {

        private final RecipeThumbnail thumb = new RecipeThumbnail(context, RecipeThumbnail.LIST_SIZE);
        private final Label title = new Label();
        private final Label meta = new Label();
        private final HBox root;

        RecipeCell() {
            title.getStyleClass().add("recipe-cell-title");
            meta.getStyleClass().add("recipe-cell-meta");
            VBox texts = new VBox(3, title, meta);
            root = new HBox(10, thumb, texts);
            root.getStyleClass().add("recipe-cell");
            HBox.setHgrow(texts, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(RecipeDtos.RecipeDto recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                thumb.clear();
                setText(null);
                setGraphic(null);
                return;
            }
            title.setText(recipe.title());
            meta.setText(buildMeta(recipe));
            setGraphic(root);
            setText(null);
            thumb.show(recipe.coverThumbnailUrl());
        }

        private String buildMeta(RecipeDtos.RecipeDto r) {
            StringBuilder sb = new StringBuilder();
            if (r.servings() != null) sb.append(r.servings()).append(" pers.  ");
            if (r.prepMinutes() != null) sb.append(r.prepMinutes()).append(" min");
            if (r.difficulty() != null) sb.append("  ·  ").append(r.difficulty());
            String creator = creatorLabel(r);
            if (creator != null) sb.append("  ·  Por ").append(creator);
            return sb.toString().trim();
        }

        private String creatorLabel(RecipeDtos.RecipeDto r) {
            return r.createdByDisplayName() != null && !r.createdByDisplayName().isBlank()
                    ? r.createdByDisplayName()
                    : null;
        }
    }
}
