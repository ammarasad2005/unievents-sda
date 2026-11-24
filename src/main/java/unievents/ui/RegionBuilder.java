package unievents.ui;

import javafx.scene.Node;

/** Screens implement this so {@link UniEventsApp#show} can swap them in. */
@FunctionalInterface
public interface RegionBuilder {
    Node build();
}
