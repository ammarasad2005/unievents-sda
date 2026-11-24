package unievents.ui;

import unievents.application.StudentController;
import unievents.domain.Student;
import unievents.infrastructure.persistence.DataHandler;

/**
 * Entry point for the student role: builds the navigation for UC1–UC5 and an
 * inbox. The controller instance is shared by all student screens.
 */
public final class StudentHome {

    private StudentHome() {
    }

    public static javafx.scene.Node create(UiContext ctx, Student student) {
        StudentController controller =
                new StudentController(student, (DataHandler) UniEventsApp.DATA);

        Shell shell = Shell.create(ctx, "UniEvents · Student");
        shell.nav("Browse Events (UC1)", () -> BrowseEventsScreen.create(controller, ctx));
        shell.nav("My Registrations (UC3)", () -> MyRegistrationsScreen.create(controller, ctx));
        shell.nav("Check In (UC4)", () -> CheckInScreen.create(controller, ctx));
        shell.nav("Review Event (UC5)", () -> ReviewEventScreen.create(controller, ctx));
        shell.nav("Notifications", () -> NotificationInboxScreen.create(controller));
        return shell.show(BrowseEventsScreen.create(controller, ctx)).node();
    }
}
