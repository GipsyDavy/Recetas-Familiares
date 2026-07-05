package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;

/**
 * Dashboard principal — GridPane 2 columnas:
 *   Col 0 (60%): recetas recientes (tarjetas verticales, scroll propio)
 *   Col 1 (40%): stock próximo a caducar + acciones rápidas
 * Fila 0 (colspan 2): saludo + acciones rápidas
 */
public class DashboardView extends ScrollPane {

    private final AppContext context;
    private final VBox recipesSection  = new VBox(8);
    private final VBox stockSection    = new VBox(6);
    private final VBox menuTodaySection = new VBox(4);
    private final HBox statsSection    = new HBox(16);
    private final Runnable onSyncRequested;
    private final Runnable onNavigateRecipes;
    private final Runnable onNavigateStock;
    private final Runnable onNavigateNotes;

    public DashboardView(AppContext context, Runnable onSyncRequested, Runnable onNavigateRecipes,
                         Runnable onNavigateStock, Runnable onNavigateNotes) {
        this.context = context;
        this.onSyncRequested = onSyncRequested;
        this.onNavigateRecipes = onNavigateRecipes;
        this.onNavigateStock = onNavigateStock;
        this.onNavigateNotes = onNavigateNotes;
        build();
    }

    private void build() {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("dashboard-scroll");

        // ── Header row (colspan 2) ────────────────────────────────────────────
        VBox header = buildHeader();

        // ── Column 0: recetas recientes ───────────────────────────────────────
        VBox recipesCard = buildCard("Recetas recientes", recipesSection, "Ver todas", onNavigateRecipes);
        GridPane.setVgrow(recipesCard, Priority.ALWAYS);
        GridPane.setHgrow(recipesCard, Priority.ALWAYS);

        // ── Column 1: stock + sync ────────────────────────────────────────────
        VBox stockCard = buildCard("Stock próximo a caducar", stockSection, null, null);

        Button syncBtn = new Button("Sincronizar ahora");
        syncBtn.getStyleClass().add("action-button-primary");
        syncBtn.setMaxWidth(Double.MAX_VALUE);
        syncBtn.setOnAction(e -> onSyncRequested.run());

        Button recipesBtn = new Button("Todas las recetas");
        recipesBtn.getStyleClass().add("action-button-secondary");
        recipesBtn.setMaxWidth(Double.MAX_VALUE);
        recipesBtn.setOnAction(e -> onNavigateRecipes.run());

        Button stockNavBtn = new Button("Stock familiar");
        stockNavBtn.getStyleClass().add("action-button-secondary");
        stockNavBtn.setMaxWidth(Double.MAX_VALUE);
        stockNavBtn.setOnAction(e -> onNavigateStock.run());

        Button notesNavBtn = new Button("Notas familiares");
        notesNavBtn.getStyleClass().add("action-button-secondary");
        notesNavBtn.setMaxWidth(Double.MAX_VALUE);
        notesNavBtn.setOnAction(e -> onNavigateNotes.run());

        VBox rightCol = new VBox(12, stockCard, syncBtn, recipesBtn, stockNavBtn, notesNavBtn);
        GridPane.setVgrow(rightCol, Priority.ALWAYS);

        // ── Grid assembly ─────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(0);
        grid.setPadding(new Insets(24, 28, 28, 28));
        grid.getStyleClass().add("dashboard-root");

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPercentWidth(60);
        col0.setHgrow(Priority.ALWAYS);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(40);
        col1.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(col0, col1);

        RowConstraints rowHeader = new RowConstraints();
        rowHeader.setVgrow(Priority.NEVER);

        RowConstraints rowContent = new RowConstraints();
        rowContent.setVgrow(Priority.ALWAYS);

        grid.getRowConstraints().addAll(rowHeader, rowContent);

        GridPane.setColumnSpan(header, 2);
        grid.add(header, 0, 0);
        grid.add(recipesCard, 0, 1);
        grid.add(rightCol, 1, 1);

        setContent(grid);
    }

    // ── Header (colspan 2) ────────────────────────────────────────────────────

    private VBox buildHeader() {
        Text greeting = new Text("¡Bienvenido de vuelta!");
        greeting.getStyleClass().add("dashboard-greeting");
        Text subtitle = new Text("¿Qué cocinamos hoy?");
        subtitle.getStyleClass().add("dashboard-subtitle");
        statsSection.setPadding(new Insets(8, 0, 0, 0));
        VBox box = new VBox(4, greeting, subtitle, statsSection, menuTodaySection);
        box.getStyleClass().add("dashboard-greeting-box");
        box.setPadding(new Insets(0, 0, 20, 0));
        return box;
    }

    // ── Card builder ──────────────────────────────────────────────────────────

