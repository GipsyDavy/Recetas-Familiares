package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

public class FamilyMembersView extends VBox {

    private final AppContext context;
    private final Label statusLabel = new Label();
    private final Label familyNameLabel = new Label("—");
    private final Label roleLabel       = new Label("—");

    public FamilyMembersView(AppContext context) {
        this.context = context;
        build();
        refresh();
    }

    private void build() {
        getStyleClass().add("content-area");
        setSpacing(20);
        setPadding(new Insets(28, 32, 28, 32));

        // ── Header ────────────────────────────────────────────────────────────
        Text title = new Text("👨‍👩‍👧  Miembros de la familia");
        title.getStyleClass().add("view-title");

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        // ── Family info card ─────────────────────────────────────────────────
        familyNameLabel.getStyleClass().add("recipe-title");
        roleLabel.getStyleClass().add("recipe-meta");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(8);
        infoGrid.add(metaKey("Familia:"),    0, 0);
        infoGrid.add(familyNameLabel,        1, 0);
        infoGrid.add(metaKey("Tu rol:"),     0, 1);
        infoGrid.add(roleLabel,              1, 1);

        VBox infoCard = new VBox(8, infoGrid);
        infoCard.getStyleClass().add("detail-card");
        infoCard.setPadding(new Insets(16));

        // ── Current user row ─────────────────────────────────────────────────
        String displayName = context.getSession().getDisplayName();
        String email       = context.getSession().getEmail();
        FamilyRole role    = context.getSession().getFamilyRole();

        TableView<MemberRow> table = buildTable();

        if (displayName != null || email != null) {
            String roleName = role != null ? role.displayName() : "Miembro";
            table.getItems().add(new MemberRow(
                    displayName != null ? displayName : "—",
                    email != null ? email : "—",
                    roleName
            ));
        }

        // ── Coming soon notice ───────────────────────────────────────────────
        Label notice = new Label("📋  La lista completa de miembros y la gestión de invitaciones estarán disponibles en la próxima actualización.");
        notice.getStyleClass().add("empty-state-text");
        notice.setWrapText(true);
        notice.setMaxWidth(Double.MAX_VALUE);

        VBox noticeBox = new VBox(notice);
        noticeBox.getStyleClass().add("detail-card");
        noticeBox.setPadding(new Insets(14, 16, 14, 16));

        // ── Status bar ───────────────────────────────────────────────────────
        statusLabel.getStyleClass().add("status-label");

        getChildren().addAll(header, infoCard, table, noticeBox, statusLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private TableView<MemberRow> buildTable() {
        TableView<MemberRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("Sin datos de miembros"));

        TableColumn<MemberRow, String> nameCol = new TableColumn<>("Nombre");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setMinWidth(160);

        TableColumn<MemberRow, String> emailCol = new TableColumn<>("Correo electrónico");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setMinWidth(200);

        TableColumn<MemberRow, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        roleCol.setMinWidth(120);

        table.getColumns().addAll(nameCol, emailCol, roleCol);
        return table;
    }

    public void refresh() {
        statusLabel.setText("Cargando información de familia...");
        Thread.ofVirtual().start(() -> {
            try {
                FamilyDtos.FamilyResponse[] families = context.getFamilyRepository().loadMyFamilies();
                Platform.runLater(() -> {
                    if (families.length > 0) {
                        FamilyDtos.FamilyResponse fam = families[0];
                        familyNameLabel.setText(fam.name() != null ? fam.name() : "—");
                        FamilyRole role = context.getSession().getFamilyRole();
                        roleLabel.setText(role != null ? role.displayName() : "—");
                    }
                    statusLabel.setText("");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar: " + ex.getMessage()));
            }
        });
    }

    private Label metaKey(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        return lbl;
    }

    // ── Row model ─────────────────────────────────────────────────────────────

    public static final class MemberRow {
        private final String name;
        private final String email;
        private final String role;

        public MemberRow(String name, String email, String role) {
            this.name  = name;
            this.email = email;
            this.role  = role;
        }

        public String getName()  { return name; }
        public String getEmail() { return email; }
        public String getRole()  { return role; }
    }
}
