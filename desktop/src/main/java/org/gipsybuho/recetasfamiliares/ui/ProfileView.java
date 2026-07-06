package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

import java.io.File;
import java.util.List;

/**
 * Vista de perfil (UX-5), accesible a todos los roles: avatar, nombre editable,
 * email, familia con rol y stats familiares con fallback local.
 */
public class ProfileView extends ScrollPane {

    private static final int AVATAR_SIZE = 88;

    private final AppContext context;
    private final Stage stage;
    private final Runnable onUserInfoChanged;
    private final java.util.function.Consumer<String> onStatus;

    private final StackPane avatarSlot = new StackPane();
    private final Label nameLabel = new Label();
    private final Label emailLabel = new Label();
    private final Label familyLabel = new Label();
    private final Label roleBadge = new Label();
    private final HBox statsSection = new HBox(16);

    public ProfileView(AppContext context, Stage stage, Runnable onUserInfoChanged,
                       java.util.function.Consumer<String> onStatus) {
        this.context = context;
        this.stage = stage;
        this.onUserInfoChanged = onUserInfoChanged;
        this.onStatus = onStatus;
        build();
    }

    private void build() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("settings-scroll");

        Label title = new Label("Mi perfil");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("Tu cuenta, tu familia y la actividad compartida.");
        subtitle.getStyleClass().add("settings-muted");
        VBox header = new VBox(3, title, subtitle);

