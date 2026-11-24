package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ClubController;
import unievents.domain.ClubOrganizer;

/**
 * UC11 — Create Club: name + description + optional president. The president
 * becomes the club's first organizer (Admin.createClub in the domain).
 */
public final class CreateClubScreen {

    private CreateClubScreen() {
    }

    public static Node create(ClubController controller, UiContext ctx) {
        Label title = new Label("Create Club (UC11)");
        title.getStyleClass().add("screen-title");

        TextField name = new TextField();
        name.setPromptText("e.g. Robotics Society");
        TextArea description = new TextArea();
        description.setPromptText("What does the club do?");
        description.setPrefRowCount(3);

        ComboBox<OrganizerOption> president = new ComboBox<>();
        try {
            president.setItems(javafx.collections.FXCollections.observableArrayList(
                    controller.getOrganizers().stream().map(OrganizerOption::new).toList()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load organizers", ex.getMessage());
        }
        president.setPromptText("No president (optional)");
        president.setPrefWidth(280);

        Label result = new Label();
        result.setWrapText(true);

        Button create = new Button("Create club");
        create.setDefaultButton(true);
        create.setOnAction(e -> {
            result.getStyleClass().clear();
            if (name.getText() == null || name.getText().isBlank()) {
                result.getStyleClass().add("label-error");
                result.setText("Club name is required.");
                return;
            }
            try {
                var club = controller.createClub(name.getText(), description.getText(),
                        president.getValue() == null ? null : president.getValue().id());
                result.getStyleClass().add("label-success");
                result.setText("Club \"" + club.getName() + "\" created (id " + club.getClubId()
                        + (president.getValue() == null ? ", no president assigned)."
                                : ", " + president.getValue().name() + " assigned as president)."));
                name.clear();
                description.clear();
                president.setValue(null);
            } catch (RuntimeException ex) {
                UniEventsApp.error("Create club failed", ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Club name:"), name);
        form.add(new Label("Description:"), 0, 1);
        form.add(description, 1, 1);
        form.addRow(2, new Label("President:"), president);

        VBox card = new VBox(12, form, new HBox(10, create), result);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }

    private record OrganizerOption(int id, String name, String detail) {
        OrganizerOption(ClubOrganizer o) {
            this(o.getUserId(), o.getFullName(), o.getEmail());
        }

        @Override
        public String toString() {
            return name + " (" + detail + ")";
        }
    }
}
