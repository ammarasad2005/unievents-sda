package unievents.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;
import unievents.domain.Registration;
import unievents.shared.EventDetails;
import unievents.shared.RegistrationStatus;

import java.time.format.DateTimeFormatter;

/**
 * UC2 — Event Detail + Register. Opened from the browse list; the Register
 * action calls StudentController.registerForEvent (domain decides seat vs
 * waitlist) and reports the outcome clearly.
 */
public final class EventDetailScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private EventDetailScreen() {
    }

    /** Opens the detail view as a dialog (browse flow keeps its list state). */
    public static void open(StudentController controller, UiContext ctx, int eventId) {
        EventDetails d;
        try {
            d = controller.getEventDetails(eventId);
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load event", ex.getMessage());
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Event details");
        dialog.setHeaderText(d.title());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        int row = 0;
        grid.addRow(row++, new Label("Club:"), new Label(d.clubName()));
        grid.addRow(row++, new Label("Category:"), new Label(d.categoryName()));
        grid.addRow(row++, new Label("Starts:"), new Label(D.format(d.startDateTime())));
        grid.addRow(row++, new Label("Ends:"), new Label(D.format(d.endDateTime())));
        grid.addRow(row++, new Label("Venue:"), new Label(d.venue()));
        grid.addRow(row++, new Label("Capacity:"), new Label(String.valueOf(d.capacity())));
        grid.addRow(row++, new Label("Remaining seats:"), new Label(String.valueOf(d.remainingSeats())));
        grid.addRow(row++, new Label("Average rating:"),
                new Label(d.averageRating() == 0 ? "No reviews yet" : String.format("%.1f / 5", d.averageRating())));

        Label description = new Label(d.description() == null ? "" : d.description());
        description.setWrapText(true);
        description.setMaxWidth(420);

        Label registerInfo = new Label();
        registerInfo.setWrapText(true);

        Button register = new Button("Register");
        register.setDefaultButton(true);
        register.setOnAction(e -> {
            try {
                Registration r = controller.registerForEvent(eventId);
                if (r.getStatus() == RegistrationStatus.REGISTERED) {
                    registerInfo.getStyleClass().add("label-success");
                    registerInfo.setText("You are REGISTERED for this event. See My Registrations.");
                } else {
                    registerInfo.getStyleClass().add("label-success");
                    registerInfo.setText("The event is full — you are WAITLISTED at position "
                            + r.getWaitlistPosition() + ".");
                }
                register.setDisable(true);
            } catch (RuntimeException ex) {
                UniEventsApp.error("Registration failed", ex.getMessage());
            }
        });

        VBox content = new VBox(12, grid, description, register, registerInfo);
        content.setPadding(new Insets(6));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