    private VBox buildCard(String title, VBox content, String linkText, Runnable linkAction) {
        HBox header = new HBox();
        header.setSpacing(8);

        Label sectionTitle = new Label(title);
        sectionTitle.getStyleClass().add("dashboard-section-title");

        header.getChildren().add(sectionTitle);
        if (linkText != null && linkAction != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Hyperlink link = new Hyperlink(linkText);
            link.getStyleClass().add("dashboard-section-link");
            link.setOnAction(e -> linkAction.run());
            header.getChildren().addAll(spacer, link);
        }

        VBox.setVgrow(content, Priority.ALWAYS);
        VBox card = new VBox(12, header, content);
        card.getStyleClass().add("dashboard-section");
        card.setPadding(new Insets(18));
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void refresh() {
        loadRecentRecipes();
        loadExpiringStock();
        loadTodayMenu();
        loadFamilyStats();
    }

    /** UX-6: stats familiares del endpoint /stats. Si falla, la fila queda vacía. */
    private void loadFamilyStats() {
        String familyId = context.getSession().getFamilyId();
        if (familyId == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                var stats = context.getFamilyRepository().loadStats(familyId);
                Platform.runLater(() -> renderFamilyStats(stats));
            } catch (Exception ex) {
                Platform.runLater(() -> statsSection.getChildren().clear());
            }
        });
    }

    private void renderFamilyStats(FamilyDtos.FamilyStatsResponse stats) {
        statsSection.getChildren().clear();
        if (stats == null) {
            return;
        }
        statsSection.getChildren().addAll(
                statChip(stats.totalRecipes() + (stats.totalRecipes() == 1 ? " receta" : " recetas")),
                statChip(stats.totalMembers() + (stats.totalMembers() == 1 ? " miembro" : " miembros")),
                statChip(stats.totalStockItems() + " en despensa")
        );
        if (stats.lastActivityAt() != null && stats.lastActivityAt().length() >= 10) {
            statsSection.getChildren().add(statChip("Última actividad: " + stats.lastActivityAt().substring(0, 10)));
        }
    }

    private Label statChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("recipe-cell-meta");
        return chip;
    }

    private void loadRecentRecipes() {
        recipesSection.getChildren().setAll(loadingLabel());

        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getRecipeRepository().loadPage(0, 5);
                List<RecipeDtos.RecipeDto> recent = page.items().stream()
                        .filter(r -> !r.deleted()).limit(5).toList();
                Platform.runLater(() -> renderRecipeCards(recent));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    var cached = context.getRecipeRepository().getCache().getItems();
                    if (!cached.isEmpty()) renderRecipeCards(cached.stream().limit(5).toList());
                    else recipesSection.getChildren().setAll(noDataLabel("Sin recetas. Sincroniza para cargar."));
                });
            }
        });
    }

    private void loadTodayMenu() {
        menuTodaySection.getChildren().clear();
        Thread.ofVirtual().start(() -> {
            try {
                LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
                var allItems = context.getMenuRepository().loadForWeek(monday);
                String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
                List<SyncDtos.MenuDtos.MenuItemDto> todayItems = allItems.stream()
                        .filter(i -> today.equals(i.plannedDate()))
                        .toList();
                Platform.runLater(() -> renderTodayMenu(todayItems));
            } catch (Exception ex) {
                Platform.runLater(() -> menuTodaySection.getChildren().clear());
            }
        });
    }

    private void renderTodayMenu(List<SyncDtos.MenuDtos.MenuItemDto> items) {
        menuTodaySection.getChildren().clear();
        if (items.isEmpty()) return;
        Label sectionLabel = new Label("Menú de hoy");
        sectionLabel.getStyleClass().add("dashboard-section-title");
        menuTodaySection.getChildren().add(sectionLabel);
        for (var item : items) {
            String title = item.recipeTitle() != null ? item.recipeTitle()
                    : item.note() != null ? item.note() : "—";
            Label row = new Label(mealTypeLabel(item.mealType()) + "  " + title);
            row.getStyleClass().add("recipe-cell-meta");
            menuTodaySection.getChildren().add(row);
        }
    }

    private String mealTypeLabel(String type) {
        return switch (type) {
            case "BREAKFAST" -> "☀️ Desayuno:";
            case "LUNCH"     -> "🍽 Almuerzo:";
            case "SNACK"     -> "🫖 Merienda:";
            case "DINNER"    -> "🌙 Cena:";
            default          -> type + ":";
        };
    }

    private void loadExpiringStock() {
        stockSection.getChildren().setAll(loadingLabel());

        Thread.ofVirtual().start(() -> {
            try {
                var items = context.getStockRepository().load();
                String horizon = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_DATE);
                List<StockDtos.StockItemDto> expiring = items.stream()
                        .filter(i -> !i.deleted() && i.expiresAt() != null
                                && i.expiresAt().compareTo(horizon) <= 0)
                        .limit(6)
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
        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.02);
            st.setToY(1.02);
            st.play();
        });
        card.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label title = new Label(recipe.title());
        title.getStyleClass().add("recipe-cell-title");
        title.setWrapText(false);

        Label meta = new Label(buildMeta(recipe));
        meta.getStyleClass().add("recipe-cell-meta");

        info.getChildren().addAll(title, meta);

        if (recipe.description() != null && !recipe.description().isBlank()) {
            Label desc = new Label(recipe.description());
            desc.getStyleClass().add("dashboard-recipe-desc");
            desc.setWrapText(true);
            desc.setMaxWidth(Double.MAX_VALUE);
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

            Label name = new Label(item.name());
            name.getStyleClass().add("stock-expiring-name");
            HBox.setHgrow(name, Priority.ALWAYS);

            String exp = item.expiresAt() != null
                    ? item.expiresAt().substring(0, Math.min(10, item.expiresAt().length())) : "—";
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

    private HBox loadingLabel() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        Label l = new Label("Cargando...");
        l.getStyleClass().add("status-label");
        HBox hbox = new HBox(8, spinner, l);
        hbox.setPadding(new Insets(8, 0, 8, 0));
        return hbox;
    }

    private String buildMeta(RecipeDtos.RecipeDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.servings() != null) sb.append(r.servings()).append(" pers.  ");
        if (r.prepMinutes() != null) sb.append(r.prepMinutes()).append(" min");
        if (r.difficulty() != null) sb.append("  ·  ").append(r.difficulty());
        return sb.toString().trim();
    }
}
