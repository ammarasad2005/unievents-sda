package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.shared.EventDetails;
import unievents.shared.EventStatus;
import unievents.shared.EventSummary;

/**
 * UC7 — Change Event Status: pick one of my events, choose the new status
 * (valid transitions only, enforced by the domain) and an optional/required
 * reason for cancellation.
 */
public final class EventStatusScreen {

    private EventStatusScreen() {
    }

    public static Node create(ManagementController controller, UiContext ctx) {
        Label title = new Label("Change Event Status (UC7)");
        title.getStyleClass().add("screen-title");

        ComboBox<EventSummary> event = new ComboBox<>();
        try {
            event.setItems(javafx.collections.FXCollections
                    .observableArrayList(controller.getMyEvents()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
        event.setPromptText("Select one of your events");
        event.setPrefWidth(340);

        ComboBox<EventStatus> status = new ComboBox<>();
        status.getItems().addAll(EventStatus.values());
        status.setPromptText("New status");

        Label current = new Label();
        current.setWrapText(true);
        event.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv == null) {
                current.setText("");
            } else {
                current.setText("Current status: " + nv.status()
                        + " — allowed: DRAFT→PUBLISHED/CANCELLED, PUBLISHED→CANCELLED/COMPLETED.");
            }
        });

        TextArea reason = new TextArea();
        reason.setPromptText("Reason (required when cancelling)");
        reason.setPrefRowCount(2);

        Label result = new Label();
        result.setWrapText(true);

        Button apply = new Button("Apply status change");
        apply.setDefaultButton(true);
        apply.setOnAction(e -> {
            result.getStyleClass().clear();
            EventSummary selected = event.getValue();
            EventStatus newStatus = status.getValue();
            if (selected == null || newStatus == null) {
                result.getStyleClass().add("label-error");
                result.setText("Select an event and a new status.");
                return;
            }
            try {
                EventStatus applied = controller.changeEventStatus(selected.eventId(), newStatus,
                        reason.getText());
                result.getStyleClass().add("label-success");
                result.setText("Status is now " + applied + " (persisted).");
                reason.clear();
                // refresh the picker so the current-status hint stays true
                event.setItems(javafx.collections.FXCollections
                        .observableArrayList(controller.getMyEvents()));
            } catch (RuntimeException ex) {
                UniEventsApp.error("Status change failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Event:"), event);
        form.addRow(1, new Label("New status:"), status);
        form.add(new Label("Reason:"), 0, 2);
        form.add(reason, 1, 2);

        VBox card = new VBox(12, current, form, new HBox(10, apply), result);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }
}
