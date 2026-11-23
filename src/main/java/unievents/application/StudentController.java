package unievents.application;

import unievents.domain.Attendance;
import unievents.domain.Event;
import unievents.domain.Notification;
import unievents.domain.Registration;
import unievents.domain.Review;
import unievents.domain.Student;
import unievents.infrastructure.persistence.DataHandler;
import unievents.shared.EventDetails;
import unievents.shared.EventSummary;
import unievents.shared.RegistrationStatus;
import unievents.shared.RegistrationSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application controller for the five student use cases (UC1–UC5).
 *
 * GRASP Controller: the UI calls only these methods; orchestration only —
 * every business rule is executed by the domain (Event, Registration...).
 */
public class StudentController {

    private final DataHandler data;
    private final Student student;      // actor of UC1–UC5

    public StudentController(Student student, DataHandler data) {
        this.student = student;
        this.data = data;
    }

    // ------------------------------------------------------------------
    // UC1: Browse Events
    // ------------------------------------------------------------------

    /** Published events as list rows (SD-01). */
    public List<EventSummary> getPublishedEvents() {
        return toSummaries(data.findPublishedEvents());
    }

    /** Optional filters: category, club, exact date (SD-01). */
    public List<EventSummary> filterEvents(Integer categoryId, Integer clubId, LocalDateTime date) {
        return toSummaries(data.findEventsByFilters(categoryId, clubId, date));
    }

    /** Full details of one event (SD-01). */
    public EventDetails getEventDetails(int eventId) {
        Event event = data.findEventById(eventId);
        return toDetails(event);
    }

    // ------------------------------------------------------------------
    // UC2: Register for Event
    // ------------------------------------------------------------------

    /**
     * Registers the logged-in student (SD-02). Domain decides seat vs waitlist.
     *
     * @throws IllegalStateException on domain rejection (full duplicate, closed...)
     */
    public Registration registerForEvent(int eventId) {
        Event event = data.findEventById(eventId);
        Registration registration = event.register(student);       // Information Expert
        data.saveRegistration(registration);
        persistNotifications();
        return registration;
    }

    // ------------------------------------------------------------------
    // UC3: My Registrations + cancel
    // ------------------------------------------------------------------

    /** The student's own registrations as list rows (SD-03). */
    public List<RegistrationSummary> getMyRegistrations() {
        data.loadRegistrations(student);
        return student.getRegistrations().stream()
                .map(r -> new RegistrationSummary(
                        r.getRegistrationId() == null ? 0 : r.getRegistrationId(),
                        r.getEvent().getEventId(),
                        r.getEvent().getTitle(),
                        r.getEvent().getStartDateTime(),
                        r.getEvent().getVenue(),
                        r.getStatus(),
                        r.getStatus() == RegistrationStatus.WAITLISTED ? r.getWaitlistPosition() : null))
                .toList();
    }

    /**
     * Cancels one registration; the domain promotes the next waitlisted
     * student when a seat is freed (SD-03).
     */
    public RegistrationStatus cancelRegistration(int registrationId) {
        data.loadRegistrations(student);
        Registration registration = student.findRegistration(registrationId);
        if (registration == null) {
            throw new IllegalStateException("Registration " + registrationId + " not found.");
        }
        RegistrationStatus status = registration.cancel();         // may promote
        try {
            data.beginTransaction();
            data.saveRegistration(registration);
            Registration promoted = registration.getEvent().promoteNextWaitlisted();
            if (promoted == null) {
                // a waitlisted entry left: close the gaps
                registration.getEvent().getWaitlist().forEach(data::saveRegistration);
            } else {
                data.saveRegistration(promoted);
            }
            data.commit();
        } catch (RuntimeException e) {
            data.rollback();
            throw e;
        }
        persistNotifications();
        return status;
    }

    // ------------------------------------------------------------------
    // UC4: Check In
    // ------------------------------------------------------------------

    /**
     * Self check-in with the event's code (SD-04).
     *
     * @throws IllegalStateException on invalid code or missing registration
     */
    public Attendance checkInToEvent(int eventId, String checkInCode) {
        Event event = data.findEventById(eventId);
        if (!event.verifyCheckInCode(checkInCode)) {
            throw new IllegalStateException("Invalid check-in code for \"" + event.getTitle() + "\".");
        }
        Registration registration = event.findRegistrationFor(student);
        if (registration == null || registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new IllegalStateException("You have no active registration for this event.");
        }
        Attendance attendance = registration.markAttended(unievents.shared.CheckInMethod.CODE);
        try {
            data.beginTransaction();
            data.saveRegistration(registration);
            data.saveAttendance(registration);
            data.commit();
        } catch (RuntimeException e) {
            data.rollback();
            throw e;
        }
        return attendance;
    }

    // ------------------------------------------------------------------
    // UC5: Review
    // ------------------------------------------------------------------

    /** Attended and not yet reviewed (SD-05). */
    public boolean checkReviewEligibility(int eventId) {
        Event event = data.findEventById(eventId);
        return event.isEligibleForReview(student);
    }

    /**
     * Submits rating + comment (SD-05). Domain re-checks eligibility.
     *
     * @throws IllegalStateException when not eligible
     * @throws IllegalArgumentException when rating out of 1..5
     */
    public Review submitReview(int eventId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        Event event = data.findEventById(eventId);
        Review review = event.addReview(student, rating, comment); // domain validates
        data.saveReview(review);
        return review;
    }

    /** Average rating of an event (updates after a new review). */
    public double getAverageRating(int eventId) {
        return data.findEventById(eventId).getAverageRating();
    }

    // ------------------------------------------------------------------
    // Inbox
    // ------------------------------------------------------------------

    public List<Notification> getMyNotifications() {
        return data.findNotificationsFor(student);
    }

    // ------------------------------------------------------------------
    // Projections (loop bodies of SD-01)
    // ------------------------------------------------------------------

    private List<EventSummary> toSummaries(List<Event> events) {
        return events.stream()
                .map(e -> new EventSummary(
                        e.getEventId(), e.getTitle(), e.getClub().getName(),
                        e.getStartDateTime(), e.getVenue(), e.getRemainingSeats(), e.getStatus()))
                .toList();
    }

    private EventDetails toDetails(Event e) {
        return new EventDetails(
                e.getEventId(), e.getTitle(), e.getDescription(),
                e.getStartDateTime(), e.getEndDateTime(), e.getVenue(),
                e.getCapacity(), e.getRemainingSeats(),
                e.getCategory().getCategoryId(), e.getCategory().getName(),
                e.getClub().getName(), e.getAverageRating());
    }

    /** Drains and stores notifications produced by the last domain call. */
    private void persistNotifications() {
        List<Notification> pending = student.takePendingNotifications();
        if (!pending.isEmpty()) {
            data.saveNotifications(pending);
        }
    }
}
