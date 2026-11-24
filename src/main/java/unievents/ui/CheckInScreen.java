package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;

/**
 * UC4 — Check In at Event: the student enters the event id and the code
 * announced at the venue. Invalid codes and missing registrations produce
 * clear error messages (no crash).
 */
public final class CheckInScreen {

    private CheckInScreen() {
    }

    public static Node create(StudentController controller, UiContext ctx) {
        Label title = new Label("Check In at Event");
        title.getStyleClass().add("screen-title");

        TextField eventId = new TextField();
        eventId.setPromptText("e.g. 2");
        TextField code = new TextField();
        code.setPromptText("6-character code from the organizer, e.g. GIT99");

        Label result = new Label();
        result.setWrapText(true);

        Button submit = new Button("Check in");
        submit.setDefaultButton(true);
        submit.setOnAction(e -> {
            result.getStyleClass().clear();
            Integer id = parseId(eventId.getText());
            if (id == null) {
                result.getStyleClass().add("label-error");
                result.setText("Enter a numeric event id.");
                return;
            }
            try {
                controller.checkInToEvent(id, code.getText());
                result.getStyleClass().add("label-success");
                result.setText("Checked in — your attendance is recorded. Enjoy the event!");
                code.clear();
            } catch (RuntimeException ex) {
                result.getStyleClass().add("label-error");
                result.setText(ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Event id:"), eventId);
        form.addRow(1, new Label("Check-in code:"), code);

        Label hint = new Label("Tip: the demo seed publishes event 2 with code GIT99.");
        hint.getStyleClass().add("subtitle");
        hint.setWrapText(true);

        VBox card = new VBox(12, form, submit, result, hint);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }

    private static Integer parseId(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
