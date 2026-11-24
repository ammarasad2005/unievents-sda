package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.shared.AudienceType;
import unievents.shared.EventSummary;

/**
 * UC8 — Send Event Announcement: shows audience counts (registered,
 * waitlisted, both) before sending; recipients get in-app notifications.
 */
public final class AnnouncementScreen {

    private AnnouncementScreen() {
    }

    public static Node create(ManagementController controller, UiContext ctx) {
        Label title = new Label("Send Event Announcement (UC8)");
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

        Label counts = new Label("Audience counts appear once you pick an event.");
        counts.setWrapText(true);
        event.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv == null) {
                counts.setText("Audience counts appear once you pick an event.");
                return;
            }
            try {
                var c = controller.getAudienceCounts(nv.eventId());
                counts.setText("Registered: " + c.registeredCount()
                        + " · Waitlisted: " + c.waitlistedCount()
                        + " · Both: " + c.bothCount());
            } catch (RuntimeException ex) {
                UniEventsApp.error("Could not load counts", ex.getMessage());
            }
        });

        TextField subject = new TextField();
        subject.setPromptText("Announcement subject");
        TextArea message = new TextArea();
        message.setPromptText("Message to the selected audience");
        message.setPrefRowCount(3);

        ComboBox<AudienceType> audience = new ComboBox<>();
        audience.getItems().addAll(AudienceType.values());
        audience.setValue(AudienceType.BOTH);
        audience.setPromptText("Audience");

        Label result = new Label();
        result.setWrapText(true);

        Button send = new Button("Send announcement");
        send.setDefaultButton(true);
        send.setOnAction(e -> {
            result.getStyleClass().clear();
            EventSummary selected = event.getValue();
            if (selected == null || subject.getText() == null || subject.getText().isBlank()
                    || message.getText() == null || message.getText().isBlank()
                    || audience.getValue() == null) {
                result.getStyleClass().add("label-error");
                result.setText("Event, subject, message and audience are all required.");
                return;
            }
            try {
                var announcement = controller.sendAnnouncement(selected.eventId(),
                        subject.getText(), message.getText(), audience.getValue());
                result.getStyleClass().add("label-success");
                result.setText("Announcement #" + announcement.getAnnouncementId()
                        + " sent to " + audience.getValue() + " — every recipient was notified in-app.");
                subject.clear();
                message.clear();
            } catch (RuntimeException ex) {
                UniEventsApp.error("Announcement failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Event:"), event);
        form.addRow(1, new Label("Audience:"), audience);
        form.addRow(2, new Label("Subject:"), subject);
        form.add(new Label("Message:"), 0, 3);
        form.add(message, 1, 3);

        VBox card = new VBox(12, counts, form, new HBox(10, send), result);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }
}
