package unievents.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.domain.Category;
import unievents.shared.EventDetails;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * UC6 — Create / Edit Event. New events are created as DRAFT by the domain
 * (Club.createEvent). Capacity below the registered count is rejected by
 * Event.updateDetails with a clear message.
 */
public final class EventFormScreen {

    private EventFormScreen() {
    }

    /** eventId == null → create; otherwise edit (prefilled). */
    public static Node create(ManagementController controller, UiContext ctx, Integer eventId) {
        boolean editing = eventId != null;
        Label title = new Label(editing ? "Edit Event (UC6)" : "Create Event (UC6)");
        title.getStyleClass().add("screen-title");

        EventDetails loaded;
        if (editing) {
            EventDetails found = null;
            try {
                found = controller.getEventDetails(eventId);
            } catch (RuntimeException ex) {
                UniEventsApp.error("Could not load event", ex.getMessage());
            }
            loaded = found;
        } else {
            loaded = null;
        }
        final EventDetails existing = loaded;

        TextField titleField = new TextField(existing == null ? "" : existing.title());
        titleField.setPromptText("Event title");
        TextArea description = new TextArea(existing == null ? "" : existing.description());
        description.setPromptText("What is this event about?");
        description.setPrefRowCount(3);

        DatePicker startDate = new DatePicker(existing == null ? null : existing.startDateTime().toLocalDate());
        ComboBox<String> startTime = timeCombo(existing == null ? null : existing.startDateTime());
        DatePicker endDate = new DatePicker(existing == null ? null : existing.endDateTime().toLocalDate());
        ComboBox<String> endTime = timeCombo(existing == null ? null : existing.endDateTime());

        TextField venue = new TextField(existing == null ? "" : existing.venue());
        venue.setPromptText("e.g. Auditorium A");

        Spinner<Integer> capacity = new Spinner<>(1, 10000, existing == null ? 50 : existing.capacity());
        capacity.setEditable(true);
        capacity.setPrefWidth(110);

        ComboBox<Category> category = new ComboBox<>();
        try {
            category.setItems(javafx.collections.FXCollections
                    .observableArrayList(controller.getCategories()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load categories", ex.getMessage());
        }
        if (existing != null) {
            category.getItems().stream()
                    .filter(c -> c.getCategoryId() == existing.categoryId())
                    .findFirst()
                    .ifPresent(category::setValue);
        }
        category.setPromptText("Category");

        Label result = new Label();
        result.setWrapText(true);

        Button save = new Button(editing ? "Save changes" : "Create draft event");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            result.getStyleClass().clear();
            if (titleField.getText() == null || titleField.getText().isBlank()
                    || venue.getText() == null || venue.getText().isBlank()
                    || startDate.getValue() == null || startTime.getValue() == null
                    || endDate.getValue() == null || endTime.getValue() == null
                    || category.getValue() == null) {
                result.getStyleClass().add("label-error");
                result.setText("Title, venue, both date-times and category are required.");
                return;
            }
            Integer cap = readCapacity(capacity);
            if (cap == null) {
                result.getStyleClass().add("label-error");
                result.setText("Capacity must be a positive whole number.");
                return;
            }
            LocalDateTime start = startDate.getValue().atTime(LocalTime.parse(startTime.getValue()));
            LocalDateTime end = endDate.getValue().atTime(LocalTime.parse(endTime.getValue()));
            try {
                controller.saveEvent(eventId, titleField.getText(), description.getText(),
                        start, end, venue.getText(), cap, category.getValue().getCategoryId());
                result.getStyleClass().add("label-success");
                result.setText(editing
                        ? "Event updated successfully."
                        : "Draft event created. Publish it from Change Status (UC7) when ready.");
                if (!editing) {
                    titleField.clear();
                    description.clear();
                    venue.clear();
                }
            } catch (RuntimeException ex) {
                UniEventsApp.error("Save failed", ex.getMessage());
            }
        });

        Button back = new Button("Back to My Events");
        back.getStyleClass().add("button-secondary");
        back.setOnAction(e -> ctx.app().show(() -> MyEventsScreen.create(controller, ctx)));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Title:"), titleField);
        form.add(new Label("Description:"), 0, 1);
        form.add(description, 1, 1);
        form.addRow(2, new Label("Starts:"), new HBox(8, startDate, startTime));
        form.addRow(3, new Label("Ends:"), new HBox(8, endDate, endTime));
        form.addRow(4, new Label("Venue:"), venue);
        form.addRow(5, new Label("Capacity:"), capacity);
        form.addRow(6, new Label("Category:"), category);

        VBox card = new VBox(12, form, new HBox(10, save, back), result);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        return new VBox(10, title, card);
    }

    private static ComboBox<String> timeCombo(LocalDateTime time) {
        ComboBox<String> combo = new ComboBox<>();
        for (int h = 8; h <= 20; h++) {
            combo.getItems().add(String.format("%02d:00", h));
        }
        if (time != null) {
            combo.setValue(String.format("%02d:00", time.getHour()));
        }
        combo.setPromptText("time");
        return combo;
    }

    private static Integer readCapacity(Spinner<Integer> spinner) {
        try {
            int value = Integer.parseInt(spinner.getEditor().getText().trim());
            return value > 0 ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
