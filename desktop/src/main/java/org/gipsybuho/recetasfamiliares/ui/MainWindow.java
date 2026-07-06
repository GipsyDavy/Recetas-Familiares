package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;

public class MainWindow {

    private final Stage stage;
    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private DashboardView dashboardView;
    private RecipeListView recipeListView;
    private StockView stockView;
    private WeeklyMenuView weeklyMenuView;
    private ShoppingListView shoppingListView;
    private NotesView notesView;
    private GlobalSearchView searchResultsView;
    private FamilyMembersView familyMembersView;
    private ProfileView profileView;
    private String activeView = "dashboard";
    private boolean navigating = false;
    private Button btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, btnSettings, btnMembers;
    private final TextField globalSearch = new TextField();
    private final Label statusBar = new Label("");

    public MainWindow(Stage stage, AppContext context) {
        this.stage = stage;
        this.context = context;
    }

    public void show() {
        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        ThemeManager.getInstance().attach(scene);
        applySavedTypography();

        stage.setTitle("Recetas Familiares");
        stage.setScene(scene);
        statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(0, 12, 4, 12));
        stage.show();
        scene.setOnKeyPressed((KeyEvent e) -> {
            if (e.isControlDown() && e.getCode() == KeyCode.F) {
                globalSearch.requestFocus();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.COMMA
                    && context.getSession().isAdmin()) {
                navigateTo("settings");
                e.consume();
            } else if (e.getCode() == KeyCode.F1 && context.getSession().isLoggedIn()) {
                HelpDialog.show(stage, activeView);
                e.consume();
            }
        });

