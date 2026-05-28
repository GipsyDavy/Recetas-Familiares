package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
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
        nameCol.setPrefWidth(240);

        TableColumn<StockDtos.StockItemDto, String> qtyCol = new TableColumn<>("Cantidad");
        qtyCol.setCellValueFactory(c -> {
            var item = c.getValue();
            String val = item.quantity() != null ? item.quantity().toString() : "—";
            if (item.unit() != null) val += " " + item.unit();
            return new SimpleStringProperty(val);
        });
        qtyCol.setPrefWidth(140);

        TableColumn<StockDtos.StockItemDto, String> expiresCol = new TableColumn<>("Caduca");
        expiresCol.setCellValueFactory(c -> {
            String exp = c.getValue().expiresAt();
            return new SimpleStringProperty(exp != null ? exp.substring(0, Math.min(10, exp.length())) : "—");
        });
        expiresCol.setPrefWidth(120);

        table.getColumns().addAll(nameCol, qtyCol, expiresCol);
        table.setItems(context.getStockRepository().getCache().getItems());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("stock-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");

        getChildren().addAll(header, table, statusLabel);
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
