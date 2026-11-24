package unievents.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Headless smoke test: verifies the JavaFX toolkit starts, the login screen
 * builds, and the app exits cleanly. Run with Monocle (-Dglass.platform=Monocle).
 */
public class UiSmokeTest extends UniEventsApp {

    @Override
    public void start(Stage stage) {
        super.start(stage);
        Node center = stage.getScene().getRoot() instanceof javafx.scene.layout.BorderPane bp
                ? bp.getCenter() : null;
        if (center == null) {
            System.out.println("SMOKE FAIL: no login screen");
            Platform.exit();
            System.exit(2);
        }
        System.out.println("SMOKE PASS: toolkit up, login screen built: " + center.getClass().getSimpleName());
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
