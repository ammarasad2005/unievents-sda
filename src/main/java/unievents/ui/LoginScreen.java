package unievents.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import unievents.application.Session;
import unievents.domain.User;

/**
 * Thin login screen (session precondition only — not a graded use case).
 * Calls Session.login which goes through the DataHandler.
 */
public final class LoginScreen {

    private LoginScreen() {
    }

    public static Region create(UniEventsApp app) {
        Label title = new Label("UniEvents");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("University Event & Club Management — FAST-NUCES");
        subtitle.getStyleClass().add("subtitle");

        TextField email = new TextField();
        email.setPromptText("email@nu.edu.pk");
        PasswordField password = new PasswordField();
        password.setPromptText("password");

        Label error = new Label();
        error.getStyleClass().add("label-error");
        error.setWrapText(true);

        Button login = new Button("Sign in");
        login.setDefaultButton(true);
        login.setOnAction(e -> {
            error.setText("");
            try {
                User user = Session.login(email.getText(), password.getText(), UniEventsApp.DATA);
                if (user == null) {
                    error.setText("Invalid email or password.");
                } else {
                    app.showHome(user);
                }
            } catch (RuntimeException ex) {
                UniEventsApp.error("Connection problem", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Email:"), email);
        form.addRow(1, new Label("Password:"), password);
        GridPane.setHgrow(email, Priority.ALWAYS);

        HBox buttons = new HBox(10, login);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(14, title, subtitle, form, error, buttons);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));
        card.setMaxWidth(420);
        card.setFillWidth(true);

        Label hint = new Label("Demo accounts (password: unievents) — ayna@nu.edu.pk (student), "
                + "hamza@nu.edu.pk (organizer), sara@nu.edu.pk (admin)");
        hint.getStyleClass().add("subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(420);
        hint.setAlignment(Pos.CENTER);

        VBox box = new VBox(16, card, hint);
        box.setAlignment(Pos.CENTER);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }
}
