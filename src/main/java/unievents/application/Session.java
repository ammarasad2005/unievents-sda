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

    /**
     * Validates credentials against the store and rehydrates the full
     * role-specific object graph (student registrations / organizer clubs
     * and events), then returns the logged-in user.
     *
     * @return the user, or null when credentials are wrong
     */
    public static User login(String email, String password, DataHandler data) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        User user = data.findUserByEmail(email.trim()).orElse(null);
        if (user != null && user.isActive() && user.matchesPassword(password)) {
            user = rehydrate(user, data);
            currentUser = user;
            return user;
        }
        return null;
    }

    /**
     * Rebuilds the user through the role-specific finder so the object graph
     * (registrations, clubs, events) is loaded. Routing uses the polymorphic
     * {@code getRole()} — no instanceof.
     */
    private static User rehydrate(User user, DataHandler data) {
        switch (user.getRole()) {
            case "STUDENT" -> {
                return data.findStudentById(user.getUserId())
                        .orElseGet(() -> (unievents.domain.Student) user);
            }
            case "CLUB_ORGANIZER" -> {
                return data.findOrganizerById(user.getUserId())
                        .orElseGet(() -> (unievents.domain.ClubOrganizer) user);
            }
            case "ADMIN" -> {
                return data.findAdminById(user.getUserId())
                        .orElseGet(() -> (unievents.domain.Admin) user);
            }
            default -> {
                return user;
            }
        }
    }

    public static void logout() {
        clear();
    }
}
