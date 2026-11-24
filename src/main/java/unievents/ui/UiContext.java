package unievents.ui;

import unievents.domain.User;

/**
 * Passed to every screen: the app navigation handle and the logged-in user.
 * Screens use it to build their controllers and navigate.
 */
public record UiContext(UniEventsApp app, User user) {
}
