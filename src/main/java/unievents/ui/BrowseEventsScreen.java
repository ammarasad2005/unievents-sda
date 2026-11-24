package unievents.ui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import unievents.application.StudentController;
import unievents.domain.Club;
import unievents.shared.EventSummary;

import java.time.format.DateTimeFormatter;

/**
 * UC1 — Browse Events: published events, optional filters, open details.
 * All data access goes through StudentController (never the DAO directly).
 */
public final class BrowseEventsScreen {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private BrowseEventsScreen() {
    }

    public static Node create(StudentController controller, UiContext ctx) {
        Label title = new Label("Browse Events");
        title.getStyleClass().add("screen-title");

        // ---- filter bar -------------------------------------------------
        ComboBox<NamedId> category = new ComboBox<>();
        ComboBox<NamedId> club = new ComboBox<>();
        try {
            category.setItems(FXCollections.observableArrayList(
                    controller.getCategories().stream().map(c -> new NamedId(c.getCategoryId(), c.getName())).toList()));
            club.setItems(FXCollections.observableArrayList(
                    controller.getClubs().stream().map(c -> new NamedId(c.getClubId(), c.getName())).toList()));
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load filters", ex.getMessage());
        }
        category.setPromptText("All categories");
        club.setPromptText("All clubs");
        DatePicker date = new DatePicker();
        date.setPromptText("Any date");
        date.setEditable(false);

        Button apply = new Button("Apply filters");
        Button reset = new Button("Reset");
        apply.getStyleClass().add("button-secondary");
        reset.getStyleClass().add("button-secondary");

        HBox filters = new HBox(10, new Label("Category:"), category,
                new Label("Club:"), club, new Label("Date:"), date, apply, reset);
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        filters.getStyleClass().add("card");

        // ---- table ------------------------------------------------------
        TableView<EventSummary> table = new TableView<>();
        table.setPlaceholder(new Label("No published events match."));
        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<EventSummary>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    EventDetailScreen.open(controller, ctx, row.getItem().eventId());
                }
            });
            return row;
        });

        TableColumn<EventSummary, String> colTitle = new TableColumn<>("Event");
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().title()));
        colTitle.setPrefWidth(240);

        TableColumn<EventSummary, String> colClub = new TableColumn<>("Club");
        colClub.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clubName()));
        colClub.setPrefWidth(150);

        TableColumn<EventSummary, String> colStart = new TableColumn<>("Starts");
        colStart.setCellValueFactory(c -> new SimpleObjectProperty<>(
                c.getValue().startDateTime() == null ? "" : D.format(c.getValue().startDateTime())));
        colStart.setPrefWidth(150);

        TableColumn<EventSummary, String> colVenue = new TableColumn<>("Venue");
        colVenue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().venue()));
        colVenue.setPrefWidth(140);

        TableColumn<EventSummary, Number> colSeats = new TableColumn<>("Remaining seats");
        colSeats.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().remainingSeats()));
        colSeats.setPrefWidth(130);

        table.getColumns().addAll(colTitle, colClub, colStart, colVenue, colSeats);

        Label status = new Label();
        status.getStyleClass().add("subtitle");

        Button open = new Button("View details");
        open.setDisable(true);
        open.setOnAction(e -> {
            EventSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                EventDetailScreen.open(controller, ctx, selected.eventId());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> open.setDisable(n == null));

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-secondary");
        refresh.setOnAction(e -> reload(table, controller, status));

        VBox box = new VBox(10, title, filters, table, new HBox(10, open, refresh, status));
        box.setPadding(new Insets(4));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(420);

        apply.setOnAction(e -> {
            try {
                table.setItems(FXCollections.observableArrayList(controller.filterEvents(
                        category.getValue() == null ? null : category.getValue().id(),
                        club.getValue() == null ? null : club.getValue().id(),
                        date.getValue() == null ? null : date.getValue().atStartOfDay())));
                status.setText(table.getItems().size() + " event(s) match the filters.");
            } catch (RuntimeException ex) {
                UniEventsApp.error("Filter failed", ex.getMessage());
            }
        });
        reset.setOnAction(e -> {
            category.setValue(null);
            club.setValue(null);
            date.setValue(null);
            reload(table, controller, status);
        });

        reload(table, controller, status);
        return box;
    }

    private static void reload(TableView<EventSummary> table, StudentController controller,
                               Label status) {
        try {
            table.setItems(FXCollections.observableArrayList(controller.getPublishedEvents()));
            status.setText(table.getItems().size() + " published event(s).");
        } catch (RuntimeException ex) {
            UniEventsApp.error("Could not load events", ex.getMessage());
        }
    }

    // ---- combo option ---------------------------------------------------

    /** Simple id + display-name pair for filter combos. */
    record NamedId(Integer id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
