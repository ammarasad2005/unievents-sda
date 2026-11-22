package unievents.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base of every person in the system (Abstraction + polymorphism:
 * the app works through {@code User} references and calls {@link #getRole()}).
 *
 * GRASP: knows its own identity data; notifications addressed to a user are
 * collected here so the application layer can persist them after a use case.
 */
public abstract class User {

    private final int userId;
    private String fullName;
    private String email;
    private String passwordHash;
    private boolean active;

    /** Notifications produced by domain operations, drained by the application layer. */
    private final List<Notification> pendingNotifications = new ArrayList<>();

    protected User(int userId, String fullName, String email, String passwordHash, boolean active) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    /** Role name ("STUDENT", "CLUB_ORGANIZER", "ADMIN") — polymorphic dispatch. */
    public abstract String getRole();

    /** Thin auth check (session precondition only, not a graded use case). */
    public boolean matchesPassword(String rawPassword) {
        return passwordHash != null && passwordHash.equals(unievents.shared.PasswordHasher.sha256(rawPassword));
    }

    /** Domain association "User receives Notification" (D4/D5). */
    public void receiveNotification(Notification notification) {
        pendingNotifications.add(notification);
    }

    /** Returns and clears notifications created by the last domain operation (persistence hook). */
    public List<Notification> takePendingNotifications() {
        List<Notification> drained = new ArrayList<>(pendingNotifications);
        pendingNotifications.clear();
        return drained;
    }

    @Override
    public String toString() {
        return fullName + " (" + getRole() + " #" + userId + ")";
    }
}
