package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.data.repository.PrivateChatRepository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel de una conversacion privada 1:1, embebido en {@link ConversationsView}.
 * Mismo patron que {@link ChatView} pero sin ChatSocket propio: la conexion en
 * tiempo real es la compartida de {@code ChatRepository} (una sola por sesion).
 */
public class PrivateChatView extends VBox {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double NEAR_BOTTOM_THRESHOLD = 0.95;
    private static final ButtonType SAVE_BUTTON_TYPE =
            new ButtonType("Guardar", ButtonBar.ButtonData.OTHER);

    private final AppContext context;
    private final Consumer<String> onStatus;

    private final Label headerLabel = new Label();
    private final Label statusLabel = new Label();
    private final VBox messagesBox = new VBox(8);
    private final ScrollPane scrollPane = new ScrollPane();
    private final Button loadOlderBtn = new Button("Cargar mensajes anteriores");
    private final Label emptyState = buildEmptyState();
    private final TextArea input = new TextArea();
    private final Button attachBtn = new Button("Imagen");
    private final Button sendBtn = new Button("Enviar");
    private final Label charCounter = new Label();

    private final List<PrivateChatDtos.PrivateMessage> messages = new ArrayList<>();

    private String conversationId;
    private boolean loading = false;
    private boolean sending = false;
    private boolean hasMoreOlder = false;
    private String nextBefore = null;
    private String myUserId;

    public PrivateChatView(AppContext context, Consumer<String> onStatus) {
        this.context = context;
        this.onStatus = onStatus;
        this.myUserId = context.getSession().getUserId();
        build();
        showEmpty();
    }

    private void build() {
        getStyleClass().add("chat-view");
        setPadding(new Insets(24));
        setSpacing(0);

        headerLabel.getStyleClass().add("view-header");
        statusLabel.getStyleClass().add("chat-status");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        MenuButton menu = new MenuButton("Opciones");
        menu.getStyleClass().add("action-button-secondary");
        MenuItem exportItem = new MenuItem("Exportar chat");
        exportItem.setOnAction(e -> exportChat());
        MenuItem clearItem = new MenuItem("Borrar chat para mi");
        clearItem.setOnAction(e -> confirmClear());
        menu.getItems().addAll(exportItem, clearItem);

        HBox headerRow = new HBox(12, headerLabel, statusLabel, headerSpacer, menu);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(0, 0, 16, 0));

        loadOlderBtn.getStyleClass().add("action-button-secondary");
        loadOlderBtn.setMaxWidth(Double.MAX_VALUE);
        loadOlderBtn.setVisible(false);
        loadOlderBtn.setManaged(false);
        loadOlderBtn.setOnAction(e -> loadOlder());

        messagesBox.getStyleClass().add("chat-messages");
        messagesBox.setPadding(new Insets(4, 8, 4, 8));
        messagesBox.setFillWidth(true);

        VBox scrollContent = new VBox(8, loadOlderBtn, messagesBox);
        VBox.setVgrow(messagesBox, Priority.ALWAYS);

        scrollPane.setContent(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinHeight(0);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        input.setPromptText("Escribe un mensaje...");
        input.setWrapText(true);
        input.setPrefRowCount(1);
        input.setMaxHeight(120);
        input.getStyleClass().add("chat-input");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleInputKey);
        input.textProperty().addListener((obs, old, val) -> updateCharCounter(val));

        attachBtn.getStyleClass().add("action-button-secondary");
        attachBtn.setOnAction(e -> chooseAndSendImage());

