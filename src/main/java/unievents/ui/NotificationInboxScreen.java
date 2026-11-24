package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;
import unievents.domain.Notification;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Inbox of in-app notifications (registration, waitlist, promotion,
 * cancellation, announcements) for the logged-in student.
 */
public final class NotificationInboxScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private NotificationInboxScreen() {
    }

    public static Node create(StudentController controller) {
        Label title = new Label("Notifications");
        title.getStyleClass().add("screen-title");

        VBox list = new VBox(8);
        list.setPadding(new Insets(4));

        try {
            List<Notification> notifications = controller.getMyNotifications();
            if (notifications.isEmpty()) {
                Label empty = new Label("No notifications yet.");
                empty.getStyleClass().add("subtitle");
                list.getChildren().add(empty);
            } else {
                for (Notification n : notifications) {
                    VBox card = new VBox(4);
                    Label type = new Label(n.getType().name());
                    type.getStyleClass().add("subtitle");
                    Label message = new Label(n.getMessage());
                    message.setWrapText(true);
                    Label when = new Label(n.getCreatedAt() == null ? "" : D.format(n.getCreatedAt()));
                    when.getStyleClass().add("subtitle");
                    card.getChildren().addAll(type, message, when);
                    card.getStyleClass().add("card");
                    list.getChildren().add(card);
                }
            }
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load notifications", ex.getMessage());
        }

        return new VBox(10, title, list);
    }
}
