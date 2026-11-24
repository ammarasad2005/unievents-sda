package unievents.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.ManagementController;
import unievents.shared.EventSummary;

import java.time.format.DateTimeFormatter;

/**
 * My Events — the organizer's entry list for UC6–UC10. Double-click (or the
 * Edit button) opens the create/edit form prefilled.
 */
public final class MyEventsScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private MyEventsScreen() {
    }

    public static Node create(ManagementController controller, UiContext ctx) {
        Label title = new Label("My Events");
        title.getStyleClass().add("screen-title");

        TableView<EventSummary> table = new TableView<>();
        table.setPlaceholder(new Label("No events in your club yet — create one."));

        TableColumn<EventSummary, String> colTitle = new TableColumn<>("Event");
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().title()));
        colTitle.setPrefWidth(240);

        TableColumn<EventSummary, String> colStart = new TableColumn<>("Starts");
        colStart.setCellValueFactory(c -> new SimpleObjectProperty<>(
                c.getValue().startDateTime() == null ? "" : D.format(c.getValue().startDateTime())));
        colStart.setPrefWidth(150);

        TableColumn<EventSummary, String> colVenue = new TableColumn<>("Venue");
        colVenue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().venue()));
        colVenue.setPrefWidth(140);

        TableColumn<EventSummary, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
        colStatus.setPrefWidth(110);

        TableColumn<EventSummary, Number> colSeats = new TableColumn<>("Seats left");
        colSeats.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(
                c.getValue().remainingSeats()));
        colSeats.setPrefWidth(100);

        table.getColumns().addAll(colTitle, colStart, colVenue, colStatus, colSeats);
        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<EventSummary>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    ctx.app().show(() -> EventFormScreen.create(controller, ctx, row.getItem().eventId()));
                }
            });
            return row;
        });

        Button edit = new Button("Edit selected (UC6)");
        edit.setDisable(true);
        edit.setOnAction(e -> {
            EventSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ctx.app().show(() -> EventFormScreen.create(controller, ctx, selected.eventId()));
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> edit.setDisable(nv == null));

        Button create = new Button("Create new event (UC6)");
        create.setOnAction(e -> ctx.app().show(() -> EventFormScreen.create(controller, ctx, null)));

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-secondary");
        refresh.setOnAction(e -> reload(table, controller));

        VBox box = new VBox(10, title, table, new HBox(10, create, edit, refresh));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(420);
        box.setPadding(new Insets(4));

        reload(table, controller);
        return box;
    }

    static void reload(TableView<EventSummary> table, ManagementController controller) {
        try {
            table.setItems(FXCollections.observableArrayList(controller.getMyEvents()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
    }
}