        sendBtn.getStyleClass().add("action-button-primary");
        sendBtn.setDefaultButton(false);
        sendBtn.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, attachBtn, input, sendBtn);
        inputRow.setAlignment(Pos.BOTTOM_LEFT);
        inputRow.setPadding(new Insets(12, 0, 0, 0));

        charCounter.getStyleClass().add("chat-char-counter");
        charCounter.setVisible(false);
        charCounter.setManaged(false);
        HBox counterRow = new HBox(charCounter);
        counterRow.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(headerRow, scrollPane, inputRow, counterRow);
    }

    private Label buildEmptyState() {
        Label label = new Label("Escribe el primer mensaje para empezar la conversacion.");
        label.getStyleClass().add("chat-empty");
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.setAlignment(Pos.CENTER);
        VBox.setMargin(label, new Insets(48, 24, 48, 24));
        return label;
    }

    // ── Ciclo de vida (invocado desde ConversationsView) ───────────────────────

    public void showEmpty() {
        this.conversationId = null;
        headerLabel.setText("Selecciona una conversacion");
        statusLabel.setText("");
        messages.clear();
        renderMessages();
        setDisableComposer(true);
    }

    /** Abre esta conversacion: suscribe el socket compartido y carga el historial. */
    public void open(PrivateChatDtos.PrivateConversation conversation) {
        // Los callbacks del socket llegan en hilos de OkHttp (ver ChatSocket): hay que
        // marshalizar al hilo de JavaFX antes de tocar el scene graph, igual que ChatView.
        context.getChatRepository().setConversationMessageListener(
                message -> Platform.runLater(() -> onRealtimeMessage(message)));
        var socket = context.getChatRepository().activeSocket();
        if (socket != null) {
            socket.subscribeConversation(conversation.conversationId());
        }
        context.getChatRepository().setActiveConversation(conversation.conversationId());

        this.conversationId = conversation.conversationId();
        headerLabel.setText(conversation.otherUserDisplayName() != null
                ? conversation.otherUserDisplayName() : "Conversacion");
        statusLabel.setText("");
        messages.clear();
        hasMoreOlder = false;
        nextBefore = null;
        setDisableComposer(false);
        loadInitialHistory();
    }

    /** Se llama al cerrar Conversaciones o cambiar de familia: libera el socket compartido. */
    public void close() {
        var socket = context.getChatRepository().activeSocket();
        if (socket != null) {
            socket.unsubscribeConversation();
        }
        context.getChatRepository().setActiveConversation(null);
        context.getChatRepository().setConversationMessageListener(null);
    }

    private void setDisableComposer(boolean disable) {
        input.setDisable(disable);
        attachBtn.setDisable(disable);
        sendBtn.setDisable(disable);
    }

    // ── Carga de historial ─────────────────────────────────────────────────────

    private void loadInitialHistory() {
        if (loading || conversationId == null) {
            return;
        }
        String targetConversation = conversationId;
        loading = true;
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageHistory page =
                        privateChatRepository().loadHistory(targetConversation, null, PrivateChatRepository.PAGE_SIZE);
                Platform.runLater(() -> {
                    loading = false;
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    messages.clear();
                    List<PrivateChatDtos.PrivateMessage> items = page.items() != null ? page.items() : List.of();
                    for (int i = items.size() - 1; i >= 0; i--) {
                        upsertAscending(items.get(i));
                    }
                    hasMoreOlder = page.hasMore();
                    nextBefore = page.nextBefore();
                    updateLoadOlderBtn();
                    renderMessages();
                    statusLabel.setText("");
                    scrollToBottom();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loading = false;
                    statusLabel.setText("Error al cargar el chat");
                    onStatus.accept("No se pudo cargar la conversacion: " + ex.getMessage());
                });
            }
        });
    }

    private void loadOlder() {
        if (loading || !hasMoreOlder || nextBefore == null || conversationId == null) {
            return;
        }
        String targetConversation = conversationId;
        loading = true;
        loadOlderBtn.setDisable(true);
        final String before = nextBefore;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageHistory page =
                        privateChatRepository().loadHistory(targetConversation, before, PrivateChatRepository.PAGE_SIZE);
                Platform.runLater(() -> {
                    loading = false;
                    loadOlderBtn.setDisable(false);
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    List<PrivateChatDtos.PrivateMessage> items = page.items() != null ? page.items() : List.of();
                    boolean changed = false;
                    for (int i = items.size() - 1; i >= 0; i--) {
                        changed |= upsertAscending(items.get(i));
                    }
                    hasMoreOlder = page.hasMore();
                    nextBefore = page.nextBefore();
                    updateLoadOlderBtn();
                    if (changed) {
                        renderMessages();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loading = false;
                    loadOlderBtn.setDisable(false);
                    onStatus.accept("No se pudieron cargar mensajes anteriores.");
                });
            }
        });
    }

    // ── Tiempo real ─────────────────────────────────────────────────────────────

    private void onRealtimeMessage(PrivateChatDtos.PrivateMessage message) {
        if (message == null || message.id() == null || !message.conversationId().equals(conversationId)) {
            return;
        }
        boolean nearBottom = scrollPane.getVvalue() >= NEAR_BOTTOM_THRESHOLD;
        if (upsertAscending(message)) {
            renderMessages();
            boolean mine = isMine(message);
            if (mine || nearBottom) {
                scrollToBottom();
            }
        }
    }

    private boolean upsertAscending(PrivateChatDtos.PrivateMessage message) {
        if (message == null || message.id() == null) {
            return false;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (message.id().equals(messages.get(i).id())) {
                messages.set(i, message);
                sortMessages();
                return true;
            }
        }
        messages.add(message);
        sortMessages();
        return true;
    }

    private void sortMessages() {
        messages.sort(Comparator
                .comparing(PrivateChatDtos.PrivateMessage::createdAt, Comparator.nullsLast(String::compareTo))
                .thenComparing(PrivateChatDtos.PrivateMessage::id, Comparator.nullsLast(String::compareTo)));
    }

    // ── Envio ────────────────────────────────────────────────────────────────────

    private void handleInputKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            sendMessage();
        }
    }

    private void sendMessage() {
        if (sending || conversationId == null) {
            return;
        }
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > PrivateChatRepository.MAX_BODY_LENGTH) {
            onStatus.accept("El mensaje supera el limite de " + PrivateChatRepository.MAX_BODY_LENGTH + " caracteres.");
            return;
        }
        String targetConversation = conversationId;
        setSending(true);
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage saved = privateChatRepository().send(targetConversation, text);
                Platform.runLater(() -> {
                    setSending(false);
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    input.clear();
                    if (upsertAscending(saved)) {
                        renderMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    onStatus.accept("No se pudo enviar el mensaje: " + ex.getMessage());
                });
            }
        });
    }

    private void chooseAndSendImage() {
        if (sending || conversationId == null) {
            return;
        }
        String caption = input.getText() == null ? "" : input.getText().trim();
        if (caption.length() > PrivateChatRepository.MAX_BODY_LENGTH) {
            onStatus.accept("El mensaje supera el limite de " + PrivateChatRepository.MAX_BODY_LENGTH + " caracteres.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enviar imagen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imagenes (JPG, PNG, WebP)", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        String targetConversation = conversationId;
        setSending(true);
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage saved = privateChatRepository().sendImage(targetConversation, caption, file);
                Platform.runLater(() -> {
                    setSending(false);
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    input.clear();
                    if (upsertAscending(saved)) {
                        renderMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    onStatus.accept("No pudimos enviar tu imagen. Comprueba que sea una foto valida e intentalo de nuevo.");
                });
            }
        });
    }

    // ── Edicion / borrado individual ─────────────────────────────────────────────

    private void showEditDialog(PrivateChatDtos.PrivateMessage message) {
        if (!isOwnMutableMessage(message) || message.body() == null || message.body().isBlank()) {
            return;
        }
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Editar mensaje");
        dialog.setHeaderText("Actualiza el texto del mensaje.");

        TextArea editor = new TextArea(message.body());
        editor.setWrapText(true);
        editor.setPrefRowCount(4);
        editor.setMaxWidth(420);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(editor);
        DialogStyler.apply(dialog);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        Runnable updateSaveState = () -> {
            String text = editor.getText() == null ? "" : editor.getText().trim();
            saveButton.setDisable(text.isEmpty()
                    || text.equals(message.body())
                    || text.length() > PrivateChatRepository.MAX_BODY_LENGTH);
        };
        editor.textProperty().addListener((obs, old, value) -> updateSaveState.run());
        updateSaveState.run();

        dialog.setResultConverter(type -> type == saveType ? editor.getText() : null);
        dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> editMessage(message, text));
    }

    private void editMessage(PrivateChatDtos.PrivateMessage message, String body) {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Guardando mensaje...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage updated =
                        privateChatRepository().edit(targetConversation, message.id(), body);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    upsertAscending(updated);
                    renderMessages();
                    onStatus.accept("Mensaje editado.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo editar el mensaje."));
            }
        });
    }

    private void confirmDeleteMessage(PrivateChatDtos.PrivateMessage message) {
        if (!isOwnMutableMessage(message)) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar mensaje");
        confirm.setHeaderText("¿Eliminar este mensaje?");
        confirm.setContentText("Se mostrara como mensaje eliminado.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(confirm);
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                deleteMessage(message);
            }
        });
    }

    private void deleteMessage(PrivateChatDtos.PrivateMessage message) {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Eliminando mensaje...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage updated = privateChatRepository().delete(targetConversation, message.id());
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    upsertAscending(updated);
                    renderMessages();
                    onStatus.accept("Mensaje eliminado.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo eliminar el mensaje."));
            }
        });
    }

    // ── Exportar / borrar ─────────────────────────────────────────────────────────

    private void exportChat() {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Exportando conversacion...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageExport export = privateChatRepository().export(targetConversation);
                String text = buildExportText(export);
                Platform.runLater(() -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                    onStatus.accept("Conversacion copiada al portapapeles ("
                            + export.totalMessages() + " mensajes).");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo exportar: " + ex.getMessage()));
            }
        });
    }

    private String buildExportText(PrivateChatDtos.PrivateMessageExport export) {
        StringBuilder builder = new StringBuilder("Conversacion privada - export\n\n");
        if (export.messages() != null) {
            for (PrivateChatDtos.PrivateMessage message : export.messages()) {
                List<PrivateChatDtos.PrivateAttachment> attachments = message.attachmentsOrEmpty();
                String body = message.body() != null ? message.body() : attachments.isEmpty() ? "(mensaje eliminado)" : "";
                builder.append('[').append(formatTime(message.createdAt())).append("] ")
                        .append(message.authorDisplayName()).append(": ")
                        .append(body);
                if (!attachments.isEmpty()) {
                    if (!body.isBlank()) {
                        builder.append(' ');
                    }
                    builder.append('[').append(attachments.size()).append(" imagen");
                    if (attachments.size() != 1) {
                        builder.append("es");
                    }
                    builder.append(']');
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void confirmClear() {
        if (conversationId == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Borrar chat para ti");
        confirm.setHeaderText("¿Borrar tu historial de esta conversacion?");
        confirm.setContentText("Se ocultara tu historial. El otro participante conserva el suyo. "
                + "Esta accion no se puede deshacer.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(confirm);
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                doClear();
            }
        });
    }

    private void doClear() {
        String targetConversation = conversationId;
        onStatus.accept("Borrando conversacion...");
        Thread.ofVirtual().start(() -> {
            try {
                privateChatRepository().clear(targetConversation);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    messages.clear();
                    hasMoreOlder = false;
                    nextBefore = null;
                    updateLoadOlderBtn();
                    renderMessages();
                    onStatus.accept("Conversacion borrada para ti.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo borrar: " + ex.getMessage()));
            }
        });
    }

    // ── Render ─────────────────────────────────────────────────────────────────────

    private void renderMessages() {
        messagesBox.getChildren().clear();
        if (messages.isEmpty()) {
            messagesBox.getChildren().add(emptyState);
            return;
        }
        for (PrivateChatDtos.PrivateMessage message : messages) {
            messagesBox.getChildren().add(buildBubbleRow(message));
        }
    }

    private HBox buildBubbleRow(PrivateChatDtos.PrivateMessage message) {
        boolean mine = isMine(message);

        VBox bubble = new VBox(6);
        bubble.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble-other");
        bubble.setMaxWidth(440);
        if (isOwnMutableMessage(message)) {
            ContextMenu menu = buildMessageMenu(message);
            bubble.setOnContextMenuRequested(e -> {
                e.consume();
                menu.show(bubble, e.getScreenX(), e.getScreenY());
            });
            HBox actions = new HBox(buildMessageMenuButton(message));
            actions.setAlignment(Pos.CENTER_RIGHT);
            bubble.getChildren().add(actions);
        }

        if (!mine) {
            Label author = new Label(safe(message.authorDisplayName()));
            author.getStyleClass().add("chat-author");
            bubble.getChildren().add(author);
        }

        List<PrivateChatDtos.PrivateAttachment> attachments = message.attachmentsOrEmpty();
        for (PrivateChatDtos.PrivateAttachment attachment : attachments) {
            bubble.getChildren().add(buildAttachmentNode(attachment));
        }

        String bodyText = message.body() != null && !message.body().isBlank() ? message.body() : null;
        String visibleText = message.deleted() ? "(mensaje eliminado)" : bodyText;
        if (visibleText != null) {
            Label body = new Label(visibleText);
            body.setWrapText(true);
            body.getStyleClass().add(mine ? "chat-body-mine" : "chat-body");
            bubble.getChildren().add(body);
        }

        Label time = new Label(formatBubbleTime(message.createdAt()));
        time.getStyleClass().add(mine ? "chat-time-mine" : "chat-time");
        bubble.getChildren().add(time);

        HBox row = new HBox(bubble);
        row.setFillHeight(false);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private MenuButton buildMessageMenuButton(PrivateChatDtos.PrivateMessage message) {
        MenuButton button = new MenuButton("...");
        button.getStyleClass().add("action-button-secondary");
        button.getItems().addAll(buildMessageMenuItems(message));
        return button;
    }

    private ContextMenu buildMessageMenu(PrivateChatDtos.PrivateMessage message) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(buildMessageMenuItems(message));
        DialogStyler.apply(menu);
        return menu;
    }

    private List<MenuItem> buildMessageMenuItems(PrivateChatDtos.PrivateMessage message) {
        List<MenuItem> items = new ArrayList<>();
        if (message.body() != null && !message.body().isBlank()) {
            MenuItem editItem = new MenuItem("Editar");
            editItem.setOnAction(e -> showEditDialog(message));
            items.add(editItem);
        }
        MenuItem deleteItem = new MenuItem("Eliminar");
        deleteItem.setOnAction(e -> confirmDeleteMessage(message));
        items.add(deleteItem);
        return items;
    }

    private StackPane buildAttachmentNode(PrivateChatDtos.PrivateAttachment attachment) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("chat-attachment-frame");
        frame.setMinSize(240, 170);
        frame.setPrefSize(240, 170);
        frame.setMaxSize(240, 170);

        Label loading = new Label("Cargando imagen...");
        loading.getStyleClass().add("chat-attachment-placeholder");
        frame.getChildren().add(loading);

        String url = attachment.thumbnailUrl() != null && !attachment.thumbnailUrl().isBlank()
                ? attachment.thumbnailUrl()
                : attachment.url();
        if (url == null || url.isBlank()) {
            loading.setText("Imagen no disponible");
            return frame;
        }

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(url);
                Image image = new Image(new ByteArrayInputStream(bytes), 240, 170, true, true);
                Platform.runLater(() -> {
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(240);
                    imageView.setFitHeight(170);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    frame.getChildren().setAll(imageView);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> loading.setText("Imagen no disponible"));
            }
        });

        String originalUrl = attachment.url();
        if (originalUrl != null && !originalUrl.isBlank()) {
            frame.setCursor(Cursor.HAND);
            Tooltip.install(frame, new Tooltip("Ver imagen completa"));
            frame.setOnMouseClicked(e -> openAttachmentViewer(attachment));
        }
        return frame;
    }

    private void openAttachmentViewer(PrivateChatDtos.PrivateAttachment attachment) {
        String url = attachment.url();
        if (url == null || url.isBlank()) {
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.setTitle("Imagen de la conversacion");
        dialog.getDialogPane().getButtonTypes().addAll(SAVE_BUTTON_TYPE, ButtonType.CLOSE);

        StackPane content = new StackPane();
        content.setPrefSize(720, 520);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        content.getChildren().add(spinner);
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(SAVE_BUTTON_TYPE);
        saveButton.setDisable(true);
        final byte[][] loaded = new byte[1][];
        saveButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            if (loaded[0] != null) {
                saveAttachmentToDisk(attachment, loaded[0]);
            }
        });

        final boolean[] closed = {false};
        dialog.setOnHidden(e -> closed[0] = true);

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(url);
                Platform.runLater(() -> {
                    if (closed[0]) {
                        return;
                    }
                    Image image = new Image(new ByteArrayInputStream(bytes), 700, 480, true, true);
                    if (image.isError()) {
                        content.getChildren().setAll(viewerErrorLabel());
                        return;
                    }
                    ImageView view = new ImageView(image);
                    view.setPreserveRatio(true);
                    view.setSmooth(true);
                    view.setFitWidth(700);
                    view.setFitHeight(480);
                    content.getChildren().setAll(view);
                    loaded[0] = bytes;
                    saveButton.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!closed[0]) {
                        content.getChildren().setAll(viewerErrorLabel());
                    }
                });
            }
        });

        dialog.showAndWait();
    }

    private Label viewerErrorLabel() {
        Label label = new Label("No se pudo cargar la imagen");
        label.getStyleClass().add("chat-attachment-placeholder");
        return label;
    }

    private void saveAttachmentToDisk(PrivateChatDtos.PrivateAttachment attachment, byte[] bytes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar imagen");
        String extension = extensionForContentType(attachment.contentType());
        chooser.setInitialFileName("recetas-dm-" + System.currentTimeMillis() + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen", "*" + extension));
        File target = getScene() != null
                ? chooser.showSaveDialog(getScene().getWindow())
                : chooser.showSaveDialog(null);
        if (target == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> onStatus.accept("Imagen guardada en " + target.getName()));
            } catch (IOException ex) {
                Platform.runLater(() -> onStatus.accept("No pudimos guardar la imagen."));
            }
        });
    }

    private static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        String normalized = contentType.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private boolean isMine(PrivateChatDtos.PrivateMessage message) {
        return myUserId != null && myUserId.equals(message.authorUserId());
    }

    private boolean isOwnMutableMessage(PrivateChatDtos.PrivateMessage message) {
        return message != null && !message.deleted() && isMine(message);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void updateLoadOlderBtn() {
        loadOlderBtn.setVisible(hasMoreOlder);
        loadOlderBtn.setManaged(hasMoreOlder);
    }

    private void setSending(boolean isSending) {
        this.sending = isSending;
        sendBtn.setDisable(isSending);
        attachBtn.setDisable(isSending);
    }

    private void updateCharCounter(String value) {
        int length = value == null ? 0 : value.length();
        boolean nearLimit = length >= PrivateChatRepository.MAX_BODY_LENGTH - 160;
        charCounter.setText(length + "/" + PrivateChatRepository.MAX_BODY_LENGTH);
        charCounter.setVisible(nearLimit);
        charCounter.setManaged(nearLimit);
    }

    private PrivateChatRepository privateChatRepository() {
        return context.getPrivateChatRepository();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String formatTime(String iso) {
        ZonedDateTime local = parseLocal(iso);
        return local != null ? local.format(TIME_FORMAT) : safe(iso);
    }

    private static String formatBubbleTime(String iso) {
        ZonedDateTime local = parseLocal(iso);
        if (local == null) {
            return safe(iso);
        }
        String time = local.format(TIME_FORMAT);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate day = local.toLocalDate();
        if (day.isEqual(today)) {
            return time;
        }
        if (day.isEqual(today.minusDays(1))) {
            return "Ayer · " + time;
        }
        String datePart = day.getYear() == today.getYear()
                ? day.format(DAY_FORMAT)
                : day.format(DAY_YEAR_FORMAT);
        return datePart + " · " + time;
    }

    private static ZonedDateTime parseLocal(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault());
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
