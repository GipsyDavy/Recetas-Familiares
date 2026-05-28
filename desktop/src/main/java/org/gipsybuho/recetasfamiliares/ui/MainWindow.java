package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.Objects;

public class MainWindow {

    private final Stage stage;
    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private DashboardView dashboardView;
    private RecipeListView recipeListView;
    private StockView stockView;
    private WeeklyMenuView weeklyMenuView;
    private ShoppingListView shoppingListView;

    public MainWindow(Stage stage, AppContext context) {
        this.stage = stage;
        this.context = context;
    }

    public void show() {
        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());

        stage.setTitle("Recetas Familiares");
        stage.setScene(scene);
        stage.show();

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
        dashboardView = new DashboardView(context, this::triggerSync, () -> navigateTo("recipes"));
        recipeListView = new RecipeListView(context);
        stockView = new StockView(context);
        weeklyMenuView = new WeeklyMenuView(context);
        shoppingListView = new ShoppingListView(context);

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

        Button btnDashboard = sidebarButton("Inicio", "dashboard");
        Button btnRecipes = sidebarButton("Recetas", "recipes");
        Button btnStock = sidebarButton("Stock", "stock");
        Button btnMenu = sidebarButton("Menú semanal", "menu");
        Button btnShopping = sidebarButton("Lista de la compra", "shopping");

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

        sidebar.getChildren().addAll(header, btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, spacer, bottom);
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

    private void navigateTo(String view) {
        switch (view) {
            case "dashboard" -> {
                root.setCenter(dashboardView);
                dashboardView.refresh();
            }
            case "recipes" -> {
                root.setCenter(recipeListView);
                recipeListView.refresh();
            }
            case "stock" -> {
                root.setCenter(stockView);
                stockView.refresh();
            }
            case "menu" -> {
                root.setCenter(weeklyMenuView);
                weeklyMenuView.refresh();
            }
            case "shopping" -> {
                root.setCenter(shoppingListView);
                shoppingListView.refresh();
            }
        }
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    private void triggerInitialSync() {
        Thread.ofVirtual().start(() -> {
            try {
                context.getSyncRepository().pull();
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
}
