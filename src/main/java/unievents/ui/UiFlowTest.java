package unievents.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import unievents.application.Session;
import unievents.domain.User;

/**
 * Headless UI integration test: logs in as each role and drives the real
 * screens against the database, the way a demonstrator would click them.
 * Requires Monocle (-Dglass.platform=Monocle -Dmonocle.platform=Headless).
 */
public class UiFlowTest extends UniEventsApp {

    private int passed;
    private int failed;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        super.start(stage);

        try {
            studentFlow();
            organizerFlow();
            adminFlow();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("unexpected exception: " + t);
        }
        System.out.println("UI-FLOW RESULT: passed=" + passed + " failed=" + failed);
        Platform.exit();
        System.exit(failed == 0 ? 0 : 3);
    }

    // ------------------------------------------------------------------

    private void studentFlow() {
        User user = Session.login("ayna@nu.edu.pk", "unievents", data());
        check("student login", user != null);
        if (user == null) {
            return;
        }
        showHome(user);
        check("student home built", centerIs(BorderPane.class));

        // BrowseEventsScreen: table + filters
        Node browse = BrowseEventsScreen.create(new unievents.application.StudentController(
                (unievents.domain.Student) user, data()), new UiContext(this, user));
        check("browse screen builds", browse.lookupAll(".table-view") instanceof TableView
                || findTable(browse) != null);
        TableView<?> table = findTable(browse);
        check("browse lists published events", table != null && table.getItems().size() == 2);
    }

    private void organizerFlow() {
        User user = Session.login("hamza@nu.edu.pk", "unievents", data());
        check("organizer login", user != null);
        if (user == null) {
            return;
        }
        showHome(user);
        check("organizer home built", centerIs(BorderPane.class));

        var controller = new unievents.application.ManagementController(
                (unievents.domain.ClubOrganizer) user, data());
        Node myEvents = MyEventsScreen.create(controller, new UiContext(this, user));
        TableView<?> table = findTable(myEvents);
        check("my events lists 4 events", table != null && table.getItems().size() == 4);

        Node status = EventStatusScreen.create(controller, new UiContext(this, user));
        check("status screen builds", status != null);

        Node attendance = AttendanceScreen.create(controller, new UiContext(this, user));
        check("attendance screen builds", attendance != null);

        Node report = AttendanceReportScreen.create(controller, new UiContext(this, user));
        check("report screen builds", report != null);
    }

    private void adminFlow() {
        User user = Session.login("sara@nu.edu.pk", "unievents", data());
        check("admin login", user != null);
        if (user == null) {
            return;
        }
        showHome(user);
        check("admin home built", centerIs(BorderPane.class));

        var clubController = new unievents.application.ClubController(
                (unievents.domain.Admin) user, data());
        Node createClub = CreateClubScreen.create(clubController, new UiContext(this, user));
        check("create club screen builds", createClub != null);

        var conflictController = new unievents.application.ConflictController(
                (unievents.domain.Admin) user, data());
        Node conflicts = ConflictScreen.create(conflictController, new UiContext(this, user));
        TableView<?> table = findTable(conflicts);
        check("conflict screen lists open conflicts", table != null && table.getItems().size() >= 1);
    }

    // ------------------------------------------------------------------

    private boolean centerIs(Class<?> type) {
        Node center = root.getCenter();
        return center != null && type.isInstance(center);
    }

    @SuppressWarnings("unchecked")
    private static TableView<?> findTable(Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof TableView<?> tv) {
            return tv;
        }
        return (TableView<?>) node.lookupAll(".table-view").stream().findFirst().orElse(null);
    }

    private void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
        }
    }

    private void fail(String label) {
        check(label, false);
    }
}
