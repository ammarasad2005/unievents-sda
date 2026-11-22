package unievents.domain;

import unievents.shared.Participant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Summary of attendance for one event (SD-10).
 * Participants are derived from the event's registrations on demand.
 */
public class AttendanceReport {

    private Integer reportId;           // null until persisted
    private final ClubOrganizer organizer;
    private final Event event;
    private final LocalDateTime generatedAt;
    private final int registeredCount;
    private final int attendedCount;

    /** Domain constructor (created via Event.generateReport). */
    AttendanceReport(ClubOrganizer organizer, Event event, int registeredCount, int attendedCount) {
        this(organizer, event, registeredCount, attendedCount, LocalDateTime.now());
    }

    /** Full constructor; also used for rehydration by the persistence layer. */
    public AttendanceReport(ClubOrganizer organizer, Event event, int registeredCount,
                            int attendedCount, LocalDateTime generatedAt) {
        if (registeredCount < 0 || attendedCount < 0) {
            throw new IllegalArgumentException("Report counts cannot be negative.");
        }
        this.organizer = organizer;
        this.event = event;
        this.registeredCount = registeredCount;
        this.attendedCount = attendedCount;
        this.generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }

    public Integer getReportId() {
        return reportId;
    }

    public ClubOrganizer getOrganizer() {
        return organizer;
    }

    public Event getEvent() {
        return event;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public int getAttendedCount() {
        return attendedCount;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int reportId) {
        this.reportId = reportId;
    }

    /** Participant list of the reported event (SSD-10). */
    public List<Participant> getParticipants() {
        return event.getParticipants();
    }

    /** Attendance percentage for display. */
    public double getAttendanceRate() {
        return registeredCount == 0 ? 0.0 : (attendedCount * 100.0) / registeredCount;
    }
}
