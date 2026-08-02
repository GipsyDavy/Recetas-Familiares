package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WeeklyMenuView extends ScrollPane {

    private static final String[] MEAL_LABELS  = {"Desayuno", "Comida", "Cena", "Merienda"};
    private static final String[] MEAL_TYPES   = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"};
    private static final String[] DAY_NAMES    = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private static final double MENU_THUMB_SIZE = 40;
    private static final int RECIPE_CACHE_PAGE_SIZE = 100;

    private final AppContext context;
    private final Runnable onSync;
    private final VBox content = new VBox();
    private final GridPane grid      = new GridPane();
    private final Label weekLabel    = new Label();
    private final Label statusLabel  = new Label();
    private LocalDate weekStart      = currentMonday();
    private boolean monthView        = false;

    public WeeklyMenuView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        build();
    }

    // ── Build ──────────────────────────────────────────────────────────────────

    private void build() {
        DesktopScroll.configurePage(this, content);
        content.getStyleClass().add("menu-view");
        content.setSpacing(0);
        content.setPadding(new Insets(24));
        setContent(content);

        Label header = new Label("Menú Semanal");
        header.getStyleClass().add("view-header");

        Button prevBtn = new Button("← Anterior");
        prevBtn.getStyleClass().add("action-button-secondary");
        prevBtn.setOnAction(e -> { weekStart = weekStart.minusWeeks(1); refresh(); });

        Button todayBtn = new Button("Hoy");
        todayBtn.getStyleClass().add("action-button-secondary");
        todayBtn.setOnAction(e -> { weekStart = currentMonday(); refresh(); });

        Button nextBtn = new Button("Siguiente →");
        nextBtn.getStyleClass().add("action-button-secondary");
        nextBtn.setOnAction(e -> { weekStart = weekStart.plusWeeks(1); refresh(); });

        Button viewToggleBtn = new Button("Vista mes");
        viewToggleBtn.getStyleClass().add("action-button-secondary");
        viewToggleBtn.setOnAction(e -> {
            monthView = !monthView;
            viewToggleBtn.setText(monthView ? "Vista semana" : "Vista mes");
            refresh();
        });

        weekLabel.getStyleClass().add("menu-week-label");
        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        Button syncBtn = new Button("Actualizar");
        syncBtn.getStyleClass().add("action-button-secondary");
        syncBtn.setOnAction(e -> onSync.run());

        Button exportBtn = new Button("💾 Exportar");
        exportBtn.getStyleClass().add("action-button-secondary");
        exportBtn.setOnAction(e -> exportCurrentView());

        FlowPane navBar = new FlowPane(10, 8, prevBtn, todayBtn, nextBtn, viewToggleBtn, syncBtn, exportBtn, navSpacer, weekLabel);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(12, 0, 16, 0));

        grid.setHgap(8);
        grid.setVgap(8);
        grid.getStyleClass().add("menu-grid");
        VBox.setVgrow(grid, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(8, 0, 0, 0));

        content.getChildren().addAll(header, navBar, grid, statusLabel);
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    public void refresh() {
        if (monthView) {
            refreshMonth();
            return;
        }

        updateWeekLabel();
        buildWeeklyStructure();
        statusLabel.setText("Cargando...");

        Thread.ofVirtual().start(() -> {
            try {
                ensureRecipeCacheLoaded();
                List<SyncDtos.MenuDtos.MenuItemDto> items =
                        context.getMenuRepository().loadForWeek(weekStart);
                Platform.runLater(() -> {
                    populateCells(items);
                    statusLabel.setText(items.size() + " entradas esta semana");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("No se pudo cargar el menú."));
            }
        });
    }

    /**
     * La cache de recetas y la de imagenes se vacian juntas en
     * AppContext.clearFamilyScopedCaches(), asi que abrir el menu sin pasar antes por
     * "Recetas" dejaria todas las celdas sin miniatura. Se repuebla una sola vez, y
     * solo cuando hace falta.
     *
     * NUNCA llamar desde el JavaFX Application Thread: hace red. Un fallo aqui no debe
     * impedir que el menu se pinte, asi que la excepcion se traga: el usuario ve los
     * titulos con placeholder, que es la degradacion prevista.
     */
    private void ensureRecipeCacheLoaded() {
        if (!context.getRecipeRepository().getCache().isEmpty()) {
            return;
        }
        String familyAtStart = context.getSession().getFamilyId();
        try {
            var page = context.getRecipeRepository().loadPage(familyAtStart, 0, RECIPE_CACHE_PAGE_SIZE);
            Platform.runLater(() -> {
                if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                    return;
                }
                context.getRecipeRepository().getCache().replaceAll(
                        page.items().stream().filter(r -> !r.deleted()).toList());
            });
        } catch (Exception ignored) {
            // Sin miniaturas, pero el menu se pinta igual.
        }
    }

    private void refreshMonth() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate calendarStart = monthStart.with(DayOfWeek.MONDAY);
        updateMonthLabel(monthStart);
        buildMonthStructure(monthStart, calendarStart);
        statusLabel.setText("Cargando...");

        Thread.ofVirtual().start(() -> {
            try {
                List<SyncDtos.MenuDtos.MenuItemDto> items = new ArrayList<>();
                for (int w = 0; w < 5; w++) {
                    items.addAll(context.getMenuRepository().loadForWeek(calendarStart.plusWeeks(w)));
                }
                List<SyncDtos.MenuDtos.MenuItemDto> monthItems = items.stream()
                        .filter(item -> isInMonth(item, monthStart))
                        .toList();
                Platform.runLater(() -> {
                    populateMonthCells(monthStart, calendarStart, monthItems);
                    statusLabel.setText(monthItems.size() + " entradas este mes");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("No se pudo cargar el menú."));
            }
        });
    }

    // ── Grid structure ─────────────────────────────────────────────────────────

    private void buildWeeklyStructure() {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        ColumnConstraints labelCol = new ColumnConstraints(92);
        labelCol.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().add(labelCol);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setFillWidth(true);
            grid.getColumnConstraints().add(dayCol);
        }

        RowConstraints headerRow = new RowConstraints(48);
        headerRow.setVgrow(Priority.NEVER);
        grid.getRowConstraints().add(headerRow);
        for (int r = 0; r < 4; r++) {
            RowConstraints mealRow = new RowConstraints(80);
            mealRow.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(mealRow);
        }

        // Corner cell
        grid.add(new Label(""), 0, 0);

        // Day headers (row 0, cols 1-7)
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d/MM");
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            Label dayLabel = new Label(DAY_NAMES[d] + "\n" + day.format(dayFmt));
            dayLabel.getStyleClass().add("menu-day-header");
            dayLabel.setTextAlignment(TextAlignment.CENTER);
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setMaxHeight(Double.MAX_VALUE);
            if (day.equals(LocalDate.now())) {
                dayLabel.getStyleClass().add("menu-day-header-today");
            }
            grid.add(dayLabel, d + 1, 0);
        }

        // Meal row labels (col 0, rows 1-4)
        for (int m = 0; m < 4; m++) {
            Label mealLabel = new Label(MEAL_LABELS[m]);
            mealLabel.getStyleClass().add("menu-meal-label");
            mealLabel.setAlignment(Pos.CENTER);
            mealLabel.setMaxHeight(Double.MAX_VALUE);
            grid.add(mealLabel, 0, m + 1);
        }

        // Empty cells for all 7×4 slots
        for (int m = 0; m < 4; m++) {
            for (int d = 0; d < 7; d++) {
                LocalDate day = weekStart.plusDays(d);
                String mealType = MEAL_TYPES[m];
                grid.add(emptyCell(day, mealType), d + 1, m + 1);
            }
        }
    }

    private void buildMonthStructure(LocalDate monthStart, LocalDate calendarStart) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setFillWidth(true);
            grid.getColumnConstraints().add(dayCol);
        }

        for (int r = 0; r < 6; r++) {
            RowConstraints row = new RowConstraints(r == 0 ? 42 : 108);
            row.setVgrow(r == 0 ? Priority.NEVER : Priority.ALWAYS);
            grid.getRowConstraints().add(row);
        }

        for (int d = 0; d < 7; d++) {
            Label dayLabel = new Label(DAY_NAMES[d]);
            dayLabel.getStyleClass().add("menu-day-header");
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setMaxHeight(Double.MAX_VALUE);
            grid.add(dayLabel, d, 0);
        }

        for (int r = 0; r < 5; r++) {
            for (int d = 0; d < 7; d++) {
                LocalDate day = calendarStart.plusDays((long) r * 7 + d);
                grid.add(monthCell(day, monthStart), d, r + 1);
            }
        }
    }

    // ── Cell population ────────────────────────────────────────────────────────

    private void populateCells(List<SyncDtos.MenuDtos.MenuItemDto> items) {
        for (SyncDtos.MenuDtos.MenuItemDto item : items) {
            if (item.plannedDate() == null || item.mealType() == null) continue;

            LocalDate date;
            try { date = LocalDate.parse(item.plannedDate()); }
            catch (Exception ignored) { continue; }

            long dayOffset = ChronoUnit.DAYS.between(weekStart, date);
            if (dayOffset < 0 || dayOffset > 6) continue;

            int col = (int) dayOffset + 1;
            int row = mealTypeToRow(item.mealType());
            if (row < 0) continue;

            // Replace the placeholder at this slot
            final int fc = col, fr = row;
            grid.getChildren().removeIf(node -> {
                Integer c = GridPane.getColumnIndex(node);
                Integer r = GridPane.getRowIndex(node);
                return fc == (c != null ? c : 0) && fr == (r != null ? r : 0);
            });
            grid.add(filledCell(item), col, row);
        }
    }

    private void populateMonthCells(LocalDate monthStart, LocalDate calendarStart, List<SyncDtos.MenuDtos.MenuItemDto> items) {
        for (SyncDtos.MenuDtos.MenuItemDto item : items) {
            if (item.plannedDate() == null) continue;

            LocalDate date;
            try { date = LocalDate.parse(item.plannedDate()); }
            catch (Exception ignored) { continue; }

            long dayOffset = ChronoUnit.DAYS.between(calendarStart, date);
            if (dayOffset < 0 || dayOffset >= 35) continue;

            int col = (int) (dayOffset % 7);
            int row = (int) (dayOffset / 7) + 1;
            VBox cell = monthCellAt(col, row);
            if (cell == null) continue;

            String title = item.recipeTitle() != null ? item.recipeTitle() : "Sin título";
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("menu-cell-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            cell.getChildren().add(titleLabel);
        }
    }

    // ── Cell builders ──────────────────────────────────────────────────────────

    private VBox emptyCell(LocalDate date, String mealType) {
        VBox cell = new VBox();
        cell.getStyleClass().add("menu-cell");
        cell.setAlignment(Pos.CENTER);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        Label dash = new Label("—");
        dash.getStyleClass().add("menu-cell-empty");
        cell.getChildren().add(dash);
        cell.setOnMouseClicked(e -> showRecipePicker(date, mealType));
        return cell;
    }

    private VBox filledCell(SyncDtos.MenuDtos.MenuItemDto item) {
        VBox cell = new VBox(4);
        cell.getStyleClass().addAll("menu-cell", "menu-cell-filled");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        cell.setPadding(new Insets(8));

        String title = item.recipeTitle() != null ? item.recipeTitle() : "Sin título";
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("menu-cell-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        RecipeThumbnail thumb = new RecipeThumbnail(context, MENU_THUMB_SIZE);
        thumb.show(context.getRecipeRepository().coverUrlFor(item.recipeId()));
        HBox titleRow = new HBox(8, thumb, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        cell.getChildren().add(titleRow);

        if (item.note() != null && !item.note().isBlank()) {
            Label noteLabel = new Label(item.note());
            noteLabel.getStyleClass().add("menu-cell-note");
            noteLabel.setWrapText(true);
            noteLabel.setMaxWidth(Double.MAX_VALUE);
            cell.getChildren().add(noteLabel);
        }

        cell.setOnMouseClicked(e -> confirmRemove(item));
        return cell;
    }

    private VBox monthCell(LocalDate day, LocalDate monthStart) {
        VBox cell = new VBox(4);
        cell.getStyleClass().add("menu-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        cell.setPadding(new Insets(8));

        Label dayLabel = new Label(String.valueOf(day.getDayOfMonth()));
        dayLabel.getStyleClass().add("menu-cell-note");
        if (day.equals(LocalDate.now())) {
            dayLabel.getStyleClass().add("menu-cell-title");
        }
        cell.getChildren().add(dayLabel);

        if (!day.getMonth().equals(monthStart.getMonth())) {
            cell.setOpacity(0.45);
        }

        return cell;
    }

    // ── CRUD actions ───────────────────────────────────────────────────────────

    private void showRecipePicker(LocalDate date, String mealType) {
        List<RecipeDtos.RecipeDto> recipes = List.copyOf(context.getRecipeRepository().getCache().getItems());
        if (recipes.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "No hay recetas en caché. Sincroniza primero.", ButtonType.OK);
            DialogStyler.apply(alert);
            alert.showAndWait();
            return;
        }

        List<String> titles = recipes.stream().map(RecipeDtos.RecipeDto::title).toList();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(titles.get(0), titles);
        dialog.setTitle("Asignar receta");
        dialog.setHeaderText(mealLabel(mealType) + " — " +
                date.format(DateTimeFormatter.ofPattern("EEEE d/MM", Locale.forLanguageTag("es"))));
        dialog.setContentText("Receta:");
        DialogStyler.apply(dialog);

        dialog.showAndWait().ifPresent(selectedTitle -> {
            RecipeDtos.RecipeDto selected = recipes.stream()
                    .filter(r -> selectedTitle.equals(r.title()))
                    .findFirst().orElse(null);
            if (selected == null) return;
            statusLabel.setText("Asignando...");
            Thread.ofVirtual().start(() -> {
                try {
                    context.getMenuRepository().assign(selected.id(), date, mealType);
                    Platform.runLater(this::refresh);
                } catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Error al asignar: " + ex.getMessage()));
                }
            });
        });
    }

    private void confirmRemove(SyncDtos.MenuDtos.MenuItemDto item) {
        String title = item.recipeTitle() != null ? item.recipeTitle() : "esta entrada";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar \"" + title + "\" del menú?",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText(null);
        DialogStyler.apply(alert);
        alert.showAndWait()
                .filter(b -> b == ButtonType.YES)
                .ifPresent(b -> {
                    statusLabel.setText("Eliminando...");
                    Thread.ofVirtual().start(() -> {
                        try {
                            context.getMenuRepository().remove(item.id());
                            Platform.runLater(this::refresh);
                        } catch (Exception ex) {
                            Platform.runLater(() -> statusLabel.setText("Error al eliminar: " + ex.getMessage()));
                        }
                    });
                });
    }

    private void exportCurrentView() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar menú semanal");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Texto (*.txt)", "*.txt"));
        chooser.setInitialFileName("menu_semanal_" + weekStart + ".txt");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        Thread.ofVirtual().start(() -> {
            try {
                List<SyncDtos.MenuDtos.MenuItemDto> items =
                        context.getMenuRepository().loadForWeek(weekStart);
                String content = buildExportText(items);
                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                Platform.runLater(() -> statusLabel.setText("Menú exportado: " + file.getName()));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al exportar: " + ex.getMessage()));
            }
        });
    }

    private String buildExportText(List<SyncDtos.MenuDtos.MenuItemDto> items) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
        sb.append("Menú Semanal — ")
                .append(weekStart.format(fmt)).append(" - ")
                .append(weekStart.plusDays(6).format(fmt)).append("\n");
        sb.append("=".repeat(50)).append("\n\n");

        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            List<SyncDtos.MenuDtos.MenuItemDto> dayItems = items.stream()
                    .filter(item -> day.toString().equals(item.plannedDate()))
                    .toList();
            if (dayItems.isEmpty()) continue;

            sb.append(DAY_NAMES[d]).append(" ").append(day.format(fmt)).append(":\n");
            for (String type : MEAL_TYPES) {
                dayItems.stream()
                        .filter(item -> type.equals(item.mealType()))
                        .forEach(item -> {
                            String label = mealLabel(type);
                            sb.append("  ").append(label).append(": ")
                                    .append(item.recipeTitle() != null ? item.recipeTitle() : "(sin nombre)")
                                    .append("\n");
                        });
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int mealTypeToRow(String mealType) {
        for (int i = 0; i < MEAL_TYPES.length; i++) {
            if (MEAL_TYPES[i].equals(mealType)) return i + 1;
        }
        return -1;
    }

    private String mealLabel(String mealType) {
        for (int i = 0; i < MEAL_TYPES.length; i++) {
            if (MEAL_TYPES[i].equals(mealType)) return MEAL_LABELS[i];
        }
        return mealType;
    }

    private void updateWeekLabel() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy",
                Locale.forLanguageTag("es"));
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(fmt) + " – " + weekEnd.format(fmt));
    }

    private void updateMonthLabel(LocalDate monthStart) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy",
                Locale.forLanguageTag("es"));
        weekLabel.setText(monthStart.format(fmt));
    }

    private boolean isInMonth(SyncDtos.MenuDtos.MenuItemDto item, LocalDate monthStart) {
        if (item.plannedDate() == null) return false;
        try {
            LocalDate date = LocalDate.parse(item.plannedDate());
            return !date.isBefore(monthStart) && date.isBefore(monthStart.plusMonths(1));
        } catch (Exception ignored) {
            return false;
        }
    }

    private VBox monthCellAt(int col, int row) {
        for (javafx.scene.Node node : grid.getChildren()) {
            Integer nodeCol = GridPane.getColumnIndex(node);
            Integer nodeRow = GridPane.getRowIndex(node);
            int c = nodeCol != null ? nodeCol : 0;
            int r = nodeRow != null ? nodeRow : 0;
            if (c == col && r == row && node instanceof VBox cell) {
                return cell;
            }
        }
        return null;
    }

    private static LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }
}
