package unievents.domain;

import unievents.shared.AttendanceRow;
import unievents.shared.AudienceCounts;
import unievents.shared.AudienceType;
import unievents.shared.CheckInMethod;
import unievents.shared.EventStatus;
import unievents.shared.NotificationType;
import unievents.shared.Participant;
import unievents.shared.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A club-hosted event.
 *
 * GRASP Information Expert: the event owns its registrations, waitlist,
 * capacity accounting, status machine and reviews — every seat-related rule
 * lives here, not in controllers or UI.
 * GRASP Creator: {@link #register(Student)} creates Registrations and
 * {@link Registration#markAttended(CheckInMethod)} creates Attendance.
 */
public class Event {

    private Integer eventId;            // null until persisted
    private String title;
    private String description;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String venue;
    private int capacity;
    private EventStatus status;
    private String checkInCode;

    private final Club club;
    private Category category;
    private final List<Registration> registrations = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();

    /** New-event constructor (Club.createEvent). Events start as DRAFT. */
    public Event(Club club, String title, String description,
                 LocalDateTime startDateTime, LocalDateTime endDateTime,
                 String venue, int capacity, Category category) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title is required.");
        }
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("Event venue is required.");
        }
        if (startDateTime == null || endDateTime == null) {
            throw new IllegalArgumentException("Event start and end are required.");
        }
        if (!endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException("Event end must be after its start.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Event category is required.");
        }
        this.club = club;
        this.title = title.trim();
        this.description = description;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.venue = venue.trim();
        this.capacity = capacity;
        this.category = category;
        this.status = EventStatus.DRAFT;
        this.checkInCode = generateCheckInCode();
    }

    /** Rehydration constructor used by the persistence layer. */
    public Event(Integer eventId, Club club, String title, String description,
                 LocalDateTime startDateTime, LocalDateTime endDateTime,
                 String venue, int capacity, EventStatus status, String checkInCode,
                 Category category) {
        this(club, title, description, startDateTime, endDateTime, venue, capacity, category);
        this.eventId = eventId;
        this.status = status;
        this.checkInCode = checkInCode;
    }

    // ------------------------------------------------------------------
    // Basic getters
    // ------------------------------------------------------------------

    public Integer getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getVenue() {
        return venue;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getCheckInCode() {
        return checkInCode;
    }

    public Club getClub() {
        return club;
    }

    public Category getCategory() {
        return category;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int eventId) {
        this.eventId = eventId;
    }

    // ------------------------------------------------------------------
    // UC6: Manage event details
    // ------------------------------------------------------------------

    /**
     * Edits event fields (SD-06 updateDetails). Business rule from D3 UC-07:
     * capacity must never fall below the current registered count, and a
     * completed event is immutable.
     */
    public void updateDetails(String title, String description,
                              LocalDateTime startDateTime, LocalDateTime endDateTime,
                              String venue, int capacity, Category category) {
        if (status == EventStatus.COMPLETED) {
            throw new IllegalStateException("A completed event cannot be edited.");
        }
        String newTitle = title == null ? this.title : title;
        if (newTitle.isBlank()) {
            throw new IllegalArgumentException("Event title is required.");
        }
        LocalDateTime newStart = startDateTime == null ? this.startDateTime : startDateTime;
        LocalDateTime newEnd = endDateTime == null ? this.endDateTime : endDateTime;
        if (!newEnd.isAfter(newStart)) {
            throw new IllegalArgumentException("Event end must be after its start.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero.");
        }
        if (capacity < getRegisteredCount()) {
            throw new IllegalStateException(
                    "Capacity cannot go below the registered count (" + getRegisteredCount() + ").");
        }
        this.title = newTitle.trim();
        this.description = description;
        this.startDateTime = newStart;
        this.endDateTime = newEnd;
        this.venue = (venue == null || venue.isBlank() ? this.venue : venue.trim());
        this.capacity = capacity;
        if (category != null) {
            this.category = category;
        }
    }

    // ------------------------------------------------------------------
    // UC7: Manage event status
    // ------------------------------------------------------------------

    /**
     * Status machine with valid transitions only (SD-07 changeStatus + validateTransition).
     *
     * DRAFT → PUBLISHED | CANCELLED
     * PUBLISHED → CANCELLED | COMPLETED
     * CANCELLED, COMPLETED → terminal
     *
     * Publishing requires a future start (D3 UC-08); cancelling notifies the
     * registered audience (event_cancelled notification).
     */
    public EventStatus changeStatus(EventStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new IllegalArgumentException("A new status is required.");
        }
        if (newStatus == status) {
            return status;
        }
        boolean valid =
                (status == EventStatus.DRAFT && (newStatus == EventStatus.PUBLISHED || newStatus == EventStatus.CANCELLED))
                        || (status == EventStatus.PUBLISHED && (newStatus == EventStatus.CANCELLED || newStatus == EventStatus.COMPLETED));
        if (!valid) {
            throw new IllegalStateException(
                    "Invalid transition " + status + " → " + newStatus + ".");
        }
        if (newStatus == EventStatus.PUBLISHED && !startDateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Only events starting in the future can be published.");
        }
        if (newStatus == EventStatus.PUBLISHED && (title == null || title.isBlank() || venue == null || venue.isBlank())) {
            throw new IllegalStateException("Event data incomplete: title and venue are required to publish.");
        }
        if (newStatus == EventStatus.CANCELLED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required to cancel an event.");
        }
        if (newStatus == EventStatus.COMPLETED && getRegisteredCount() == 0) {
            // allowed, but reporting nothing yet
        }
        EventStatus previous = status;
        this.status = newStatus;
        if (newStatus == EventStatus.CANCELLED && previous == EventStatus.PUBLISHED) {
            notifyCancellation(reason);
        }
        return status;
    }

    /** Whether a status change is legal right now (UI hint, no state change). */
    public boolean canTransitionTo(EventStatus newStatus) {
        try {
            validateTransition(newStatus);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Pure transition check without side effects (called from changeStatus). */
    private void validateTransition(EventStatus newStatus) {
        boolean valid =
                (status == EventStatus.DRAFT && (newStatus == EventStatus.PUBLISHED || newStatus == EventStatus.CANCELLED))
                        || (status == EventStatus.PUBLISHED && (newStatus == EventStatus.CANCELLED || newStatus == EventStatus.COMPLETED));
        if (!valid) {
            throw new IllegalStateException(
                    "Invalid transition " + status + " → " + newStatus + ".");
        }
    }

    // ------------------------------------------------------------------
    // Seat / waitlist accounting
    // ------------------------------------------------------------------

    /**
     * Seat holders: students currently REGISTERED plus those already ATTENDED
     * (an attended student still occupies a seat). Used for capacity math,
     * the report and the capacity-edit rule.
     */
    public int getRegisteredCount() {
        return (int) registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                        || r.getStatus() == RegistrationStatus.ATTENDED)
                .count();
    }

    public int getWaitlistedCount() {
        return (int) registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .count();
    }

    public int getAttendedCount() {
        return (int) registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ATTENDED)
                .count();
    }

    public int getRemainingSeats() {
        return Math.max(0, capacity - getRegisteredCount());
    }

    public List<Registration> getRegistrations() {
        return new ArrayList<>(registrations);
    }

    /** Waitlist ordered by position (D5: getWaitlist(): List&lt;Registration&gt;). */
    public List<Registration> getWaitlist() {
        return registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .sorted(Comparator.comparingInt(Registration::getWaitlistPosition))
                .toList();
    }

    // ------------------------------------------------------------------
    // UC2: Register
    // ------------------------------------------------------------------

    /**
     * Registers a student. Seats left → REGISTERED; otherwise WAITLISTED with
     * the next position (SD-02). The event (Information Expert) decides which.
     *
     * @throws IllegalStateException if the event is not open for registration
     *                               or the student already holds a seat/position
     */
    public Registration register(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("A student is required.");
        }
        if (status != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Registration is only possible for PUBLISHED events.");
        }
        if (!startDateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Registration is closed: the event has already started.");
        }
        Registration existing = findRegistrationFor(student);
        if (existing != null && existing.isActiveRegistration()) {
            throw new IllegalStateException(
                    "You are already " + existing.getStatus() + " for this event.");
        }

        RegistrationStatus status;
        Integer waitlistPosition = null;
        if (getRemainingSeats() > 0) {
            status = RegistrationStatus.REGISTERED;
        } else {
            status = RegistrationStatus.WAITLISTED;
            waitlistPosition = getWaitlist().size() + 1;
        }
        Registration registration = new Registration(student, this, status, waitlistPosition);
        registrations.add(registration);
        student.receiveNotification(new Notification(
                "You are " + status + " for \"" + title + "\""
                + (waitlistPosition != null ? " (waitlist position " + waitlistPosition + ")." : "."),
                waitlistPosition != null ? NotificationType.WAITLIST : NotificationType.REGISTRATION));
        return registration;
    }

    /** Finds the student's registration on this event, if any. */
    public Registration findRegistrationFor(Student student) {
        return registrations.stream()
                .filter(r -> r.getStudent() == student || (student != null
                        && r.getStudent() != null
                        && r.getStudent().getUserId() == student.getUserId()))
                .findFirst()
                .orElse(null);
    }

    public Registration findRegistration(int registrationId) {
        return registrations.stream()
                .filter(r -> r.getRegistrationId() != null && r.getRegistrationId() == registrationId)
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // UC3: Cancel + waitlist promotion
    // ------------------------------------------------------------------

    /**
     * Promotes the first waitlisted registration into the freed seat and
     * notifies the promoted student (SD-03). Called by {@link Registration#cancel()}.
     *
     * @return the promoted registration, or null when the waitlist is empty
     */
    public Registration promoteNextWaitlisted() {
        List<Registration> waitlist = getWaitlist();
        if (waitlist.isEmpty()) {
            return null;
        }
        Registration promoted = waitlist.get(0);
        promoted.promote();
        resequenceWaitlist();
        promoted.getStudent().receiveNotification(new Notification(
                "A seat opened for \"" + title + "\" — you are now REGISTERED.",
                NotificationType.PROMOTION));
        return promoted;
    }

    /** Closes position gaps after a waitlist entry leaves (package-private: Registration.cancel uses it). */
    void resequenceWaitlist() {
        int pos = 1;
        for (Registration r : getWaitlist()) {
            r.setWaitlistPosition(pos++);
        }
    }

    // ------------------------------------------------------------------
    // UC4: Check-in
    // ------------------------------------------------------------------

    /** True when the submitted code matches this event's code (SD-04). */
    public boolean verifyCheckInCode(String code) {
        return code != null && checkInCode != null && checkInCode.equalsIgnoreCase(code.trim());
    }

    // ------------------------------------------------------------------
    // UC5: Reviews
    // ------------------------------------------------------------------

    /** Attended and no review yet → eligible (SD-05 isEligibleForReview). */
    public boolean isEligibleForReview(Student student) {
        Registration registration = findRegistrationFor(student);
        boolean attended = registration != null && registration.isAttended();
        return attended && !hasReviewFrom(student);
    }

    /** True when the student already reviewed this event. */
    public boolean hasReviewFrom(Student student) {
        return reviews.stream().anyMatch(r -> r.getStudent().getUserId() == student.getUserId());
    }

    /**
     * Adds a review after re-checking eligibility (SD-05 addReview).
     *
     * @throws IllegalStateException when not eligible
     */
    public Review addReview(Student student, int rating, String comment) {
        if (!isEligibleForReview(student)) {
            throw new IllegalStateException(
                    "Only attendees without an existing review can review this event.");
        }
        Review review = new Review(student, this, rating, comment);
        reviews.add(review);
        return review;
    }

    public List<Review> getReviews() {
        return new ArrayList<>(reviews);
    }

    /** Mean rating or 0 when no reviews exist. */
    public double getAverageRating() {
        if (reviews.isEmpty()) {
            return 0.0;
        }
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
    }

    // ------------------------------------------------------------------
    // UC8: Announcements
    // ------------------------------------------------------------------

    /** Audience sizes for the announcement screen (SD-08 getAudienceCounts). */
    public AudienceCounts getAudienceCounts() {
        return new AudienceCounts(getSeatHolders().size(), getWaitlistedCount(),
                getSeatHolders().size() + getWaitlistedCount());
    }

    /** Students in the chosen audience (SD-08 getAudience). REGISTERED covers seat holders. */
    public List<Student> getAudience(AudienceType audience) {
        List<Student> seatHolders = getSeatHolders().stream()
                .map(Registration::getStudent).toList();
        List<Student> waitlisted = getWaitlist().stream()
                .map(Registration::getStudent).toList();
        return switch (audience) {
            case REGISTERED -> seatHolders;
            case WAITLISTED -> waitlisted;
            case BOTH -> {
                List<Student> all = new ArrayList<>(seatHolders);
                all.addAll(waitlisted);
                yield all;
            }
        };
    }

    private List<Registration> getSeatHolders() {
        return registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                        || r.getStatus() == RegistrationStatus.ATTENDED)
                .toList();
    }

    // ------------------------------------------------------------------
    // UC9: Attendance
    // ------------------------------------------------------------------

    /** Attendance sheet rows: one per active registration (SD-09). */
    public List<AttendanceRow> getAttendanceSheet() {
        return registrations.stream()
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .sorted(Comparator.comparing(r -> r.getStudent().getFullName(),
                        Comparator.nullsLast(String::compareTo)))
                .map(Registration::toAttendanceRow)
                .toList();
    }

    /**
     * Marks the given registrations attended via MANUAL check-in (SD-09).
     *
     * @return the updated registrations (caller persists them)
     */
    public List<Registration> recordAttendance(List<Integer> registrationIds) {
        List<Registration> updated = new ArrayList<>();
        if (registrationIds == null) {
            return updated;
        }
        for (Integer id : registrationIds) {
            Registration registration = findRegistration(id);
            if (registration == null) {
                throw new IllegalArgumentException("Unknown registration id " + id + ".");
            }
            if (!registration.isAttended()) {
                registration.markAttended(CheckInMethod.MANUAL);
                updated.add(registration);
            }
        }
        return updated;
    }

    // ------------------------------------------------------------------
    // UC10: Reports
    // ------------------------------------------------------------------

    /**
     * Generates the attendance report for this event (SD-10).
     * Information Expert: the event knows its counts and participants.
     */
    public AttendanceReport generateReport(ClubOrganizer organizer) {
        AttendanceReport report = new AttendanceReport(organizer, this,
                getRegisteredCount(), getAttendedCount());
        // participants are derived from registrations on demand
        return report;
    }

    /** Participant lines for the report view (SSD-10). */
    public List<Participant> getParticipants() {
        return registrations.stream()
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .map(Registration::toParticipant)
                .toList();
    }

    // ------------------------------------------------------------------
    // Notifications on cancellation
    // ------------------------------------------------------------------

    private void notifyCancellation(String reason) {
        for (Student s : getAudience(AudienceType.BOTH)) {
            s.receiveNotification(new Notification(
                    "Event \"" + title + "\" was cancelled"
                    + (reason != null && !reason.isBlank() ? ": " + reason : "."),
                    NotificationType.EVENT_CANCELLED));
        }
    }

    // ------------------------------------------------------------------
    // Rehydration hooks (persistence layer)
    // ------------------------------------------------------------------

    public void loadRegistrations(List<Registration> loaded) {
        registrations.clear();
        if (loaded != null) {
            registrations.addAll(loaded);
        }
    }

    public void loadReviews(List<Review> loaded) {
        reviews.clear();
        if (loaded != null) {
            reviews.addAll(loaded);
        }
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    private static String generateCheckInCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return title + " (" + status + ")";
    }
}
