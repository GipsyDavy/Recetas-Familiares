package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardView extends ScrollPane {

    private final AppContext context;
    private final VBox recipesSection = new VBox(8);
    private final VBox stockSection = new VBox(6);
    private final Runnable onSyncRequested;
    private final Runnable onNavigateRecipes;

    public DashboardView(AppContext context, Runnable onSyncRequested, Runnable onNavigateRecipes) {
        this.context = context;
        this.onSyncRequested = onSyncRequested;
        this.onNavigateRecipes = onNavigateRecipes;
        build();
    }

    private void build() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("dashboard-scroll");

        VBox root = new VBox(28);
        root.setPadding(new Insets(28, 32, 28, 32));
        root.getStyleClass().add("dashboard-root");

        root.getChildren().addAll(
                buildGreeting(),
                buildQuickActions(),
                buildSection("Recetas recientes", recipesSection, "Ver todas", onNavigateRecipes),
                buildSection("Stock próximo a caducar", stockSection, null, null)
        );

        setContent(root);
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private VBox buildGreeting() {
        Text greeting = new Text("¡Bienvenido de vuelta!");
        greeting.getStyleClass().add("dashboard-greeting");
        Text subtitle = new Text("¿Qué cocinamos hoy?");
        subtitle.getStyleClass().add("dashboard-subtitle");
        VBox box = new VBox(4, greeting, subtitle);
        box.getStyleClass().add("dashboard-greeting-box");
        return box;
    }

    private HBox buildQuickActions() {
        Button syncBtn = new Button("Sincronizar ahora");
        syncBtn.getStyleClass().add("action-button-primary");
        syncBtn.setOnAction(e -> onSyncRequested.run());

        Button recipesBtn = new Button("Todas las recetas");
        recipesBtn.getStyleClass().add("action-button-secondary");
        recipesBtn.setOnAction(e -> onNavigateRecipes.run());

        HBox box = new HBox(12, syncBtn, recipesBtn);
        box.getStyleClass().add("quick-actions");
        return box;
    }

    private VBox buildSection(String title, VBox content, String linkText, Runnable linkAction) {
        HBox header = new HBox();
        header.setSpacing(8);

        Label sectionTitle = new Label(title);
        sectionTitle.getStyleClass().add("dashboard-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().add(sectionTitle);
        if (linkText != null && linkAction != null) {
            Hyperlink link = new Hyperlink(linkText);
            link.getStyleClass().add("dashboard-section-link");
            link.setOnAction(e -> linkAction.run());
            header.getChildren().addAll(spacer, link);
        }

        VBox section = new VBox(10, header, content);
        section.getStyleClass().add("dashboard-section");
        section.setPadding(new Insets(18));
        return section;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void refresh() {
        loadRecentRecipes();
        loadExpiringStock();
    }

    private void loadRecentRecipes() {
        recipesSection.getChildren().clear();
        Label placeholder = new Label("Cargando...");
        placeholder.getStyleClass().add("status-label");
        recipesSection.getChildren().add(placeholder);

        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(0, 5);
                List<RecipeDtos.RecipeDto> recent = page.content().stream()
                        .filter(r -> !r.deleted())
                        .limit(5)
                        .toList();
                Platform.runLater(() -> renderRecipeCards(recent));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    // Show from cache if available
                    var cached = context.getRecipeRepository().getCache().getItems();
                    if (!cached.isEmpty()) {
                        renderRecipeCards(cached.stream().limit(5).toList());
                    } else {
                        recipesSection.getChildren().setAll(noDataLabel("Sin recetas. Sincroniza para cargar."));
                    }
                });
            }
        });
    }

    private void loadExpiringStock() {
        stockSection.getChildren().clear();
        Label placeholder = new Label("Cargando...");
        placeholder.getStyleClass().add("status-label");
        stockSection.getChildren().add(placeholder);

        Thread.ofVirtual().start(() -> {
            try {
                var items = context.getStockRepository().load();
                String horizon = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_DATE);
                List<StockDtos.StockItemDto> expiring = items.stream()
                        .filter(i -> !i.deleted() && i.expiresAt() != null && i.expiresAt().compareTo(horizon) <= 0)
                        .limit(4)
                        .toList();
                Platform.runLater(() -> renderExpiringStock(expiring));
            } catch (Exception ex) {
                Platform.runLater(() -> stockSection.getChildren().setAll(
                        noDataLabel("No se pudo cargar el stock.")));
            }
        });
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private void renderRecipeCards(List<RecipeDtos.RecipeDto> recipes) {
        recipesSection.getChildren().clear();
        if (recipes.isEmpty()) {
            recipesSection.getChildren().add(noDataLabel("Aún no hay recetas. ¡Añade la primera!"));
            return;
        }
        for (var recipe : recipes) {
            recipesSection.getChildren().add(buildRecipeCard(recipe));
        }
    }

    private HBox buildRecipeCard(RecipeDtos.RecipeDto recipe) {
        HBox card = new HBox(16);
        card.getStyleClass().add("dashboard-recipe-card");
        card.setPadding(new Insets(12, 16, 12, 16));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label title = new Label(recipe.title());
        title.getStyleClass().add("recipe-cell-title");

        Label meta = new Label(buildMeta(recipe));
        meta.getStyleClass().add("recipe-cell-meta");

        info.getChildren().addAll(title, meta);

        if (recipe.description() != null && !recipe.description().isBlank()) {
            Label desc = new Label(recipe.description());
            desc.getStyleClass().add("dashboard-recipe-desc");
            desc.setWrapText(false);
            desc.setMaxWidth(400);
            info.getChildren().add(desc);
        }

        card.getChildren().add(info);
        return card;
    }

    private void renderExpiringStock(List<StockDtos.StockItemDto> items) {
        stockSection.getChildren().clear();
        if (items.isEmpty()) {
            stockSection.getChildren().add(noDataLabel("Sin artículos próximos a caducar."));
            return;
        }
        for (var item : items) {
            HBox row = new HBox(12);
            row.getStyleClass().add("stock-expiring-row");

            Label name = new Label(item.ingredientName());
            name.getStyleClass().add("stock-expiring-name");
            HBox.setHgrow(name, Priority.ALWAYS);

            String exp = item.expiresAt() != null
                    ? item.expiresAt().substring(0, Math.min(10, item.expiresAt().length()))
                    : "—";
            Label date = new Label(exp);
            date.getStyleClass().add("stock-expiring-date");

            row.getChildren().addAll(name, date);
            stockSection.getChildren().add(row);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Label noDataLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("no-data-label");
        return l;
    }

    private String buildMeta(RecipeDtos.RecipeDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.servings() != null) sb.append(r.servings()).append(" pers.  ");
        if (r.prepMinutes() != null) sb.append(r.prepMinutes()).append(" min");
        if (r.difficulty() != null) sb.append("  ·  ").append(r.difficulty());
        return sb.toString().trim();
    }
}
