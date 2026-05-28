package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class WeeklyMenuView extends VBox {

    private static final String[] MEAL_LABELS  = {"Desayuno", "Comida", "Cena", "Merienda"};
    private static final String[] MEAL_TYPES   = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"};
    private static final String[] DAY_NAMES    = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private final AppContext context;
    private final GridPane grid      = new GridPane();
    private final Label weekLabel    = new Label();
    private final Label statusLabel  = new Label();
    private LocalDate weekStart      = currentMonday();

    public WeeklyMenuView(AppContext context) {
        this.context = context;
        build();
    }

    // ── Build ──────────────────────────────────────────────────────────────────

    private void build() {
        getStyleClass().add("menu-view");
        setSpacing(0);
        setPadding(new Insets(24));

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

        weekLabel.getStyleClass().add("menu-week-label");
        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        HBox navBar = new HBox(10, prevBtn, todayBtn, nextBtn, navSpacer, weekLabel);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(12, 0, 16, 0));

        // Grid column constraints: col0 = label, cols1-7 = days (equal flex)
        ColumnConstraints labelCol = new ColumnConstraints(92);
        labelCol.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().add(labelCol);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setFillWidth(true);
            grid.getColumnConstraints().add(dayCol);
        }

        // Grid row constraints: row0 = header, rows1-4 = meals
        RowConstraints headerRow = new RowConstraints(48);
        headerRow.setVgrow(Priority.NEVER);
        grid.getRowConstraints().add(headerRow);
        for (int r = 0; r < 4; r++) {
            RowConstraints mealRow = new RowConstraints(80);
            mealRow.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(mealRow);
        }

        grid.setHgap(8);
        grid.setVgap(8);
        grid.getStyleClass().add("menu-grid");
        VBox.setVgrow(grid, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(8, 0, 0, 0));

        getChildren().addAll(header, navBar, grid, statusLabel);
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    public void refresh() {
        updateWeekLabel();
        buildStaticStructure();
        statusLabel.setText("Cargando...");

        Thread.ofVirtual().start(() -> {
            try {
                List<SyncDtos.MenuDtos.MenuItemDto> items =
                        context.getMenuRepository().loadForWeek(weekStart);
                Platform.runLater(() -> {
                    populateCells(items);
                    statusLabel.setText(items.size() + " entradas esta semana");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudo cargar el menú.");
                });
            }
        });
    }

    // ── Grid structure ─────────────────────────────────────────────────────────

    private void buildStaticStructure() {
        grid.getChildren().clear();

        // Corner cell
        Label corner = new Label("");
        grid.add(corner, 0, 0);

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
                grid.add(emptyCell(), d + 1, m + 1);
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

    // ── Cell builders ──────────────────────────────────────────────────────────

    private VBox emptyCell() {
        VBox cell = new VBox();
        cell.getStyleClass().add("menu-cell");
        cell.setAlignment(Pos.CENTER);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        Label dash = new Label("—");
        dash.getStyleClass().add("menu-cell-empty");
        cell.getChildren().add(dash);
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
        cell.getChildren().add(titleLabel);

        if (item.note() != null && !item.note().isBlank()) {
            Label noteLabel = new Label(item.note());
            noteLabel.getStyleClass().add("menu-cell-note");
            noteLabel.setWrapText(true);
            noteLabel.setMaxWidth(Double.MAX_VALUE);
            cell.getChildren().add(noteLabel);
        }
        return cell;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int mealTypeToRow(String mealType) {
        for (int i = 0; i < MEAL_TYPES.length; i++) {
            if (MEAL_TYPES[i].equals(mealType)) return i + 1;
        }
        return -1;
    }

    private void updateWeekLabel() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy",
                Locale.forLanguageTag("es"));
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(fmt) + " – " + weekEnd.format(fmt));
    }

    private static LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }
}
