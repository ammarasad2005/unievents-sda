package unievents.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import unievents.application.Session;
import unievents.domain.User;
import unievents.infrastructure.persistence.DatabaseController;
import unievents.infrastructure.persistence.SupabaseHandler;

/**
 * JavaFX entry point. Wires the single DataHandler implementation and swaps
 * role-based home screens after login. Role routing happens in
 * {@link HomeRouter} through the polymorphic {@code getRole()} call; there
 * is no instanceof or type-switch in any business logic.
 */
public class UniEventsApp extends Application {

    private static volatile SupabaseHandler dataHandler;

    /** Lazy single DataHandler instance (Protected Variation: swap implementations here). */
    public static unievents.infrastructure.persistence.DataHandler data() {
        if (dataHandler == null) {
            synchronized (UniEventsApp.class) {
                if (dataHandler == null) {
                    dataHandler = new SupabaseHandler();
                }
            }
        }
        return dataHandler;
    }

    private Stage stage;
    BorderPane root;          // package-private: UI flow test reads it

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
        root.setCenter(HomeRouter.homeFor(ctx, user));
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
