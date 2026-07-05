package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.function.Consumer;

public class StockView extends VBox {

    private static final int PAGE_SIZE = 50;

    private final AppContext context;
    private final Runnable onSync;
    private final Consumer<String> onStatusUpdate;
    private final TableView<StockDtos.StockItemDto> table = new TableView<>();
    private final Label statusLabel = new Label();
    private final TextField filterField = new TextField();
    private final javafx.collections.ObservableList<StockDtos.StockItemDto> displayItems =
            javafx.collections.FXCollections.observableArrayList();
    private FilteredList<StockDtos.StockItemDto> filteredItems;
    private int currentLimit = PAGE_SIZE;
    private Button loadMoreBtn;

    public StockView(AppContext context, Runnable onSync) {
        this(context, onSync, msg -> {});
    }

    public StockView(AppContext context, Runnable onSync, Consumer<String> onStatusUpdate) {
        this.context = context;
        this.onSync = onSync;
        this.onStatusUpdate = onStatusUpdate;
        build();
    }

    @SuppressWarnings("unchecked")
    private void build() {
        getStyleClass().add("stock-view");
        setSpacing(12);
        setPadding(new Insets(24));

        Label header = new Label("Stock familiar");
        header.getStyleClass().add("view-header");

        TableColumn<StockDtos.StockItemDto, String> nameCol = new TableColumn<>("Ingrediente");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(220);

        TableColumn<StockDtos.StockItemDto, String> qtyCol = new TableColumn<>("Cantidad");
        qtyCol.setCellValueFactory(c -> {
            var item = c.getValue();
            String val = item.quantity() != null ? item.quantity().toString() : "—";
            if (item.unit() != null) val += " " + item.unit();
            return new SimpleStringProperty(val);
        });
        qtyCol.setPrefWidth(120);

        TableColumn<StockDtos.StockItemDto, String> expiresCol = new TableColumn<>("Caduca");
        expiresCol.setCellValueFactory(c -> {
            String exp = c.getValue().expiresAt();
            return new SimpleStringProperty(exp != null ? exp.substring(0, Math.min(10, exp.length())) : "—");
        });
        expiresCol.setPrefWidth(110);

        TableColumn<StockDtos.StockItemDto, String> lowStockCol = new TableColumn<>("Mín. stock");
        lowStockCol.setCellValueFactory(c -> {
            var item = c.getValue();
            if (item.lowStockThreshold() == null) return new SimpleStringProperty("—");
            String val = item.lowStockThreshold().toString();
            if (item.unit() != null) val += " " + item.unit();
            // Visual warning if current quantity is below threshold
            boolean belowMin = item.quantity() != null && item.quantity() < item.lowStockThreshold();
            return new SimpleStringProperty(belowMin ? "⚠ " + val : val);
        });
        lowStockCol.setPrefWidth(110);

        table.getColumns().addAll(nameCol, qtyCol, expiresCol, lowStockCol);
        filteredItems = new FilteredList<>(context.getStockRepository().getCache().getItems());
        filteredItems.addListener((javafx.collections.ListChangeListener<StockDtos.StockItemDto>) c -> {
            currentLimit = PAGE_SIZE;
            refreshDisplay();
        });
        table.setItems(displayItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("stock-table");
        table.setPlaceholder(buildEmptyState(
                "🥫",
                "Stock vacío",
                "Registra ingredientes de casa para controlar caducidades y bajo stock"
        ));
        ContextMenu cm = new ContextMenu();
        MenuItem cmEdit = new MenuItem("Editar");
        MenuItem cmDelete = new MenuItem("Eliminar");
        cmEdit.setOnAction(e -> openEditDialog());
        cmDelete.setOnAction(e -> deleteSelected());
        cm.getItems().addAll(cmEdit, new SeparatorMenuItem(), cmDelete);
        DialogStyler.apply(cm);
        table.setContextMenu(cm);
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                deleteSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                openEditDialog();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.N) {
                openCreateDialog();
                e.consume();
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");

        filterField.setPromptText("Buscar en stock...");
        filterField.getStyleClass().add("search-field");
        filterField.textProperty().addListener((obs, old, val) ->
            filteredItems.setPredicate(val == null || val.isBlank() ? null :
                s -> s.name() != null && s.name().toLowerCase().contains(val.toLowerCase())));

        getChildren().addAll(header, filterField, buildToolbar(), table, statusLabel);
    }

    private HBox buildToolbar() {
        Button newBtn = new Button("+ Nuevo");
        newBtn.getStyleClass().add("action-button-primary");
        newBtn.setOnAction(e -> openCreateDialog());
        Tooltip.install(newBtn, new Tooltip("Nuevo ítem de stock (Ctrl+N)"));

        Button editBtn = new Button("Editar");
        editBtn.getStyleClass().add("action-button-secondary");
        editBtn.setDisable(true);
        editBtn.setOnAction(e -> openEditDialog());
        Tooltip.install(editBtn, new Tooltip("Editar ítem seleccionado (Enter)"));

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("action-button-secondary");
        deleteBtn.setDisable(true);
        deleteBtn.setOnAction(e -> confirmDelete());
        Tooltip.install(deleteBtn, new Tooltip("Eliminar ítem seleccionado (Supr)"));

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSelection = sel != null;
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> onSync.run());

        loadMoreBtn = new Button("Cargar más");
        loadMoreBtn.getStyleClass().add("action-button-secondary");
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setOnAction(e -> {
            currentLimit += PAGE_SIZE;
            refreshDisplay();
        });

        HBox toolbar = new HBox(8, newBtn, editBtn, deleteBtn, refreshBtn, loadMoreBtn);
        toolbar.setPadding(new Insets(0, 0, 4, 0));
        return toolbar;
    }