        VBox content = new VBox(18, header, buildIdentityCard(), buildFamilyCard(), buildHelpCard());
        content.getStyleClass().add("settings-tab-content");
        content.setPadding(new Insets(24, 28, 28, 28));
        setContent(content);
    }

    private VBox buildIdentityCard() {
        avatarSlot.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        avatarSlot.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatarSlot.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);

        Button changePhotoBtn = new Button("Cambiar foto");
        changePhotoBtn.getStyleClass().add("action-button-secondary");
        changePhotoBtn.setOnAction(e -> chooseAndUploadAvatar());

        nameLabel.getStyleClass().add("profile-name");
        Button editNameBtn = new Button("✏");
        editNameBtn.getStyleClass().add("action-button-secondary");
        editNameBtn.setPadding(new Insets(2, 8, 2, 8));
        Tooltip tooltip = new Tooltip("Editar nombre");
        tooltip.setShowDelay(Duration.millis(400));
        Tooltip.install(editNameBtn, tooltip);
        editNameBtn.setOnAction(e -> editDisplayName());
        HBox nameRow = new HBox(8, nameLabel, editNameBtn);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        emailLabel.getStyleClass().add("settings-muted");

        VBox textCol = new VBox(6, nameRow, emailLabel, changePhotoBtn);
        textCol.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(20, avatarSlot, textCol);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, row);
        card.getStyleClass().add("settings-section");
        return card;
    }

    private VBox buildFamilyCard() {
        Label sectionTitle = new Label("Mi familia");
        sectionTitle.getStyleClass().add("settings-section-title");

        familyLabel.getStyleClass().add("profile-family-name");
        roleBadge.getStyleClass().add("profile-role-badge");
        HBox familyRow = new HBox(10, familyLabel, roleBadge);
        familyRow.setAlignment(Pos.CENTER_LEFT);

        statsSection.setPadding(new Insets(6, 0, 0, 0));

        VBox card = new VBox(10, sectionTitle, familyRow, statsSection);
        card.getStyleClass().add("settings-section");
        return card;
    }

    private VBox buildHelpCard() {
        Label sectionTitle = new Label("Ayuda");
        sectionTitle.getStyleClass().add("settings-section-title");
        Label desc = new Label("Vuelve a ver la guia de primeros pasos cuando quieras.");
        desc.getStyleClass().add("settings-muted");
        desc.setWrapText(true);

        Button onboardingBtn = new Button("Ver guía de bienvenida");
        onboardingBtn.getStyleClass().add("action-button-secondary");
        onboardingBtn.setOnAction(e -> OnboardingDialog.showAgain(stage));

        VBox card = new VBox(10, sectionTitle, desc, onboardingBtn);
        card.getStyleClass().add("settings-section");
        return card;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    public void refresh() {
        String displayName = context.getSession().getDisplayName();
        String cleanName = displayName != null ? displayName.trim() : "";
        nameLabel.setText(cleanName.isBlank() ? "Sin nombre" : cleanName);
        emailLabel.setText(context.getSession().getEmail() != null ? context.getSession().getEmail() : "");
        renderAvatar(cleanName);

        FamilyRole role = context.getSession().getFamilyRole();
        roleBadge.setText(role != null ? role.displayName() : "");
        roleBadge.setVisible(role != null);
        familyLabel.setText("Cargando...");
        loadFamilyName();
        loadFamilyStats();
    }

    private void renderAvatar(String displayName) {
        String avatarUrl = context.getSession().getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            avatarSlot.getChildren().setAll(initialsNode(displayName));
            return;
        }
        // Carga autenticada en segundo plano: /uploads/** requiere JWT (SEC-3)
        avatarSlot.getChildren().setAll(initialsNode(displayName));
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(avatarUrl);
                Image image = new Image(new java.io.ByteArrayInputStream(bytes),
                        AVATAR_SIZE, AVATAR_SIZE, true, true);
                Platform.runLater(() -> {
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(AVATAR_SIZE);
                    imageView.setFitHeight(AVATAR_SIZE);
                    imageView.setPreserveRatio(false);
                    StackPane pane = new StackPane(imageView);
                    pane.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
                    pane.setClip(new Circle(AVATAR_SIZE / 2.0, AVATAR_SIZE / 2.0, AVATAR_SIZE / 2.0));
                    avatarSlot.getChildren().setAll(pane);
                });
            } catch (Exception ignored) {
                // Sin avatar remoto: quedan las iniciales
            }
        });
    }

    private Node initialsNode(String displayName) {
        String text = displayName != null && !displayName.isBlank() ? initials(displayName) : "👤";
        Label label = new Label(text);
        label.getStyleClass().add("avatar-circle");
        label.setTextFill(javafx.scene.paint.Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 32));
        label.setAlignment(Pos.CENTER);
        label.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        label.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        return label;
    }

    private String initials(String displayName) {
        StringBuilder sb = new StringBuilder(2);
        for (String part : displayName.split("\\s+")) {
            if (!part.isBlank()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (sb.length() == 2) break;
            }
        }
        return sb.toString();
    }

    private void loadFamilyName() {
        Thread.ofVirtual().start(() -> {
            try {
                FamilyDtos.FamilyResponse[] families = context.getFamilyRepository().loadMyFamilies();
                String familyId = context.getSession().getFamilyId();
                String name = null;
                for (FamilyDtos.FamilyResponse family : families) {
                    if (family.id() != null && family.id().equals(familyId)) {
                        name = family.name();
                        break;
                    }
                }
                if (name == null && families.length > 0) name = families[0].name();
                String display = name != null ? name : "Sin familia";
                Platform.runLater(() -> familyLabel.setText(display));
            } catch (Exception ex) {
                Platform.runLater(() -> familyLabel.setText("Familia no disponible sin conexión"));
            }
        });
    }

    private void loadFamilyStats() {
        statsSection.getChildren().clear();
        String familyId = context.getSession().getFamilyId();
        if (familyId == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                var stats = context.getFamilyRepository().loadStats(familyId);
                Platform.runLater(() -> renderStats(stats));
            } catch (Exception ex) {
                Platform.runLater(this::renderLocalStats);
            }
        });
    }

    private void renderStats(FamilyDtos.FamilyStatsResponse stats) {
        statsSection.getChildren().clear();
        if (stats == null) return;
        statsSection.getChildren().addAll(
                statChip(stats.totalRecipes() + (stats.totalRecipes() == 1 ? " receta" : " recetas")),
                statChip(stats.totalMembers() + (stats.totalMembers() == 1 ? " miembro" : " miembros")),
                statChip(stats.totalStockItems() + " en despensa"));
        if (stats.lastActivityAt() != null && stats.lastActivityAt().length() >= 10) {
            statsSection.getChildren().add(statChip("Última actividad: " + stats.lastActivityAt().substring(0, 10)));
        }
    }

    /** Fallback local mínimo cuando /stats falla (mismo criterio que DashboardView). */
    private void renderLocalStats() {
        statsSection.getChildren().clear();
        List<RecipeDtos.RecipeDto> cachedRecipes = context.getRecipeRepository().getCache().getItems()
                .stream()
                .filter(recipe -> !recipe.deleted())
                .toList();
        if (cachedRecipes.isEmpty()) return;
        statsSection.getChildren().add(
                statChip(cachedRecipes.size() + (cachedRecipes.size() == 1 ? " receta" : " recetas")));
        cachedRecipes.stream()
                .map(RecipeDtos.RecipeDto::updatedAt)
                .filter(value -> value != null && value.length() >= 10)
                .max(String::compareTo)
                .ifPresent(value -> statsSection.getChildren().add(
                        statChip("Última actividad: " + value.substring(0, 10))));
    }

    private Label statChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("recipe-cell-meta");
        return chip;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    private void chooseAndUploadAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes (JPG, PNG, WebP)", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        if (file.length() > 8L * 1024 * 1024) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "El tamaño máximo permitido es 8 MB.");
            alert.setTitle("Archivo demasiado grande");
            alert.setHeaderText(null);
            DialogStyler.apply(alert);
            alert.showAndWait();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                context.getUserRepository().uploadAvatar(file);
                Platform.runLater(() -> {
                    refresh();
                    onUserInfoChanged.run();
                    onStatus.accept("Foto de perfil actualizada");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("Error al subir la foto: " + ex.getMessage()));
            }
        });
    }

    private void editDisplayName() {
        String current = context.getSession().getDisplayName();
        TextInputDialog dialog = new TextInputDialog(current != null ? current : "");
        dialog.setTitle("Editar nombre");
        dialog.setHeaderText(null);
        dialog.setContentText("Nombre:");
        DialogStyler.apply(dialog);
        dialog.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            Thread.ofVirtual().start(() -> {
                try {
                    context.getUserRepository().updateDisplayName(newName.trim());
                    Platform.runLater(() -> {
                        refresh();
                        onUserInfoChanged.run();
                        onStatus.accept("Nombre actualizado");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> onStatus.accept("Error al actualizar nombre: " + ex.getMessage()));
                }
            });
        });
    }
}
