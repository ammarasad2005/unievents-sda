package unievents.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;
import unievents.shared.RegistrationStatus;
import unievents.shared.RegistrationSummary;

import java.time.format.DateTimeFormatter;

/**
 * UC3 — My Registrations: list of the student's registrations with cancel.
 * Cancellation feedback states whether a waitlisted student was promoted.
 */
public final class MyRegistrationsScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private MyRegistrationsScreen() {
    }

    public static Node create(StudentController controller, UiContext ctx) {
        Label title = new Label("My Registrations");
        title.getStyleClass().add("screen-title");

        TableView<RegistrationSummary> table = new TableView<>();
        table.setPlaceholder(new Label("No registrations yet — browse events to register."));

        TableColumn<RegistrationSummary, String> colEvent = new TableColumn<>("Event");
        colEvent.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().eventTitle()));
        colEvent.setPrefWidth(220);

        TableColumn<RegistrationSummary, String> colStart = new TableColumn<>("Starts");
        colStart.setCellValueFactory(c -> new SimpleObjectProperty<>(
                c.getValue().startDateTime() == null ? "" : D.format(c.getValue().startDateTime())));
        colStart.setPrefWidth(150);

        TableColumn<RegistrationSummary, String> colVenue = new TableColumn<>("Venue");
        colVenue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().venue()));
        colVenue.setPrefWidth(130);

        TableColumn<RegistrationSummary, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().status() == RegistrationStatus.WAITLISTED && c.getValue().waitlistPosition() != null
                        ? "WAITLISTED #" + c.getValue().waitlistPosition()
                        : c.getValue().status().name()));
        colStatus.setPrefWidth(140);

        table.getColumns().addAll(colEvent, colStart, colVenue, colStatus);

        Label status = new Label();
        status.setWrapText(true);

        Button cancel = new Button("Cancel registration");
        cancel.setDisable(true);
        cancel.setOnAction(e -> {
            RegistrationSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.status() == RegistrationStatus.ATTENDED) {
                UniEventsApp.error("Cannot cancel",
                        "An attended registration cannot be cancelled.");
                return;
            }
            try {
                RegistrationStatus result = controller.cancelRegistration(selected.registrationId());
                status.getStyleClass().add("label-success");
                status.setText("Registration cancelled (" + result + "). "
                        + "If you held a seat, the next waitlisted student was promoted automatically.");
                reload(table, controller);
            } catch (RuntimeException ex) {
                UniEventsApp.error("Cancel failed", ex.getMessage());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> cancel.setDisable(n == null));

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-secondary");
        refresh.setOnAction(e -> reload(table, controller));

        VBox box = new VBox(10, title, table, new HBox(10, cancel, refresh), status);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(420);
        box.setPadding(new Insets(4));

        reload(table, controller);
        return box;
    }

    private static void reload(TableView<RegistrationSummary> table, StudentController controller) {
        try {
            table.setItems(FXCollections.observableArrayList(controller.getMyRegistrations()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load registrations", ex.getMessage());
        }
    }
}
