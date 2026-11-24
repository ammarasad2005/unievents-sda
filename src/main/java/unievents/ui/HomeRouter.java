package unievents.ui;

import javafx.scene.Node;
import unievents.application.ConflictController;
import unievents.application.ManagementController;
import unievents.application.StudentController;
import unievents.domain.Admin;
import unievents.domain.ClubOrganizer;
import unievents.domain.Student;

/**
 * Role-based home routing (UI layer only).
 *
 * The dispatch key is {@code getRole()} — an overridden method on the User
 * hierarchy, i.e. genuine polymorphic dispatch through a supertype
 * reference. Business logic never branches on role or concrete type; only
 * this navigation helper does, to pick the entry screen.
 */
public final class HomeRouter {

    private HomeRouter() {
    }

    public static Node homeFor(UiContext ctx, unievents.domain.User user) {
        return switch (user.getRole()) {
            case "STUDENT" -> StudentHome.create(ctx, (Student) user);
            case "CLUB_ORGANIZER" -> OrganizerHome.create(ctx, (ClubOrganizer) user);
            case "ADMIN" -> AdminHome.create(ctx, (Admin) user);
            default -> throw new IllegalStateException("Unknown role: " + user.getRole());
        };
    }

    // ---- controllers per role (shared by the screens of that role) ------

    static StudentController studentController(Student student) {
        return new StudentController(student, UniEventsApp.data());
    }

    static ManagementController managementController(ClubOrganizer organizer) {
        return new ManagementController(organizer, UniEventsApp.data());
    }

    static ConflictController conflictController(Admin admin) {
        return new ConflictController(admin, UniEventsApp.data());
    }
}