        if (context.getSession().isLoggedIn()) {
            showMain();
            refreshPersistedRole();
        } else {
            showLogin();
        }
    }

    // ── Login ────────────────────────────────────────────────────────────────

    private void showLogin() {
        LoginView loginView = new LoginView(context, () -> {
            showMain();
            triggerInitialSync();
        });
        root.setLeft(null);
        root.setCenter(loginView);
    }

    // ── Main shell ───────────────────────────────────────────────────────────

    private void showMain() {
        dashboardView = new DashboardView(context, this::triggerSync,
                () -> navigateTo("recipes"),
                () -> navigateTo("stock"),
                () -> navigateTo("notes"));
        recipeListView = new RecipeListView(context, this::triggerSync);
        stockView = new StockView(context, this::triggerSync, this::setStatus);
        weeklyMenuView = new WeeklyMenuView(context, this::triggerSync);
        shoppingListView = new ShoppingListView(context, this::triggerSync);
        notesView = new NotesView(context, this::triggerSync);
        profileView = new ProfileView(context, stage, this::refreshUserCard, this::setStatus);
        if (context.getSession().isAdmin()) {
            familyMembersView = new FamilyMembersView(context);
        }

        VBox sidebar = buildSidebar();
        root.setLeft(sidebar);
        navigateTo("dashboard");
        OnboardingDialog.showIfFirstRun(stage);
    }

    private void refreshPersistedRole() {
        FamilyRole before = context.getSession().getFamilyRole();
        Thread.ofVirtual().start(() -> {
            context.getFamilyRepository().detectAndSaveRole();
            FamilyRole after = context.getSession().getFamilyRole();
            if (!Objects.equals(before, after)) {
                Platform.runLater(this::showMain);
            }
        });
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);
        sidebar.setMinWidth(200);

        Text appName = new Text("Recetas\nFamiliares");
        appName.getStyleClass().add("sidebar-title");
        VBox header = new VBox(appName);
        header.getStyleClass().add("sidebar-header");
        header.setPadding(new Insets(24, 16, 16, 16));

        globalSearch.setPromptText("Buscar en todo...");
        globalSearch.getStyleClass().add("search-field");
        globalSearch.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(globalSearch, new Insets(0, 8, 8, 8));
        globalSearch.textProperty().addListener((obs, old, query) -> {
            if (navigating) return;
            if (query == null || query.trim().length() < 2) {
                if (searchResultsView != null && root.getCenter() == searchResultsView) {
                    navigateTo(activeView);
                }
            } else {
                showSearchResults(query.trim());
            }
        });

        btnDashboard = sidebarButton("🏠  Inicio", "dashboard");
        btnRecipes   = sidebarButton("📖  Recetas", "recipes");
        btnStock     = sidebarButton("🧂  Stock", "stock");
        btnMenu      = sidebarButton("📅  Menú semanal", "menu");
        btnShopping  = sidebarButton("🛒  Lista de la compra", "shopping");
        btnNotes     = sidebarButton("📝  Notas familiares", "notes");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button syncBtn = new Button("Sincronizar");
        syncBtn.getStyleClass().addAll("sidebar-nav-button", "sync-button");
        syncBtn.setMaxWidth(Double.MAX_VALUE);
        syncBtn.setOnAction(e -> triggerSync());

        Button logoutBtn = new Button("Cerrar sesión");
        logoutBtn.getStyleClass().addAll("sidebar-nav-button", "logout-button");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> doLogout());

        Button helpBtn = new Button("❓  Ayuda");
        helpBtn.getStyleClass().add("sidebar-nav-button");
        helpBtn.setMaxWidth(Double.MAX_VALUE);
        Tooltip helpTooltip = new Tooltip("Ayuda (F1)");
        helpTooltip.setShowDelay(Duration.millis(400));
        Tooltip.install(helpBtn, helpTooltip);
        helpBtn.setOnAction(e -> HelpDialog.show(stage, activeView));

        VBox bottom = new VBox(8, helpBtn, syncBtn, logoutBtn);
        bottom.setPadding(new Insets(8, 16, 24, 16));

        HBox userCard = buildUserCard();

        // ── Common buttons ────────────────────────────────────────────────────
        sidebar.getChildren().addAll(header, globalSearch, userCard,
                btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes);

        // ── Admin-only buttons ────────────────────────────────────────────────
        if (context.getSession().isAdmin()) {
            Separator adminSep = new Separator();
            VBox.setMargin(adminSep, new Insets(4, 16, 4, 16));

            btnMembers  = sidebarButton("👨‍👩‍👧  Miembros", "members");
            btnSettings = sidebarButton("⚙  Ajustes", "settings");
            Tooltip.install(btnSettings, new Tooltip("Ajustes (Ctrl+,)"));

            sidebar.getChildren().addAll(adminSep, btnMembers, btnSettings);
        }

        sidebar.getChildren().addAll(spacer, bottom);
        return sidebar;
    }

    private HBox buildUserCard() {
        String displayName = context.getSession().getDisplayName();
        String email = context.getSession().getEmail();
        String cleanName = displayName != null ? displayName.trim() : "";

        Node avatarNode = buildAvatarNode(context.getSession().getAvatarUrl(), cleanName);

        HBox userCard = new HBox(10);
        userCard.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        userCard.setPadding(new Insets(8, 16, 10, 16));

        if (cleanName.isBlank()) {
            userCard.getChildren().add(avatarNode);
        } else {
            Label nameLabel = new Label(cleanName);
            nameLabel.getStyleClass().add("sidebar-user-name");

            Label emailLabel = new Label(truncateEmail(email));
            emailLabel.getStyleClass().add("sidebar-user-email");

            VBox textBox = new VBox(2, nameLabel, emailLabel);

            userCard.getChildren().addAll(avatarNode, textBox);
        }

        // UX-5: la card completa navega al perfil (foto y nombre se editan alli)
        userCard.setStyle("-fx-cursor: hand;");
        Tooltip profileTooltip = new Tooltip("Ver mi perfil");
        profileTooltip.setShowDelay(Duration.millis(400));
        Tooltip.install(userCard, profileTooltip);
        userCard.setOnMouseClicked(e -> navigateTo("profile"));

        return userCard;
    }

    /** Reconstruye la user card del sidebar con fade tras cambiar nombre o avatar. */
    private void refreshUserCard() {
        VBox sidebar = (VBox) root.getLeft();
        if (sidebar == null) return;
        Node oldCard = sidebar.getChildren().get(2);
        HBox newCard = buildUserCard();
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), oldCard);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> {
            sidebar.getChildren().set(2, newCard);
            newCard.setOpacity(0.0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), newCard);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private Node buildAvatarNode(String avatarUrl, String displayName) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            ImageView imageView = new ImageView();
            imageView.setFitWidth(40);
            imageView.setFitHeight(40);
            imageView.setPreserveRatio(false);
            // Carga autenticada en segundo plano: /uploads/** requiere JWT (SEC-3)
            Thread.ofVirtual().start(() -> {
                try {
                    byte[] bytes = context.getApiClient().fetchImage(avatarUrl);
                    Image image = new Image(new java.io.ByteArrayInputStream(bytes), 40, 40, true, true);
                    Platform.runLater(() -> imageView.setImage(image));
                } catch (Exception ignored) {
                    // Sin avatar remoto: queda el circulo vacio hasta recargar
                }
            });
            StackPane pane = new StackPane(imageView);
            pane.setPrefSize(40, 40);
            pane.setMinSize(40, 40);
            pane.setMaxSize(40, 40);
            pane.setClip(new Circle(20, 20, 20));
            return pane;
        }
        String text = (displayName != null && !displayName.isBlank()) ? initials(displayName) : "👤";
        Label label = new Label(text);
        label.getStyleClass().add("avatar-circle");
        label.setTextFill(javafx.scene.paint.Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 18));
        label.setAlignment(javafx.geometry.Pos.CENTER);
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

    private String truncateEmail(String email) {
        if (email == null) return "";
        String cleanEmail = email.trim();
        return cleanEmail.length() > 24 ? cleanEmail.substring(0, 24) : cleanEmail;
    }

    private Button sidebarButton(String label, String view) {
        Button btn = new Button(label);
        btn.getStyleClass().add("sidebar-nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> navigateTo(view));
        VBox.setMargin(btn, new Insets(2, 8, 0, 8));
        return btn;
    }

    private void showSearchResults(String query) {
        if (searchResultsView == null) {
            searchResultsView = new GlobalSearchView(context, this::onSearchResultClicked);
        }
        searchResultsView.search(query);
        root.setCenter(searchResultsView);
    }

    private void onSearchResultClicked(String viewKey) {
        String query = globalSearch.getText().trim();
        navigateTo(viewKey);
        switch (viewKey) {
            case "recipes" -> recipeListView.filterBy(query);
            case "stock"   -> stockView.filterBy(query);
            case "notes"   -> notesView.filterBy(query);
        }
    }

    private void navigateTo(String view) {
        navigating = true;
        activeView = view;
        globalSearch.clear();
        navigating = false;
        updateActiveSidebarButton(view);
        switch (view) {
            case "dashboard" -> {
                setCenterWithFade(dashboardView);
                dashboardView.refresh();
            }
            case "recipes" -> {
                setCenterWithFade(recipeListView);
                recipeListView.refresh();
            }
            case "stock" -> {
                setCenterWithFade(stockView);
                stockView.refresh();
            }
            case "menu" -> {
                setCenterWithFade(weeklyMenuView);
                weeklyMenuView.refresh();
            }
            case "shopping" -> {
                setCenterWithFade(shoppingListView);
                shoppingListView.refresh();
            }
            case "notes" -> {
                setCenterWithFade(notesView);
                notesView.refresh();
            }
            case "profile" -> {
                setCenterWithFade(profileView);
                profileView.refresh();
            }
            case "members" -> {
                if (context.getSession().isAdmin() && familyMembersView != null) {
                    setCenterWithFade(familyMembersView);
                    familyMembersView.refresh();
                }
            }
            case "settings" -> {
                if (context.getSession().isAdmin()) setCenterWithFade(buildSettingsView());
            }
        }
    }

    public void setStatus(String msg) {
        Platform.runLater(() -> statusBar.setText(msg));
    }

    private void setCenterWithFade(Node nextNode) {
        Node prev = root.getCenter();
        if (prev != null) {
            FadeTransition out = new FadeTransition(Duration.millis(180), prev);
            out.setFromValue(1.0);
            out.setToValue(0.0);
            out.setOnFinished(ev -> {
                root.setCenter(nextNode);
                nextNode.setOpacity(0.0);
                FadeTransition in = new FadeTransition(Duration.millis(180), nextNode);
                in.setFromValue(0.0);
                in.setToValue(1.0);
                in.play();
            });
            out.play();
        } else {
            root.setCenter(nextNode);
        }
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    private void triggerInitialSync() {
        Thread.ofVirtual().start(() -> {
            try {
                context.getSyncRepository().pull();
                Platform.runLater(() ->
                    ExpiryNotificationService.showIfNeeded(
                        context.getStockRepository().getCache().getItems(), stage));
            } catch (Exception ignored) {
                // Silent fail on initial sync — UI still works offline
            }
        });
    }

    private void triggerSync() {
        Thread.ofVirtual().start(() -> {
            try {
                context.getSyncRepository().pull();
                Platform.runLater(() -> {
                    if (root.getCenter() instanceof DashboardView) dashboardView.refresh();
                    else if (root.getCenter() instanceof RecipeListView) recipeListView.refresh();
                    else if (root.getCenter() instanceof StockView) stockView.refresh();
                    else if (root.getCenter() instanceof WeeklyMenuView) weeklyMenuView.refresh();
                    else if (root.getCenter() instanceof ShoppingListView) shoppingListView.refresh();
                    else if (root.getCenter() instanceof NotesView) notesView.refresh();
                    ExpiryNotificationService.showIfNeeded(
                        context.getStockRepository().getCache().getItems(), stage);
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        showAlert("Error de sincronización", ex.getMessage()));
            }
        });
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    private void updateActiveSidebarButton(String view) {
        Button[] navButtons = {btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, btnSettings, btnMembers};
        for (Button btn : navButtons) {
            if (btn != null) btn.getStyleClass().remove("sidebar-nav-button-active");
        }
        Button active = switch (view) {
            case "dashboard" -> btnDashboard;
            case "recipes"   -> btnRecipes;
            case "stock"     -> btnStock;
            case "menu"      -> btnMenu;
            case "shopping"  -> btnShopping;
            case "notes"     -> btnNotes;
            case "settings"  -> btnSettings;
            case "members"   -> btnMembers;
            default          -> null;
        };
        if (active != null) active.getStyleClass().add("sidebar-nav-button-active");
    }

    private void doLogout() {
        Thread.ofVirtual().start(() -> {
            context.getAuthRepository().logout();
            Platform.runLater(this::showLogin);
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        DialogStyler.apply(alert);
        alert.showAndWait();
    }

    private VBox buildSettingsView() {
        CheckBox cbSounds = new CheckBox("Efectos de sonido");
        cbSounds.setSelected(SoundPlayer.isSoundEnabled());

        TabPane tabs = new TabPane(
                new Tab("Apariencia", buildAppearanceTab(cbSounds)),
                new Tab("Acerca de", buildAboutTab()),
                new Tab("Diagnostico", buildDiagnosticsTab())
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("settings-tabs");

        VBox shell = new VBox(12, settingsHeader(), tabs);
        shell.getStyleClass().add("settings-shell");
        shell.setPadding(new Insets(24));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return shell;
    }

    private HBox settingsHeader() {
        Label title = new Label("Configuracion");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("Preferencias visuales, informacion de la aplicacion y diagnostico del equipo.");
        subtitle.getStyleClass().add("settings-muted");
        VBox text = new VBox(3, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, text, spacer);
        header.getStyleClass().add("settings-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private ScrollPane buildAppearanceTab(CheckBox cbSounds) {
        cbSounds.setOnAction(e -> {
            Preferences.userRoot().node("recetas").putBoolean("sound", cbSounds.isSelected());
            setStatus(cbSounds.isSelected() ? "Sonido activado" : "Sonido desactivado");
        });
        HBox soundRow = new HBox(cbSounds);
        soundRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16);
        content.getStyleClass().add("settings-tab-content");
        content.getChildren().add(configPanel(
                "Tema de color",
                "Cambia al instante el aspecto visual completo de la aplicacion.",
                createThemeCards()));
        content.getChildren().add(configPanel("Modo oscuro", null, createModeSelector()));
        content.getChildren().add(configPanel("Sonido", null, soundRow));
        content.getChildren().add(configPanel(
                "Tipografia",
                "La fuente y el tamaño se aplican al guardar. El titulo y algunos elementos fijos mantienen su tamaño relativo.",
                createTypographySelector()));
        content.getChildren().add(configPanel("Vista previa", null, buildThemePreview()));
        return settingsScroll(content);
    }

    private VBox configPanel(String title, String description, Node content) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("settings-section");
        Label titleLabel = sectionTitle(title);
        if (description == null || description.isBlank()) {
            panel.getChildren().addAll(titleLabel, content);
        } else {
            Label desc = new Label(description);
            desc.getStyleClass().add("settings-muted");
            desc.setWrapText(true);
            panel.getChildren().addAll(titleLabel, desc, content);
        }
        return panel;
    }

    private Node createThemeCards() {
        FlowPane cards = new FlowPane(12, 12);
        cards.getStyleClass().add("settings-theme-cards");
        for (ThemeManager.AppTheme theme : ThemeManager.AppTheme.values()) {
            cards.getChildren().add(createThemeCard(theme));
        }
        return cards;
    }

    private Button createThemeCard(ThemeManager.AppTheme theme) {
        Button card = new Button();
        card.getStyleClass().add("settings-theme-card");
        if (theme == ThemeManager.getInstance().loadTheme()) {
            card.getStyleClass().add("settings-theme-card-active");
        }
        card.setGraphic(themeCardGraphic(theme));
        card.setOnAction(e -> {
            ThemeManager.getInstance().applyTheme(theme, ThemeManager.getInstance().loadMode());
            setStatus("Tema aplicado: " + theme.displayName());
            root.setCenter(buildSettingsView());
        });
        return card;
    }

    private VBox themeCardGraphic(ThemeManager.AppTheme theme) {
        HBox preview = new HBox();
        preview.getStyleClass().addAll("root", "settings-theme-preview-strip");
        updateThemePreview(preview, theme, ThemeManager.getInstance().loadMode());

        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("settings-theme-mini-sidebar");
        for (int i = 0; i < 4; i++) {
            Region line = new Region();
            line.getStyleClass().add("settings-theme-mini-line");
            sidebar.getChildren().add(line);
        }

        VBox body = new VBox(5);
        body.getStyleClass().add("settings-theme-mini-body");
        body.getChildren().addAll(miniStripe("primary"), miniStripe("accent"), miniStripe("border"));
        HBox.setHgrow(body, Priority.ALWAYS);
        preview.getChildren().addAll(sidebar, body);

        Label name = new Label(theme.displayName() + (theme == ThemeManager.getInstance().loadTheme() ? "  ✓" : ""));
        name.getStyleClass().add("settings-theme-card-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setAlignment(Pos.CENTER);

        VBox box = new VBox(preview, name);
        box.setFillWidth(true);
        return box;
    }

    private Region miniStripe(String role) {
        Region stripe = new Region();
        stripe.getStyleClass().add("settings-theme-mini-" + role);
        return stripe;
    }

    private Node createModeSelector() {
        CheckBox darkMode = new CheckBox("Ejecutar la aplicacion en modo oscuro");
        darkMode.setSelected(ThemeManager.getInstance().isDarkModeActive(ThemeManager.getInstance().loadMode()));
        darkMode.setOnAction(e -> {
            ThemeManager.ThemeMode mode = darkMode.isSelected()
                    ? ThemeManager.ThemeMode.DARK : ThemeManager.ThemeMode.LIGHT;
            ThemeManager.getInstance().applyTheme(ThemeManager.getInstance().loadTheme(), mode);
            setStatus(darkMode.isSelected() ? "Modo oscuro activado" : "Modo claro activado");
            root.setCenter(buildSettingsView());
        });
        HBox row = new HBox(darkMode);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node createTypographySelector() {
        Preferences prefs = Preferences.userRoot().node("recetas/ui");
        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("Segoe UI", "Arial", "Calibri", "Georgia", "Consolas", "Trebuchet MS", "Verdana");
        fontCombo.setValue(prefs.get("fontFamily", "Segoe UI"));

        ComboBox<String> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll("Pequeño", "Normal", "Grande", "Muy grande");
        sizeCombo.setValue(prefs.get("fontSizeLabel", "Normal"));

        Button applyFont = new Button("Aplicar tipografia");
        applyFont.getStyleClass().add("action-button-primary");
        applyFont.setOnAction(e -> {
            prefs.put("fontFamily", fontCombo.getValue());
            prefs.put("fontSizeLabel", sizeCombo.getValue());
            prefs.putInt("fontSize", fontSizeFromLabel(sizeCombo.getValue()));
            applySavedTypography();
            setStatus("Tipografia aplicada correctamente");
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(new Label("Fuente:"), 0, 0);
        grid.add(fontCombo, 1, 0);
        grid.add(new Label("Tamaño:"), 0, 1);
        grid.add(sizeCombo, 1, 1);
        grid.add(applyFont, 1, 2);
        return grid;
    }

    private VBox buildThemePreview() {
        ThemeManager.AppTheme theme = ThemeManager.getInstance().loadTheme();
        ThemeManager.ThemeMode mode = ThemeManager.getInstance().loadMode();
        Label title = new Label("Recetas Familiares");
        title.getStyleClass().add("settings-preview-title");

        Label nav1 = new Label("Inicio");
        nav1.getStyleClass().add("settings-preview-nav-active");
        Label nav2 = new Label("Recetas");
        nav2.getStyleClass().add("settings-preview-nav");
        VBox sidebar = new VBox(8, title, nav1, nav2);
        sidebar.getStyleClass().add("settings-preview-sidebar");

        Label cardTitle = new Label("Arroz familiar");
        cardTitle.getStyleClass().add("recipe-title-small");
        Label cardMeta = new Label("4 porciones · 35 min");
        cardMeta.getStyleClass().add("recipe-meta");
        Button primary = new Button("Guardar");
        primary.getStyleClass().add("action-button-primary");
        Button secondary = new Button("Cancelar");
        secondary.getStyleClass().add("action-button-secondary");
        HBox buttons = new HBox(8, primary, secondary);
        VBox card = new VBox(8, cardTitle, cardMeta, buttons);
        card.getStyleClass().add("settings-preview-card");

        VBox body = new VBox(10, card);
        body.getStyleClass().add("settings-preview-body");
        HBox sample = new HBox(sidebar, body);
        sample.getStyleClass().add("settings-preview-sample");
        HBox.setHgrow(body, Priority.ALWAYS);

        VBox preview = new VBox(sample);
        preview.getStyleClass().addAll("root", "settings-preview");
        updateThemePreview(preview, theme, mode);
        return preview;
    }

    private void updateThemePreview(Parent preview, ThemeManager.AppTheme theme, ThemeManager.ThemeMode mode) {
        preview.getStylesheets().setAll(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        String themeSheet = ThemeManager.getInstance().stylesheetFor(
                theme != null ? theme : ThemeManager.AppTheme.BOSQUE,
                mode != null ? mode : ThemeManager.ThemeMode.SYSTEM);
        if (themeSheet != null) preview.getStylesheets().add(themeSheet);
    }

    private ScrollPane buildAboutTab() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) version = "1.1";

        Label app = new Label("Recetas Familiares");
        app.getStyleClass().add("settings-about-title");
        Label subtitle = new Label("Sistema familiar de gestion culinaria");
        subtitle.getStyleClass().add("settings-muted");
        Label desc = new Label("Aplicacion de escritorio para guardar recetas, gestionar ingredientes, planificar menus, generar listas de compra y conservar notas familiares desde una interfaz unificada conectada al backend de Recetas Familiares.");
        desc.setWrapText(true);
        desc.getStyleClass().add("settings-about-description");
        Label versionBadge = new Label("Version " + version);
        versionBadge.getStyleClass().add("settings-version-badge");

        VBox titlePanel = new VBox(8, app, subtitle, desc, versionBadge);
        HBox logo = new HBox(new Label("RF"));
        logo.getStyleClass().add("settings-about-logo");
        logo.setAlignment(Pos.CENTER);
        HBox headerCard = new HBox(22, logo, titlePanel);
        headerCard.getStyleClass().add("settings-about-header");
        headerCard.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titlePanel, Priority.ALWAYS);

        String[][] appRows = {
                {"Creador", "Gipsybuho"},
                {"Ano", "2026"},
                {"Tecnologia", "JavaFX + Maven"},
                {"Base de datos", "MySQL mediante backend API"}
        };

        String[][] scopeRows = {
                {"Recetas", "Ingredientes, pasos, fotos y PDF"},
                {"Stock", "Caducidades y bajo stock familiar"},
                {"Menus", "Planificacion semanal y mensual"},
                {"Sincronizacion", "Backend compartido con Android e iOS"}
        };

        HBox cards = new HBox(16,
                configPanel("Informacion de la aplicacion", null, aboutInfoGrid(appRows)),
                configPanel("Cobertura funcional", null, aboutInfoGrid(scopeRows))
        );
        HBox.setHgrow(cards.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(cards.getChildren().get(1), Priority.ALWAYS);

        Button onboardingBtn = new Button("Ver guía de bienvenida");
        onboardingBtn.getStyleClass().add("action-button-secondary");
        onboardingBtn.setOnAction(e -> OnboardingDialog.showAgain(stage));
        HBox helpRow = new HBox(onboardingBtn);
        helpRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, headerCard, cards,
                configPanel("Ayuda", "Vuelve a ver la guia de primeros pasos cuando quieras.", helpRow));
        content.getStyleClass().add("settings-tab-content");
        return settingsScroll(content);
    }

    private GridPane aboutInfoGrid(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        for (int i = 0; i < rows.length; i++) {
            Label key = new Label(rows[i][0]);
            key.getStyleClass().add("settings-about-key");
            Label value = new Label(rows[i][1]);
            value.getStyleClass().add("settings-about-value");
            value.setWrapText(true);
            grid.add(key, 0, i);
            grid.add(value, 1, i);
        }
        return grid;
    }

    private Node buildDiagnosticsTab() {
        Label backendStatus = new Label("");
        backendStatus.getStyleClass().add("status-label");

        VBox diagnosticContent = buildDiagnosticContent();
        ScrollPane diagnosticScroll = settingsScroll(diagnosticContent);
        diagnosticScroll.setMaxHeight(Double.MAX_VALUE);

        Button copyBtn = new Button("Copiar diagnóstico");
        copyBtn.getStyleClass().add("action-button-secondary");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(diagnosticText());
            Clipboard.getSystemClipboard().setContent(content);
            backendStatus.setText("Diagnóstico copiado.");
        });

        Button testBtn = new Button("Probar backend");
        testBtn.getStyleClass().add("action-button-primary");
        testBtn.setOnAction(e -> testBackend(backendStatus));

        Button refreshBtn = new Button("Actualizar diagnostico");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> diagnosticScroll.setContent(buildDiagnosticContent()));

        HBox actions = new HBox(10, refreshBtn, testBtn, copyBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(12, actions, diagnosticScroll, backendStatus);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(diagnosticScroll, Priority.ALWAYS);
        StackPane computer = diagnosticComputerIcon();
        computer.setMaxHeight(Double.MAX_VALUE);
        HBox content = new HBox(16, left, computer);
        content.getStyleClass().add("settings-tab-content");
        content.setFillHeight(true);
        content.setMaxHeight(Double.MAX_VALUE);
        return content;
    }

    private VBox buildDiagnosticContent() {
        VBox content = new VBox(10);
        content.getStyleClass().add("settings-diagnostic-list");

        List<String[]> equipo = new ArrayList<>();
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName != null && !computerName.isBlank()) equipo.add(diagRow("Nombre del equipo", computerName));
        Map<String, String> osWmi = wmicQuery("os", "get", "Caption,BuildNumber,OSArchitecture", "/format:value");
        String osDisplay = osWmi.getOrDefault("Caption",
                System.getProperty("os.name", "Desconocido") + " " + System.getProperty("os.version", "")).trim();
        String osBuild = osWmi.get("BuildNumber");
        if (osBuild != null && !osBuild.isBlank()) osDisplay += " (Build " + osBuild + ")";
        equipo.add(diagRow("Sistema operativo", osDisplay));
        equipo.add(diagRow("Arquitectura", osWmi.getOrDefault("OSArchitecture", System.getProperty("os.arch", ""))));
        content.getChildren().add(diagSection("Equipo", equipo));

        List<String[]> cpu = new ArrayList<>();
        Map<String, String> cpuWmi = wmicQuery("cpu", "get", "Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed", "/format:value");
        String procId = System.getenv("PROCESSOR_IDENTIFIER");
        cpu.add(diagRow("Procesador", cpuWmi.getOrDefault("Name",
                procId != null ? procId : System.getProperty("os.arch", "")).trim()));
        cpu.add(diagRow("Nucleos", cpuWmi.getOrDefault("NumberOfCores", "?") + " fisicos / "
                + cpuWmi.getOrDefault("NumberOfLogicalProcessors", String.valueOf(Runtime.getRuntime().availableProcessors())) + " logicos"));
        String mhz = cpuWmi.get("MaxClockSpeed");
        if (mhz != null && !mhz.isBlank()) {
            try {
                cpu.add(diagRow("Frecuencia max.", String.format("%.2f GHz", Integer.parseInt(mhz.trim()) / 1000.0)));
            } catch (NumberFormatException ignored) {
                cpu.add(diagRow("Frecuencia max.", mhz.trim() + " MHz"));
            }
        }
        content.getChildren().add(diagSection("Procesador", cpu));

        List<String[]> ram = new ArrayList<>();
        try {
            com.sun.management.OperatingSystemMXBean osMx =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long total = osMx.getTotalMemorySize();
            long free = osMx.getFreeMemorySize();
            long used = total - free;
            ram.add(diagRow("RAM total", formatBytes(total)));
            ram.add(diagRow("Usada / libre", formatBytes(used) + " / " + formatBytes(free)
                    + "  (" + String.format("%.1f%%", (double) used / total * 100) + " en uso)"));
        } catch (Exception ex) {
            ram.add(diagRow("RAM", "No disponible"));
        }
        content.getChildren().add(diagSection("Memoria RAM", ram));

        List<String[]> disks = new ArrayList<>();
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                long total = store.getTotalSpace();
                if (total <= 0) continue;
                long free = store.getUsableSpace();
                long used = total - free;
                String type = store.type() == null || store.type().isBlank() ? "" : "  [" + store.type() + "]";
                disks.add(diagRow("Disco " + store.name() + type,
                        formatBytes(total) + " total  ·  " + formatBytes(free) + " libres"
                                + "  (" + String.format("%.1f%%", (double) used / total * 100) + " usado)"));
            }
        } catch (Exception ex) {
            disks.add(diagRow("Discos", "No disponible"));
        }
        if (!disks.isEmpty()) content.getChildren().add(diagSection("Almacenamiento", disks));

        Map<String, String> gpuWmi = wmicQuery("path", "win32_videocontroller", "get", "Caption,AdapterRAM", "/format:value");
        String caption = gpuWmi.get("Caption");
        if (caption != null && !caption.isBlank()) {
            List<String[]> gpu = new ArrayList<>();
            gpu.add(diagRow("Tarjeta grafica", caption.trim()));
            String vram = gpuWmi.get("AdapterRAM");
            if (vram != null && !vram.isBlank()) {
                try {
                    gpu.add(diagRow("Memoria GPU", formatBytes(Long.parseLong(vram.trim()))));
                } catch (NumberFormatException ignored) {
                    gpu.add(diagRow("Memoria GPU", vram.trim()));
                }
            }
            content.getChildren().add(diagSection("Graficos", gpu));
        }

        List<String[]> sistema = new ArrayList<>();
        var bounds = javafx.stage.Screen.getPrimary().getBounds();
        sistema.add(diagRow("Resolucion pantalla", (int) bounds.getWidth() + " x " + (int) bounds.getHeight() + " px"));
        sistema.add(diagRow("Java (JVM)", System.getProperty("java.version", "?") + "  ·  " + System.getProperty("java.vendor", "")));
        sistema.add(diagRow("JavaFX", System.getProperty("javafx.version", "N/D")));
        sistema.add(diagRow("Backend configurado", backendUrl()));
        sistema.add(diagRow("Sesion", context.getSession().isLoggedIn()
                ? "Activa (" + nullSafe(context.getSession().getEmail()) + ")" : "No iniciada"));
        sistema.add(diagRow("Tema", ThemeManager.getInstance().loadTheme().displayName() + " / " + ThemeManager.getInstance().loadMode()));
        content.getChildren().add(diagSection("Sistema", sistema));
        return content;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-section-title");
        return label;
    }

    private VBox diagSection(String title, List<String[]> rows) {
        VBox section = new VBox(8);
        section.getStyleClass().add("settings-diagnostic-section");
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("settings-diagnostic-section-title");
        Separator separator = new Separator();
        VBox rowsBox = new VBox(6);
        for (String[] row : rows) {
            rowsBox.getChildren().add(diagnosticRow(row[0], row[1]));
        }
        section.getChildren().addAll(titleLabel, separator, rowsBox);
        return section;
    }

    private static String[] diagRow(String label, String value) {
        return new String[]{label, value};
    }

    private StackPane diagnosticComputerIcon() {
        StackPane panel = new StackPane();
        panel.getStyleClass().add("settings-diagnostic-computer");
        var logoUrl = getClass().getResource("/brand/gipsy-buho-logo.png");
        if (logoUrl != null) {
            ImageView logo = new ImageView(new Image(logoUrl.toExternalForm(), true));
            logo.setFitWidth(170);
            logo.setFitHeight(170);
            logo.setPreserveRatio(true);
            panel.getChildren().add(logo);
        } else {
            Label fallback = new Label("RF");
            fallback.getStyleClass().add("settings-diagnostic-logo-fallback");
            panel.getChildren().add(fallback);
        }
        return panel;
    }

    private HBox diagnosticRow(String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("settings-diagnostic-key");
        keyLabel.setMinWidth(190);
        Label valueLabel = new Label(value != null && !value.isBlank() ? value : "No disponible");
        valueLabel.getStyleClass().add("settings-diagnostic-value");
        valueLabel.setWrapText(true);
        HBox row = new HBox(12, keyLabel, valueLabel);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        return row;
    }

    private ScrollPane settingsScroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");
        return scroll;
    }

    private String diagnosticText() {
        return String.join(System.lineSeparator(),
                "Recetas Familiares - Diagnóstico",
                "Java: " + System.getProperty("java.version", "N/D"),
                "JavaFX: " + System.getProperty("javafx.version", "N/D"),
                "Sistema: " + System.getProperty("os.name", "N/D") + " "
                        + System.getProperty("os.version", "") + " " + System.getProperty("os.arch", ""),
                "Directorio: " + System.getProperty("user.dir", "N/D"),
                "Backend: " + backendUrl(),
                "Sesión: " + (context.getSession().isLoggedIn() ? "Activa" : "No iniciada"),
                "Tema: " + ThemeManager.getInstance().loadTheme().displayName()
                        + " / " + ThemeManager.getInstance().loadMode());
    }

    private Map<String, String> wmicQuery(String... args) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            String[] command = new String[args.length + 1];
            command[0] = "wmic";
            System.arraycopy(args, 0, command, 1, args.length);
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        if (!value.isEmpty() && !result.containsKey(key)) {
                            result.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }

    private String backendUrl() {
        return System.getProperty("api.base.url", "http://localhost:8080/");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private void testBackend(Label status) {
        status.setText("Probando backend...");
        Thread.ofVirtual().start(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(backendUrl()).toURL().openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                Platform.runLater(() -> status.setText("Backend responde: HTTP " + code));
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Backend no disponible: " + ex.getMessage()));
            }
        });
    }

    private int fontSizeFromLabel(String label) {
        return switch (label) {
            case "Pequeño" -> 12;
            case "Grande" -> 15;
            case "Muy grande" -> 17;
            default -> 14;
        };
    }

    private void applySavedTypography() {
        Preferences prefs = Preferences.userRoot().node("recetas/ui");
        String family = prefs.get("fontFamily", "Segoe UI");
        int size = prefs.getInt("fontSize", 14);
        root.setStyle("-fx-font-family: \"" + family + "\"; -fx-font-size: " + size + "px;");
    }
}
