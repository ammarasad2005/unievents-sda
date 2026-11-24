package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;
import unievents.shared.EventSummary;

/**
 * UC5 — Rate and Review Event: pick an event, check eligibility, submit
 * rating + comment. The domain allows reviews only for attendees without an
 * existing review.
 */
public final class ReviewEventScreen {

    private ReviewEventScreen() {
    }

    public static Node create(StudentController controller, UiContext ctx) {
        Label title = new Label("Rate and Review Event");
        title.getStyleClass().add("screen-title");

        ComboBox<EventSummary> event = new ComboBox<>();
        try {
            event.setItems(javafx.collections.FXCollections
                    .observableArrayList(controller.getPublishedEvents()));
            // completed events reviewed earlier also matter: include from my registrations
            controller.getMyRegistrations().forEach(r -> {
                boolean already = event.getItems().stream()
                        .anyMatch(s -> s.eventId() == r.eventId());
                if (!already && r.status() == unievents.shared.RegistrationStatus.ATTENDED) {
                    event.getItems().add(new EventSummary(r.eventId(), r.eventTitle(),
                            "—", r.startDateTime(), r.venue(), 0, unievents.shared.EventStatus.COMPLETED));
                }
            });
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
        event.setPromptText("Select an event");
        event.setPrefWidth(320);

        Label eligibility = new Label("Pick an event to check eligibility.");
        eligibility.setWrapText(true);

        Button check = new Button("Check eligibility");
        check.getStyleClass().add("button-secondary");
        check.setOnAction(e -> {
            EventSummary selected = event.getValue();
            if (selected == null) {
                eligibility.setText("Select an event first.");
                return;
            }
            try {
                boolean eligible = controller.checkReviewEligibility(selected.eventId());
                eligibility.getStyleClass().clear();
                if (eligible) {
                    eligibility.getStyleClass().add("label-success");
                    eligibility.setText("Eligible: you attended and have not reviewed this event yet.");
                } else {
                    eligibility.getStyleClass().add("label-error");
                    eligibility.setText("Not eligible: reviews require attendance and only one review per event.");
                }
            } catch (RuntimeException ex) {
                UniEventsApp.error("Eligibility check failed", ex.getMessage());
            }
        });

        Slider rating = new Slider(1, 5, 5);
        rating.setMajorTickUnit(1);
        rating.setMinorTickCount(0);
        rating.setSnapToTicks(true);
        rating.setShowTickLabels(true);
        rating.setShowTickMarks(true);
        rating.setPrefWidth(240);

        TextArea comment = new TextArea();
        comment.setPromptText("Optional comment about the event");
        comment.setPrefRowCount(3);

        Label result = new Label();
        result.setWrapText(true);

        Button submit = new Button("Submit review");
        submit.setDefaultButton(true);
        submit.setOnAction(e -> {
            EventSummary selected = event.getValue();
            if (selected == null) {
                result.getStyleClass().clear();
                result.getStyleClass().add("label-error");
                result.setText("Select an event first.");
                return;
            }
            try {
                controller.submitReview(selected.eventId(), (int) rating.getValue(), comment.getText());
                result.getStyleClass().clear();
                result.getStyleClass().add("label-success");
                result.setText("Review saved. New average rating: "
                        + String.format("%.1f / 5", controller.getAverageRating(selected.eventId())));
                comment.clear();
            } catch (RuntimeException ex) {
                UniEventsApp.error("Review failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Event:"), event, check);
        form.addRow(1, new Label("Rating (1–5):"), rating);
        form.add(new Label("Comment:"), 0, 2);
        form.add(comment, 1, 2);

        VBox card = new VBox(12, eligibility, form, submit, result);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }
}
