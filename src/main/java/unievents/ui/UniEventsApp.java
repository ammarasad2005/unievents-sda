package unievents.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import unievents.application.Session;
import unievents.domain.Admin;
import unievents.domain.ClubOrganizer;
import unievents.domain.Student;
import unievents.domain.User;
import unievents.infrastructure.persistence.DatabaseController;
import unievents.infrastructure.persistence.SupabaseHandler;

/**
 * JavaFX entry point. Wires the single DataHandler implementation and swaps
 * role-based home screens after login (polymorphic dispatch on User — no
 * instanceof anywhere in the navigation).
 */
public class UniEventsApp extends Application {

    public static final SupabaseHandler DATA = new SupabaseHandler();

    private Stage stage;
    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        root = new BorderPane();
        Scene scene = new Scene(root, 1024, 700);
        scene.getStylesheets().add(getClass().getResource("/unievents.css").toExternalForm());
        stage.setTitle("UniEvents — FAST-NUCES");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> DatabaseController.getInstance().closeConnection());
        showLogin();
        stage.show();
    }

    public void showLogin() {
        Session.logout();
        root.setCenter(LoginScreen.create(this));
    }

    /** Navigates to the role-based home after login. */
    public void showHome(User user) {
        UiContext ctx = new UiContext(this, user);
        root.setCenter(user instanceof Student s ? StudentHome.create(ctx, s)
                : user instanceof ClubOrganizer o ? OrganizerHome.create(ctx, o)
                : AdminHome.create(ctx, (Admin) user));
    }

    public void show(RegionBuilder builder) {
        root.setCenter(builder.build());
    }

    public Stage getStage() {
        return stage;
    }

    /** Error dialog used by every screen (SQLException never leaks raw). */
    public static void error(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Unexpected error." : message);
        alert.showAndWait();
    }

    /** Info dialog. */
    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
