package unievents.application;

import unievents.domain.Attendance;
import unievents.domain.Category;
import unievents.domain.Club;
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
import java.util.Map;
import java.util.stream.Collectors;

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

    /** Filter dimensions for the browse screen (UC1). */
    public List<Category> getCategories() {
        return data.findAllCategories();
    }

    public List<Club> getClubs() {
        return data.findAllClubs();
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
     * Cancels one registration. The domain promotes the next waitlisted
     * student inside {@link Registration#cancel()} when a seat is freed, and
     * the controller persists the touched rows in one transaction (SD-03).
     */
    public RegistrationStatus cancelRegistration(int registrationId) {
        data.loadRegistrations(student);
        Registration own = student.findRegistration(registrationId);
        if (own == null) {
            throw new IllegalStateException("Registration " + registrationId + " not found.");
        }
        // Reload the registration through its event so the object we cancel
        // is the same instance held in event.getRegistrations(); otherwise the
        // waitlist promotion triggered by cancel() would mutate a detached copy.
        Event event = own.getEvent();
        data.loadRegistrations(event);
        Registration registration = event.findRegistration(registrationId);
        if (registration == null) {
            throw new IllegalStateException("Registration " + registrationId + " not found.");
        }

        Map<Integer, RegistrationStatus> before = event.getRegistrations().stream()
                .collect(Collectors.toMap(Registration::getRegistrationId,
                        Registration::getStatus));
        Map<Integer, Integer> positionsBefore = event.getRegistrations().stream()
                .filter(r -> r.getWaitlistPosition() > 0)
                .collect(Collectors.toMap(Registration::getRegistrationId,
                        Registration::getWaitlistPosition));

        RegistrationStatus status = registration.cancel();         // may promote + resequence

        try {
            data.beginTransaction();
            for (Registration r : event.getRegistrations()) {
                boolean changed = !before.getOrDefault(r.getRegistrationId(), r.getStatus())
                        .equals(r.getStatus())
                        || !positionsBefore.getOrDefault(r.getRegistrationId(),
                                r.getWaitlistPosition()).equals(r.getWaitlistPosition());
                if (changed) {
                    data.saveRegistration(r);
                }
            }
            data.commit();
        } catch (RuntimeException e) {
            data.rollback();
            throw e;
        }
        // persist notifications of every student touched by this flow
        // (the canceller AND any promoted waitlisted student)
        List<Notification> produced = new java.util.ArrayList<>();
        for (Registration r : event.getRegistrations()) {
            produced.addAll(r.getStudent().takePendingNotifications());
        }
        produced.addAll(student.takePendingNotifications());
        if (!produced.isEmpty()) {
            data.saveNotifications(produced);
        }
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
