package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.Map;

/**
 * Bandeja de conversaciones privadas: lista a la izquierda, panel de mensajes
 * de la conversacion seleccionada a la derecha. Mismo patron que RecipeListView
 * + RecipeDetailView (ver addendum 2026-07-22 de la spec de chat privado).
 */
public class ConversationsView extends ScrollPane {

    private final AppContext context;
    private final Runnable onSync;
    private final SplitPane splitPane = new SplitPane();
    private final ListView<PrivateChatDtos.PrivateConversation> listView = new ListView<>();
    private final PrivateChatView detailView;
    private final Label statusLabel = new Label();
    private final Button refreshBtn = new Button("Actualizar");

    private Map<String, Integer> unreadByConversation = Map.of();

    public ConversationsView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        this.detailView = new PrivateChatView(context, this::setStatus);
        build();
    }

    private void build() {
        DesktopScroll.configurePage(this, splitPane);
        splitPane.getStyleClass().add("recipe-list-view");
        setContent(splitPane);

        Label header = new Label("Conversaciones");
        header.getStyleClass().add("view-header");

        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> refresh());

        listView.getStyleClass().add("recipe-list");
        listView.setPlaceholder(new Label("Sin conversaciones todavia."));
        listView.setCellFactory(lv -> new ConversationCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, conversation) -> {
            if (conversation != null) {
                openConversation(conversation);
            }
        });

        statusLabel.getStyleClass().add("status-label");

        VBox leftPanel = new VBox(10, header, refreshBtn, listView, statusLabel);
        leftPanel.setPadding(new Insets(16));
        VBox.setVgrow(listView, Priority.ALWAYS);
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(340);

        splitPane.getItems().addAll(leftPanel, detailView);
        splitPane.setDividerPositions(0.35);
    }

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var conversations = context.getPrivateChatRepository().listConversations();
                Platform.runLater(() -> {
                    var selected = listView.getSelectionModel().getSelectedItem();
                    listView.getItems().setAll(conversations);
                    statusLabel.setText(conversations.length + " conversacion(es)");
                    if (selected != null) {
                        for (var conversation : conversations) {
                            if (conversation.conversationId().equals(selected.conversationId())) {
                                listView.getSelectionModel().select(conversation);
                                break;
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("No se pudieron cargar las conversaciones."));
            }
        });
    }

    /** Abre (creando si hace falta) la conversacion con otro miembro. Llamado desde Miembros. */
    public void openWith(String otherUserId) {
        statusLabel.setText("Abriendo conversacion...");
        Thread.ofVirtual().start(() -> {
            try {
                var conversation = context.getPrivateChatRepository().createOrGetConversation(otherUserId);
                Platform.runLater(() -> {
                    refresh();
                    openConversation(conversation);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("No se pudo abrir la conversacion: " + ex.getMessage()));
            }
        });
    }

    private void openConversation(PrivateChatDtos.PrivateConversation conversation) {
        detailView.open(conversation);
    }

    /** Actualiza los contadores de no-leidos mostrados en cada fila (badge de la sidebar por conversacion). */
    public void updateUnread(Map<String, Integer> unreadByConversation) {
        this.unreadByConversation = unreadByConversation;
        listView.refresh();
    }

    /** Se llama al salir de esta pantalla: libera la suscripcion a la conversacion abierta. */
    public void onHidden() {
        detailView.close();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private final class ConversationCell extends ListCell<PrivateChatDtos.PrivateConversation> {
        @Override
        protected void updateItem(PrivateChatDtos.PrivateConversation conversation, boolean empty) {
            super.updateItem(conversation, empty);
            if (empty || conversation == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(conversation.otherUserDisplayName() != null
                    ? conversation.otherUserDisplayName() : "Conversacion");
            title.getStyleClass().add("recipe-cell-title");
            Label preview = new Label(conversation.lastMessagePreview() != null
                    ? conversation.lastMessagePreview() : "");
            preview.getStyleClass().add("recipe-cell-meta");
            VBox textBox = new VBox(3, title, preview);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            HBox row = new HBox(8, textBox);
            Integer unread = unreadByConversation.get(conversation.conversationId());
            if (unread != null && unread > 0) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label badge = new Label(unread > 9 ? "9+" : String.valueOf(unread));
                badge.getStyleClass().add("profile-role-badge");
                row.getChildren().addAll(spacer, badge);
            }
            row.getStyleClass().add("recipe-cell");
            setGraphic(row);
            setText(null);
        }
    }
}
