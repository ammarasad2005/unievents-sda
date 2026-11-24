package unievents.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ConflictController;
import unievents.shared.ConflictDetails;
import unievents.shared.ConflictSummary;
import unievents.shared.ResolutionType;

import java.time.format.DateTimeFormatter;

/**
 * UC12 — Resolve Registration Conflict: open conflicts list, details
 * (registration history, capacity, waitlist) and an apply-resolution form.
 */
public final class ConflictScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private ConflictScreen() {
    }

    public static Node create(ConflictController controller, UiContext ctx) {
        Label title = new Label("Resolve Registration Conflict (UC12)");
        title.getStyleClass().add("screen-title");

        TableView<ConflictSummary> table = new TableView<>();
        table.setPlaceholder(new Label("No open conflicts — all clear."));

        TableColumn<ConflictSummary, Number> colId = new TableColumn<>("#");
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(
                c.getValue().conflictId()));
        colId.setPrefWidth(50);

        TableColumn<ConflictSummary, String> colEvent = new TableColumn<>("Event");
        colEvent.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().eventTitle()));
        colEvent.setPrefWidth(200);

        TableColumn<ConflictSummary, String> colStudent = new TableColumn<>("Concerned student");
        colStudent.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fullName()));
        colStudent.setPrefWidth(160);

        TableColumn<ConflictSummary, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type().name()));
        colType.setPrefWidth(140);

        table.getColumns().addAll(colId, colEvent, colStudent, colType);

        Label details = new Label("Select a conflict to see its details.");
        details.setWrapText(true);

        ComboBox<ResolutionType> resolution = new ComboBox<>();
        resolution.getItems().addAll(ResolutionType.values());
        resolution.setPromptText("Resolution");

        TextArea reason = new TextArea();
        reason.setPromptText("Reason for the decision (required)");
        reason.setPrefRowCount(2);

        Label result = new Label();
        result.setWrapText(true);

        Button detailsBtn = new Button("Show details");
        detailsBtn.getStyleClass().add("button-secondary");
        detailsBtn.setOnAction(e -> {
            ConflictSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UniEventsApp.error("Pick a conflict", "Select a conflict from the list first.");
                return;
            }
            try {
                ConflictDetails d = controller.getConflictDetails(selected.conflictId());
                StringBuilder sb = new StringBuilder();
                sb.append("Conflict #").append(d.conflictId()).append(" — ")
                        .append(d.type()).append(" on \"").append(d.eventTitle()).append("\"\n");
                sb.append("Capacity: ").append(d.capacity()).append("\n");
                sb.append("Waitlist size: ").append(d.waitlist().size()).append("\n");
                sb.append("Concerned registrations: ").append(d.registrationHistory().size()).append("\n");
                d.registrationHistory().forEach(r -> sb.append("  • #")
                        .append(r.getRegistrationId()).append(" ")
                        .append(r.getStudent().getFullName()).append(" — ")
                        .append(r.getStatus())
                        .append(r.getWaitlistPosition() > 0 ? " (waitlist #" + r.getWaitlistPosition() + ")" : "")
                        .append(", registered ").append(D.format(r.getRegisteredAt())).append("\n"));
                details.setText(sb.toString());
            } catch (RuntimeException ex) {
                UniEventsApp.error("Details failed", ex.getMessage());
            }
        });

        Button apply = new Button("Apply resolution");
        apply.setDefaultButton(true);
        apply.setOnAction(e -> {
            ConflictSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UniEventsApp.error("Pick a conflict", "Select a conflict from the list first.");
                return;
            }
            if (resolution.getValue() == null || reason.getText() == null || reason.getText().isBlank()) {
                UniEventsApp.error("Missing data", "Both a resolution and a reason are required.");
                return;
            }
            try {
                var status = controller.resolveConflict(selected.conflictId(),
                        resolution.getValue(), reason.getText());
                result.getStyleClass().clear();
                result.getStyleClass().add("label-success");
                result.setText("Conflict is now " + status + ".");
                reason.clear();
                resolution.setValue(null);
                details.setText("Select a conflict to see its details.");
                reload(table, controller);
            } catch (RuntimeException ex) {
                UniEventsApp.error("Resolution failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Resolution:"), resolution);
        form.add(new Label("Reason:"), 0, 1);
        form.add(reason, 1, 1);

        VBox box = new VBox(10, title, table, new HBox(10, detailsBtn), details, form,
                new HBox(10, apply), result);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(220);
        box.setPadding(new Insets(4));

        reload(table, controller);
        return box;
    }

    private static void reload(TableView<ConflictSummary> table, ConflictController controller) {
        try {
            table.setItems(FXCollections.observableArrayList(controller.getOpenConflicts()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load conflicts", ex.getMessage());
        }
    }
}
