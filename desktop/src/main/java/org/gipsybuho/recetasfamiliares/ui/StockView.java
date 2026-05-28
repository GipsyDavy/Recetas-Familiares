package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

public class StockView extends VBox {

    private final AppContext context;
    private final TableView<StockDtos.StockItemDto> table = new TableView<>();
    private final Label statusLabel = new Label();

    public StockView(AppContext context) {
        this.context = context;
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
        table.setItems(context.getStockRepository().getCache().getItems());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("stock-table");
        table.setPlaceholder(buildEmptyState(
                "🥫",
                "Stock vacío",
                "Registra ingredientes de casa para controlar caducidades y bajo stock"
        ));
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");

        getChildren().addAll(header, buildToolbar(), table, statusLabel);
    }

    private HBox buildToolbar() {
        Button newBtn = new Button("+ Nuevo");
        newBtn.getStyleClass().add("action-button-primary");
        newBtn.setOnAction(e -> openCreateDialog());

        Button editBtn = new Button("Editar");
        editBtn.getStyleClass().add("action-button-secondary");
        editBtn.setDisable(true);
        editBtn.setOnAction(e -> openEditDialog());

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("action-button-secondary");
        deleteBtn.setDisable(true);
        deleteBtn.setOnAction(e -> confirmDelete());

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSelection = sel != null;
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        HBox toolbar = new HBox(8, newBtn, editBtn, deleteBtn);
        toolbar.setPadding(new Insets(0, 0, 4, 0));
        return toolbar;
    }

    private void openCreateDialog() {
        StockFormDialog.forCreate(getScene().getWindow(), context, saved -> {
            context.getStockRepository().getCache().getItems().add(saved);
            statusLabel.setText("Item creado.");
        }).show();
    }

    private void openEditDialog() {
        StockDtos.StockItemDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        StockFormDialog.forEdit(getScene().getWindow(), context, selected, saved -> {
            var items = context.getStockRepository().getCache().getItems();
            int idx = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id().equals(saved.id())) { idx = i; break; }
            }
            if (idx >= 0) items.set(idx, saved);
            else items.add(saved);
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

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                statusLabel.setText("Eliminando...");
                Thread.ofVirtual().start(() -> {
                    try {
                        context.getStockRepository().delete(selected.id());
                        Platform.runLater(() -> {
                            context.getStockRepository().getCache().getItems().remove(selected);
                            statusLabel.setText("Item eliminado.");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> statusLabel.setText("Error al eliminar: " + ex.getMessage()));
                    }
                });
            }
        });
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

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var items = context.getStockRepository().load();
                Platform.runLater(() -> {
                    context.getStockRepository().getCache().replaceAll(
                            items.stream().filter(i -> !i.deleted()).toList());
                    statusLabel.setText(items.size() + " artículos");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar stock."));
            }
        });
    }
}
