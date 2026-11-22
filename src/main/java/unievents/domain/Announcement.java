package unievents.domain;

import unievents.shared.AudienceType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An announcement sent by an organizer to an event audience.
 * GRASP: the announcement notifies its own recipients (SD-08 notifyRecipients).
 */
public class Announcement {

    private Integer announcementId;     // null until persisted
    private final ClubOrganizer sender;
    private final Event event;
    private final String subject;
    private final String message;
    private final AudienceType audience;
    private final LocalDateTime sentAt;

    /** Domain constructor (created via ClubOrganizer.createAnnouncement). */
    Announcement(Event event, ClubOrganizer sender, String subject, String message, AudienceType audience) {
        this(event, sender, subject, message, audience, LocalDateTime.now());
    }

    /** Full constructor; also used for rehydration by the persistence layer. */
    public Announcement(Event event, ClubOrganizer sender, String subject, String message,
                        AudienceType audience, LocalDateTime sentAt) {
        this.event = event;
        this.sender = sender;
        this.subject = subject;
        this.message = message;
        this.audience = audience;
        this.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
    }

    public Integer getAnnouncementId() {
        return announcementId;
    }

    public Event getEvent() {
        return event;
    }

    public ClubOrganizer getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public AudienceType getAudience() {
        return audience;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int announcementId) {
        this.announcementId = announcementId;
    }

    /**
     * Delivers an in-app notification to every recipient (SD-08).
     *
     * @return number of users notified
     */
    public int notifyRecipients(List<Student> recipients) {
        int count = 0;
        if (recipients != null) {
            for (Student student : recipients) {
                student.receiveNotification(new Notification(
                        "Announcement for \"" + event.getTitle() + "\": " + subject,
                        unievents.shared.NotificationType.ANNOUNCEMENT, this));
                count++;
            }
        }
        return count;
    }
}
