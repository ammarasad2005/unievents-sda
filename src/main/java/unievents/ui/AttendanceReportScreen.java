package unievents.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.shared.EventSummary;
import unievents.shared.Participant;

/**
 * UC10 — Generate Attendance Report: builds the report (registered count,
 * attended count, participant list), persists it and shows a summary card.
 */
public final class AttendanceReportScreen {

    private AttendanceReportScreen() {
    }

    public static Node create(ManagementController controller, UiContext ctx) {
        Label title = new Label("Generate Attendance Report (UC10)");
        title.getStyleClass().add("screen-title");

        ComboBox<EventSummary> event = new ComboBox<>();
        try {
            event.setItems(FXCollections.observableArrayList(controller.getMyEvents()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
        event.setPromptText("Select one of your events");
        event.setPrefWidth(340);

        Label summary = new Label();
        summary.setWrapText(true);

        TableView<Participant> table = new TableView<>();
        table.setPlaceholder(new Label("Generate a report to see the participant list."));

        TableColumn<Participant, String> colName = new TableColumn<>("Participant");
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().fullName()));
        colName.setPrefWidth(220);

        TableColumn<Participant, String> colRoll = new TableColumn<>("Roll number");
        colRoll.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().rollNumber()));
        colRoll.setPrefWidth(130);

        TableColumn<Participant, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().status().name()));
        colStatus.setPrefWidth(120);

        table.getColumns().addAll(colName, colRoll, colStatus);
        table.setPrefHeight(340);

        Button generate = new Button("Generate report");
        generate.setDefaultButton(true);
        generate.setOnAction(e -> {
            EventSummary selected = event.getValue();
            if (selected == null) {
                UniEventsApp.error("Pick an event", "Select one of your events first.");
                return;
            }
            try {
                ManagementController.ReportWithParticipants r =
                        controller.generateAttendanceReport(selected.eventId());
                summary.getStyleClass().clear();
                summary.getStyleClass().add("label-success");
                summary.setText("Report #" + r.report().getReportId() + " saved — registered: "
                        + r.report().getRegisteredCount() + ", attended: "
                        + r.report().getAttendedCount() + " ("
                        + String.format("%.0f%%", r.report().getAttendanceRate()) + ").");
                table.setItems(FXCollections.observableArrayList(r.participants()));
            } catch (RuntimeException ex) {
                UniEventsApp.error("Report failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Event:"), event);

        VBox box = new VBox(10, title, form, new HBox(10, generate), summary, table);
        box.setPadding(new Insets(4));

        return box;
    }
}
