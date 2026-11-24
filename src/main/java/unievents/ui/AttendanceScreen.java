package unievents.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.shared.AttendanceRow;
import unievents.shared.EventSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * UC9 — Attendance Sheet: loads the sheet + check-in code, lets the organizer
 * select rows and mark them attended with MANUAL method.
 */
public final class AttendanceScreen {

    private AttendanceScreen() {
    }

    public static Node create(ManagementController controller, UiContext ctx) {
        Label title = new Label("Attendance Sheet (UC9)");
        title.getStyleClass().add("screen-title");

        ComboBox<EventSummary> event = new ComboBox<>();
        try {
            event.setItems(FXCollections.observableArrayList(controller.getMyEvents()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
        event.setPromptText("Select one of your events");
        event.setPrefWidth(340);

        Button load = new Button("Load sheet");
        load.getStyleClass().add("button-secondary");

        Label codeLabel = new Label();
        codeLabel.setWrapText(true);

        TableView<Row> table = new TableView<>();
        table.setPlaceholder(new Label("Load a sheet for one of your events."));

        TableColumn<Row, String> colName = new TableColumn<>("Student");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().row().fullName()));
        colName.setPrefWidth(200);

        TableColumn<Row, String> colRoll = new TableColumn<>("Roll number");
        colRoll.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().row().rollNumber()));
        colRoll.setPrefWidth(120);

        TableColumn<Row, Boolean> colPresent = new TableColumn<>("Present");
        colPresent.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().present().get()));
        colPresent.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final CheckBox box = new CheckBox();
            {
                box.setDisable(true);
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
                if (!empty) {
                    Row row = getTableView().getItems().get(getIndex());
                    box.setSelected(row.present().get());
                    row.present().addListener((o, ov, nv) -> box.setSelected(nv));
                }
            }
        });
        colPresent.setPrefWidth(90);

        TableColumn<Row, String> colState = new TableColumn<>("Status");
        colState.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().row().alreadyCheckedIn() ? "ATTENDED" : "—"));
        colState.setPrefWidth(100);

        table.getColumns().addAll(colName, colRoll, colPresent, colState);

        Runnable loadSheet = () -> {
            EventSummary selected = event.getValue();
            if (selected == null) {
                UniEventsApp.error("Pick an event", "Select one of your events first.");
                return;
            }
            try {
                ManagementController.AttendanceSheet sheet =
                        controller.getAttendanceSheet(selected.eventId());
                codeLabel.setText("Check-in code for self check-in (UC4): " + sheet.checkInCode());
                List<Row> rows = new ArrayList<>();
                for (AttendanceRow r : sheet.rows()) {
                    rows.add(new Row(r));
                }
                table.setItems(FXCollections.observableArrayList(rows));
            } catch (RuntimeException ex) {
                UniEventsApp.error("Could not load sheet", ex.getMessage());
            }
        };
        load.setOnAction(e -> loadSheet.run());

        Label result = new Label();
        result.setWrapText(true);

        Button mark = new Button("Mark selected as attended (MANUAL)");
        mark.setOnAction(e -> {
            EventSummary selected = event.getValue();
            if (selected == null) {
                UniEventsApp.error("Pick an event", "Select one of your events first.");
                return;
            }
            List<Integer> ids = table.getItems().stream()
                    .filter(r -> r.present().get() && !r.row().alreadyCheckedIn())
                    .map(r -> r.row().registrationId())
                    .toList();
            if (ids.isEmpty()) {
                result.getStyleClass().clear();
                result.getStyleClass().add("label-error");
                result.setText("Tick at least one not-yet-attended row.");
                return;
            }
            try {
                int saved = controller.recordAttendance(selected.eventId(), ids);
                result.getStyleClass().clear();
                result.getStyleClass().add("label-success");
                result.setText(saved + " registration(s) marked attended (MANUAL).");
                loadSheet.run();
            } catch (RuntimeException ex) {
                UniEventsApp.error("Attendance failed", ex.getMessage());
            }
        });

        HBox toolbar = new HBox(10, load, mark);
        VBox box = new VBox(10, title, new HBox(10, event), codeLabel, table, toolbar, result);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(360);
        box.setPadding(new Insets(4));

        return box;
    }

    /** UI row: attendance data + editable present flag. */
    private record Row(AttendanceRow row, SimpleBooleanProperty present) {
        Row(AttendanceRow row) {
            this(row, new SimpleBooleanProperty(row.alreadyCheckedIn()));
        }
    }
}
