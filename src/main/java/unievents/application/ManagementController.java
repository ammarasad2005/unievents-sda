package unievents.application;

import unievents.domain.Announcement;
import unievents.domain.AttendanceReport;
import unievents.domain.Category;
import unievents.domain.Club;
import unievents.domain.ClubOrganizer;
import unievents.domain.Event;
import unievents.domain.Notification;
import unievents.domain.Registration;
import unievents.infrastructure.persistence.DataHandler;
import unievents.shared.AttendanceRow;
import unievents.shared.AudienceCounts;
import unievents.shared.AudienceType;
import unievents.shared.EventDetails;
import unievents.shared.EventStatus;
import unievents.shared.EventSummary;
import unievents.shared.Participant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application controller for the organizer use cases (UC6–UC10).
 *
 * GRASP Controller + High Cohesion: everything about managing events lives
 * here; conflicts (UC12) and club creation (UC11) have their own controllers.
 */
public class ManagementController {

    private final DataHandler data;
    private final ClubOrganizer organizer;   // actor of UC6–UC10

    public ManagementController(ClubOrganizer organizer, DataHandler data) {
        this.organizer = organizer;
        this.data = data;
    }

    // ------------------------------------------------------------------
    // My Events (entry list for UC6–UC10)
    // ------------------------------------------------------------------

    /** Events across the organizer's club(s), newest first (SD-06/07). */
    public List<EventSummary> getMyEvents() {
        return organizer.getMyEvents().stream()
                .map(e -> new EventSummary(e.getEventId(), e.getTitle(),
                        e.getClub().getName(), e.getStartDateTime(), e.getVenue(),
                        e.getRemainingSeats(), e.getStatus()))
                .toList();
    }

    /** Management details of one event (SD-06/07 getEventDetails). */
    public EventDetails getEventDetails(int eventId) {
        Event e = data.findEventById(eventId);
        return new EventDetails(e.getEventId(), e.getTitle(), e.getDescription(),
                e.getStartDateTime(), e.getEndDateTime(), e.getVenue(),
                e.getCapacity(), e.getRemainingSeats(),
                e.getCategory().getCategoryId(), e.getCategory().getName(),
                e.getClub().getName(), e.getAverageRating());
    }

    // ------------------------------------------------------------------
    // UC6: Create / edit event
    // ------------------------------------------------------------------

    /**
     * Creates (eventId == null) or edits an event (SD-06 saveEvent).
     * Capacity below the registered count is rejected by the domain.
     */
    public Event saveEvent(Integer eventId, String title, String description,
                           LocalDateTime startDateTime, LocalDateTime endDateTime,
                           String venue, int capacity, int categoryId) {
        Category category = data.findCategoryById(categoryId);
        if (eventId == null) {
            Club club = organizer.getClub();
            if (club == null) {
                throw new IllegalStateException("You are not assigned to a club yet.");
            }
            Event event = club.createEvent(title, description, startDateTime, endDateTime,
                    venue, capacity, category);                  // Creator
            data.saveEvent(event);
            return event;
        }
        Event event = data.findEventById(eventId);
        event.updateDetails(title, description, startDateTime, endDateTime,
                venue, capacity, category);                       // Information Expert
        data.saveEvent(event);
        return event;
    }

    // ------------------------------------------------------------------
    // UC7: Change status
    // ------------------------------------------------------------------

    /**
     * Applies a status change with valid transitions only (SD-07).
     * Cancellation notifies the registered audience.
     */
    public EventStatus changeEventStatus(int eventId, EventStatus newStatus, String reason) {
        Event event = data.findEventById(eventId);
        EventStatus status = event.changeStatus(newStatus, reason);   // domain validates
        data.saveEvent(event);
        persistNotifications();
        return status;
    }

    // ------------------------------------------------------------------
    // UC8: Announcements
    // ------------------------------------------------------------------

    /** Audience sizes for the announcement screen (SD-08). */
    public AudienceCounts getAudienceCounts(int eventId) {
        return data.findEventById(eventId).getAudienceCounts();
    }

    /**
     * Sends an announcement to the chosen audience; every recipient gets an
     * in-app notification (SD-08).
     */
    public Announcement sendAnnouncement(int eventId, String subject, String message,
                                         AudienceType audience) {
        Event event = data.findEventById(eventId);
        Announcement announcement = organizer.createAnnouncement(event, subject,
                message, audience);                               // domain notifies recipients
        data.saveAnnouncement(announcement);
        // persist the in-app notifications created for every recipient
        List<Notification> produced = new java.util.ArrayList<>();
        for (Registration r : event.getRegistrations()) {
            produced.addAll(r.getStudent().takePendingNotifications());
        }
        if (!produced.isEmpty()) {
            data.saveNotifications(produced);
        }
        return announcement;
    }

    // ------------------------------------------------------------------
    // UC9: Attendance sheet + mark attendance
    // ------------------------------------------------------------------

    /** Sheet rows + the event's check-in code (SD-09 getAttendanceSheet). */
    public AttendanceSheet getAttendanceSheet(int eventId) {
        Event event = data.findEventById(eventId);
        return new AttendanceSheet(event.getCheckInCode(), event.getAttendanceSheet());
    }

    /**
     * Marks the selected registrations attended via MANUAL check-in (SD-09).
     *
     * @return number of registrations updated
     */
    public int recordAttendance(int eventId, List<Integer> registrationIds) {
        Event event = data.findEventById(eventId);
        List<Registration> updated = event.recordAttendance(registrationIds);
        try {
            data.beginTransaction();
            for (Registration r : updated) {
                data.saveRegistration(r);
                data.saveAttendance(r);
            }
            data.commit();
        } catch (RuntimeException e) {
            data.rollback();
            throw e;
        }
        return updated.size();
    }

    // ------------------------------------------------------------------
    // UC10: Attendance report
    // ------------------------------------------------------------------

    /**
     * Generates and persists the attendance report (SD-10).
     *
     * @return report + its participant lines
     */
    public ReportWithParticipants generateAttendanceReport(int eventId) {
        Event event = data.findEventById(eventId);
        AttendanceReport report = event.generateReport(organizer);   // Information Expert
        data.saveAttendanceReport(report);
        return new ReportWithParticipants(report, event.getParticipants());
    }

    // ------------------------------------------------------------------
    // Catalogue data for forms
    // ------------------------------------------------------------------

    public List<Category> getCategories() {
        return data.findAllCategories();
    }

    public List<Club> getClubs() {
        return data.findAllClubs();
    }

    // ------------------------------------------------------------------
    // DTOs for compound returns
    // ------------------------------------------------------------------

    /** Check-in code + sheet rows (SSD-09). */
    public record AttendanceSheet(String checkInCode, List<AttendanceRow> rows) {
    }

    /** Report + participants (SSD-10). */
    public record ReportWithParticipants(AttendanceReport report, List<Participant> participants) {
    }

    /** Drains and stores notifications produced by the last domain call. */
    private void persistNotifications() {
        List<Notification> pending = organizer.takePendingNotifications();
        if (!pending.isEmpty()) {
            data.saveNotifications(pending);
        }
    }
}
