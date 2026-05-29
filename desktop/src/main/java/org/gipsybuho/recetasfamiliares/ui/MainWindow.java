package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.core.AppContext;

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
    private String activeView = "dashboard";
    private boolean navigating = false;
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
            } else if (e.isControlDown() && e.getCode() == KeyCode.COMMA) {
                showPreferencesDialog();
                e.consume();
            }
        });

        if (context.getSession().isLoggedIn()) {
            showMain();
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

        VBox sidebar = buildSidebar();
        root.setLeft(sidebar);
        navigateTo("dashboard");
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

        Button btnDashboard = sidebarButton("Inicio", "dashboard");
        Button btnRecipes = sidebarButton("Recetas", "recipes");
        Button btnStock = sidebarButton("Stock", "stock");
        Button btnMenu = sidebarButton("Menú semanal", "menu");
        Button btnShopping = sidebarButton("Lista de la compra", "shopping");
        Button btnNotes = sidebarButton("Notas familiares", "notes");
        Button settingsBtn = new Button("⚙ Ajustes");
        settingsBtn.getStyleClass().add("sidebar-nav-button");
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setOnAction(e -> showPreferencesDialog());
        Tooltip.install(settingsBtn, new Tooltip("Ajustes (Ctrl+,)"));
        VBox.setMargin(settingsBtn, new Insets(2, 8, 0, 8));

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

        VBox bottom = new VBox(8, syncBtn, logoutBtn);
        bottom.setPadding(new Insets(8, 16, 24, 16));

        sidebar.getChildren().addAll(header, globalSearch, btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, settingsBtn, spacer, bottom);
        return sidebar;
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
        alert.showAndWait();
    }

    private void showPreferencesDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ajustes");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(480, 380);
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());

        // ── Sonido ──────────────────────────────────────────────────────────────
        CheckBox cbSounds = new CheckBox("Efectos de sonido");
        cbSounds.setSelected(SoundPlayer.isSoundEnabled());

        // ── Modo de tema ─────────────────────────────────────────────────────────
        Label lblMode = new Label("Modo de interfaz");
        lblMode.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbLight  = new RadioButton("Claro");
        RadioButton rbDark   = new RadioButton("Oscuro");
        RadioButton rbSystem = new RadioButton("Sistema");
        rbLight.setToggleGroup(modeGroup);
        rbDark.setToggleGroup(modeGroup);
        rbSystem.setToggleGroup(modeGroup);
        ThemeManager.ThemeMode currentMode = ThemeManager.getInstance().loadMode();
        (switch (currentMode) {
            case LIGHT  -> rbLight;
            case DARK   -> rbDark;
            case SYSTEM -> rbSystem;
        }).setSelected(true);
        javafx.scene.layout.HBox modeRow = new javafx.scene.layout.HBox(16, rbLight, rbDark, rbSystem);

        // ── Selector de tema ─────────────────────────────────────────────────────
        Label lblTheme = new Label("Color del tema");
        lblTheme.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        ComboBox<ThemeManager.AppTheme> themeCombo = new ComboBox<>();
        themeCombo.getItems().setAll(ThemeManager.AppTheme.values());
        themeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ThemeManager.AppTheme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        themeCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ThemeManager.AppTheme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        themeCombo.setValue(ThemeManager.getInstance().loadTheme());
        themeCombo.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(14,
                cbSounds,
                new Separator(),
                lblMode, modeRow,
                new Separator(),
                lblTheme, themeCombo
        );
        content.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                Preferences.userRoot().node("recetas")
                        .putBoolean("sound", cbSounds.isSelected());
                ThemeManager.ThemeMode mode = rbLight.isSelected() ? ThemeManager.ThemeMode.LIGHT
                        : rbDark.isSelected() ? ThemeManager.ThemeMode.DARK
                        : ThemeManager.ThemeMode.SYSTEM;
                ThemeManager.AppTheme theme = themeCombo.getValue() != null
                        ? themeCombo.getValue() : ThemeManager.AppTheme.BOSQUE;
                ThemeManager.getInstance().applyTheme(theme, mode);
            }
        });
    }
}