    private void openCreateDialog() {
        StockFormDialog.forCreate(getScene().getWindow(), context, saved -> {
            context.getStockRepository().getCache().add(saved);
            refreshDisplay();
            statusLabel.setText("Item creado.");
        }).show();
    }

    private void openEditDialog() {
        StockDtos.StockItemDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        StockFormDialog.forEdit(getScene().getWindow(), context, selected, saved -> {
            context.getStockRepository().getCache()
                    .replaceOrAdd(saved, item -> item.id().equals(saved.id()));
            refreshDisplay();
            statusLabel.setText("Item actualizado.");
        }).show();
    }

    private void confirmDelete() {
        StockDtos.StockItemDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar item");
        confirm.setHeaderText("¿Eliminar \"" + selected.name() + "\"?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        confirm.initOwner(getScene().getWindow());
        DialogStyler.apply(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                animateDelete(selected, () -> performDelete(selected));
            }
        });
    }

    private void deleteSelected() {
        confirmDelete();
    }

    private void animateDelete(StockDtos.StockItemDto selected, Runnable afterAnimation) {
        TableRow<?> row = findRow(selected);
        Node target = row != null ? row : table;

        FadeTransition fade = new FadeTransition(Duration.millis(150), target);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> {
            if (row == null) {
                table.setOpacity(1.0);
                afterAnimation.run();
                return;
            }
            double height = row.getHeight() > 0 ? row.getHeight() : row.getPrefHeight();
            row.setMinHeight(0);
            Timeline collapse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(row.prefHeightProperty(), height)),
                    new KeyFrame(Duration.millis(150), new KeyValue(row.prefHeightProperty(), 0))
            );
            collapse.setOnFinished(ev -> afterAnimation.run());
            collapse.play();
        });
        fade.play();
    }

    private TableRow<?> findRow(StockDtos.StockItemDto selected) {
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row && row.getItem() == selected) {
                return row;
            }
        }
        return null;
    }

    private void performDelete(StockDtos.StockItemDto selected) {
        statusLabel.setText("Eliminando...");
        Thread.ofVirtual().start(() -> {
            try {
                context.getStockRepository().delete(selected.id());
                Platform.runLater(() -> {
                    context.getStockRepository().getCache().remove(selected);
                    refreshDisplay();
                    statusLabel.setText("Item eliminado.");
                    onStatusUpdate.accept("Ítem eliminado");
                    SoundPlayer.playDelete();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al eliminar: " + ex.getMessage()));
            }
        });
    }

    private void refreshDisplay() {
        int total = filteredItems.size();
        int showing = Math.min(currentLimit, total);
        displayItems.setAll(filteredItems.subList(0, showing));
        boolean hasMore = showing < total && (filterField.getText() == null || filterField.getText().isBlank());
        if (loadMoreBtn != null) loadMoreBtn.setVisible(hasMore);
        if (loadMoreBtn != null) loadMoreBtn.setText("Cargar más (" + showing + " de " + total + ")");
    }

    private Node buildEmptyState(String emoji, String title, String subtitle) {
        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8B6F5E; -fx-text-alignment: center;");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMaxWidth(320);
        subtitleLabel.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, emojiLabel, titleLabel, subtitleLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    public void filterBy(String query) {
        filterField.setText(query != null ? query : "");
    }

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var items = context.getStockRepository().load();
                Platform.runLater(() -> {
                    context.getStockRepository().getCache().replaceAll(
                            items.stream().filter(i -> !i.deleted()).toList());
                    refreshDisplay();
                    statusLabel.setText(items.size() + " artículos");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar stock."));
            }
        });
    }
}
