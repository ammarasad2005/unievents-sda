package unievents.domain;

import unievents.shared.NotificationType;

import java.time.LocalDateTime;

/**
 * In-app notification received by a user.
 * Polymorphism demo point: formatting is derived from the enum, users are
 * handled through the {@code User} supertype in {@code receiveNotification}.
 */
public class Notification {

    private Integer notificationId;     // null until persisted
    private final User recipient;
    private final String message;
    private final NotificationType type;
    private final LocalDateTime createdAt;
    private final boolean read;         // fresh notifications are unread
    private final Announcement announcement;   // link when type == ANNOUNCEMENT

    /** Domain constructor (recipient bound later at persistence time). */
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
