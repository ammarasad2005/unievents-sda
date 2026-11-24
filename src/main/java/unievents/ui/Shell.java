package unievents.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * Shared layout for all role home screens: sidebar navigation + content area.
 * Screens register themselves through {@link #nav(String, Supplier)}.
 */
public final class Shell {

    private final BorderPane shell = new BorderPane();
    private final VBox navBox = new VBox(6);
    private final VBox contentBox = new VBox(12);
    private final Label heading = new Label();
    private final Label userLabel = new Label();

    private Shell(UiContext ctx) {
        heading.getStyleClass().add("app-title");

        userLabel.getStyleClass().add("subtitle");
        userLabel.setText(ctx.user().getFullName() + " · " + prettyRole(ctx.user().getRole()));

        Button logout = new Button("Sign out");
        logout.getStyleClass().add("button-secondary");
        logout.setOnAction(e -> ctx.app().showLogin());

        HBox top = new HBox(16, heading, userLabel, logout);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(userLabel, Priority.ALWAYS);
        top.setPadding(new Insets(14, 18, 14, 18));
        top.getStyleClass().add("card");

        navBox.setPadding(new Insets(14, 10, 14, 10));
        navBox.getStyleClass().add("card");
        navBox.setPrefWidth(230);

        contentBox.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f4f6fa; -fx-background-color: #f4f6fa;");

        shell.setTop(top);
        shell.setLeft(navBox);
        shell.setCenter(scroll);
    }

    public static Shell create(UiContext ctx, String title) {
        Shell shell = new Shell(ctx);
        shell.heading.setText(title);
        return shell;
    }

    /** Adds a sidebar entry that builds its target screen lazily. */
    public Shell nav(String label, Supplier<Node> screen) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> {
            try {
                contentBox.getChildren().setAll(screen.get());
            } catch (RuntimeException ex) {
                UniEventsApp.error("Could not load screen", ex.getMessage());
            }
        });
        navBox.getChildren().add(button);
        return this;
    }

    /** Replaces the sidebar entry points with fresh screens (after data changes). */
    public Shell show(Node initial) {
        contentBox.getChildren().setAll(initial);
        return this;
    }

    public Node node() {
        return shell;
    }

    public VBox content() {
        return contentBox;
    }

    private static String prettyRole(String role) {
        return switch (role) {
            case "STUDENT" -> "Student";
            case "CLUB_ORGANIZER" -> "Club Organizer";
            case "ADMIN" -> "Admin";
            default -> role;
        };
    }
}
