package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ShoppingListView extends VBox {

    private final AppContext context;
    private final Runnable onSync;
    private final Label statusLabel = new Label();
    private final ListView<SyncDtos.ShoppingDtos.ShoppingListDto> listView = new ListView<>();

    public ShoppingListView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        build();
    }

    // ── Build ──────────────────────────────────────────────────────────────────

    private void build() {
        getStyleClass().add("stock-view");
        setSpacing(0);
        setPadding(new Insets(24));

        Label header = new Label("Lista de la compra");
        header.getStyleClass().add("view-header");

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> onSync.run());

        HBox toolbar = new HBox(12, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 0, 16, 0));

        listView.getStyleClass().add("shopping-list");
        listView.setPlaceholder(buildEmptyState(
                "🛒",
                "Sin listas de la compra",
                "Las listas se generan desde el menú semanal o se crean en el servidor"
        ));
        listView.setCellFactory(lv -> new ShoppingListCell());
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                var selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) openDetail(selected);
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(8, 0, 0, 0));

        getChildren().addAll(header, toolbar, listView, statusLabel);
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                List<SyncDtos.ShoppingDtos.ShoppingListDto> lists =
                        context.getShoppingListRepository().loadAll();
                Platform.runLater(() -> {
                    listView.getItems().setAll(lists);
                    statusLabel.setText(lists.size() + " listas");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar: " + ex.getMessage()));
            }
        });
    }

    // ── Detail dialog ──────────────────────────────────────────────────────────

    private void openDetail(SyncDtos.ShoppingDtos.ShoppingListDto list) {
        statusLabel.setText("Cargando artículos...");
        Thread.ofVirtual().start(() -> {
            try {
                List<SyncDtos.ShoppingDtos.ShoppingListItemDto> items =
                        context.getShoppingListRepository().loadItems(list.id());
                Platform.runLater(() -> {
                    statusLabel.setText("");
                    showItemsDialog(list, items);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void showItemsDialog(
            SyncDtos.ShoppingDtos.ShoppingListDto list,
            List<SyncDtos.ShoppingDtos.ShoppingListItemDto> initialItems
    ) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(list.name());
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(6);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);

        // Mutable snapshot — refreshed when user checks/unchecks
        var itemsRef = new AtomicReference<>(initialItems);

        buildItemRows(content, itemsRef, list, dialog);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(400);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void buildItemRows(
            VBox content,
            AtomicReference<List<SyncDtos.ShoppingDtos.ShoppingListItemDto>> itemsRef,
            SyncDtos.ShoppingDtos.ShoppingListDto list,
            Dialog<Void> dialog
    ) {
        content.getChildren().clear();
        List<SyncDtos.ShoppingDtos.ShoppingListItemDto> items = itemsRef.get();
        long pending = items.stream().filter(i -> !i.checked()).count();
        Label summary = new Label(pending + " pendiente" + (pending != 1 ? "s" : "") + " de " + items.size());
        summary.getStyleClass().add("recipe-meta");
        content.getChildren().add(summary);

        for (SyncDtos.ShoppingDtos.ShoppingListItemDto item : items) {
            CheckBox cb = new CheckBox(buildItemLabel(item));
            cb.setSelected(item.checked());
            cb.getStyleClass().add("shopping-item-checkbox");
            if (item.checked()) cb.setStyle("-fx-text-fill: -color-outline;");
            cb.setMaxWidth(Double.MAX_VALUE);

            cb.setOnAction(e -> {
                cb.setDisable(true);
                Thread.ofVirtual().start(() -> {
                    try {
                        context.getShoppingListRepository().checkItem(item, cb.isSelected());
                        List<SyncDtos.ShoppingDtos.ShoppingListItemDto> refreshed =
                                context.getShoppingListRepository().loadItems(list.id());
                        Platform.runLater(() -> {
                            itemsRef.set(refreshed);
                            buildItemRows(content, itemsRef, list, dialog);
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            cb.setSelected(!cb.isSelected()); // rollback
                            cb.setDisable(false);
                        });
                    }
                });
            });
            content.getChildren().add(cb);
        }
    }

    private String buildItemLabel(SyncDtos.ShoppingDtos.ShoppingListItemDto item) {
        StringBuilder sb = new StringBuilder(item.name());
        if (item.quantity() != null) sb.append("  ").append(item.quantity());
        if (item.unit() != null && !item.unit().isBlank()) sb.append(" ").append(item.unit());
        if (item.note() != null && !item.note().isBlank()) sb.append(" (").append(item.note()).append(")");
        return sb.toString();
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

    // ── Cell ───────────────────────────────────────────────────────────────────

    private static class ShoppingListCell extends ListCell<SyncDtos.ShoppingDtos.ShoppingListDto> {
        @Override
        protected void updateItem(SyncDtos.ShoppingDtos.ShoppingListDto item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                Label title = new Label(item.name());
                title.getStyleClass().add("recipe-title-small");
                String meta = item.completed() ? "Completada"
                        : (item.note() != null && !item.note().isBlank() ? item.note() : "");
                Label subtitle = new Label(meta);
                subtitle.getStyleClass().add("recipe-meta");
                VBox box = new VBox(2, title, subtitle);
                box.setPadding(new Insets(6, 8, 6, 8));
                setGraphic(box);
                setText(null);
            }
        }
    }
}
