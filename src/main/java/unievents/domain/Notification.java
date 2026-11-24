package unievents.domain;

import unievents.shared.NotificationType;

import java.time.LocalDateTime;

/**
 * In-app notification received by a user.
 * Polymorphism demo point: notifications are received through the
 * {@code User} supertype ({@code User.receiveNotification}) with no
 * instanceof or role switch anywhere.
 */
public class Notification {

    private Integer notificationId;     // null until persisted
    private User recipient;
    private String message;
    private NotificationType type;
    private LocalDateTime createdAt;
    private boolean read;
    private Announcement announcement;  // link when type == ANNOUNCEMENT

    /** Domain constructor (recipient bound on receiveNotification). */
    public Notification(String message, NotificationType type) {
        this(null, null, message, type, null, false, null);
    }

    /** Domain constructor with announcement link. */
    public Notification(String message, NotificationType type, Announcement announcement) {
        this(null, null, message, type, null, false, announcement);
    }

    /** Full constructor; also used for rehydration by the persistence layer. */
    public Notification(Integer notificationId, User recipient, String message, NotificationType type,
                        LocalDateTime createdAt, boolean read, Announcement announcement) {
        this.notificationId = notificationId;
        this.recipient = recipient;
        this.message = message;
        this.type = type;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.read = read;
        this.announcement = announcement;
    }

    /** Binds the receiving user at delivery time (package-private). */
    void bindRecipient(User user) {
        this.recipient = user;
    }

    public Integer getNotificationId() {
        return notificationId;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getMessage() {
        return message;
    }

    public NotificationType getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public Announcement getAnnouncement() {
        return announcement;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int notificationId) {
        this.notificationId = notificationId;
    }
}
