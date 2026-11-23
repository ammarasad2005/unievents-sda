package unievents.application;

import unievents.domain.User;
import unievents.infrastructure.persistence.DataHandler;

/**
 * Session holder for the logged-in user (thin precondition only —
 * Login/Register are NOT primary use cases of this system).
 */
public final class Session {

    private static User currentUser;

    private Session() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void clear() {
        currentUser = null;
    }

    /** Validates credentials against the store; returns the user or null. */
    public static User login(String email, String password, DataHandler data) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        User user = data.findUserByEmail(email.trim()).orElse(null);
        if (user != null && user.isActive() && user.matchesPassword(password)) {
            currentUser = user;
            return user;
        }
        return null;
    }

    public static void logout() {
        clear();
    }
}
