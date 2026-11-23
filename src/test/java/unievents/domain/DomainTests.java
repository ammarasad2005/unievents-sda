package unievents.domain;

import unievents.shared.CheckInMethod;
import unievents.shared.EventStatus;
import unievents.shared.RegistrationStatus;
import unievents.shared.ResolutionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain unit tests: registration, waitlist, cancellation, promotion,
 * check-in, review eligibility, status machine — all without UI or database.
 */
class DomainTests {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private Club publishedClubWithEvent(int capacity, LocalDateTime start) {
        Club club = new Club("Coding Society", "All things code");
        Category cat = new Category(1, "Tech");
        Event event = club.createEvent("Hackathon 101", "Overnight build sprint",
                start, start.plusHours(8), "Auditorium", capacity, cat);
        event.changeStatus(EventStatus.PUBLISHED, null);
        return club;
    }

    private Student student(int id, String name) {
        return new Student(id, name, name.toLowerCase().replace(' ', '.') + "@nu.edu.pk",
                "hash", true, "23I-0" + id);
    }

    private LocalDateTime future() {
        return LocalDateTime.now().plusDays(7).withNano(0);
    }

    // ------------------------------------------------------------------
    // UC2: register → seat vs waitlist
    // ------------------------------------------------------------------

    @Test
    void registerTakesSeatWhenAvailable() {
        Club club = publishedClubWithEvent(2, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");

        Registration r = event.register(s1);

        assertEquals(RegistrationStatus.REGISTERED, r.getStatus());
        assertEquals(0, r.getWaitlistPosition());
        assertEquals(1, event.getRemainingSeats());
        assertEquals(1, event.getRegisteredCount());
    }

    @Test
    void fullEventWaitlistsWithNextPosition() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Student s2 = student(2, "Bilal Raza");
        Student s3 = student(3, "Cyra Ali");
        event.register(s1);

        Registration r2 = event.register(s2);
        Registration r3 = event.register(s3);

        assertEquals(RegistrationStatus.WAITLISTED, r2.getStatus());
        assertEquals(1, r2.getWaitlistPosition());
        assertEquals(RegistrationStatus.WAITLISTED, r3.getStatus());
        assertEquals(2, r3.getWaitlistPosition());
        assertEquals(0, event.getRemainingSeats());
        assertEquals(2, event.getWaitlistedCount());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        Club club = publishedClubWithEvent(5, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        event.register(s1);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> event.register(s1));
        assertTrue(ex.getMessage().contains("already"));
    }

    @Test
    void draftEventRejectsRegistration() {
        Club club = new Club("Drama Club", "Stage");
        Event draft = club.createEvent("Auditions", "Open auditions",
                future(), future().plusHours(3), "Studio", 10, new Category(1, "Cultural"));
        Student s1 = student(1, "Ayna Khan");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> draft.register(s1));
        assertTrue(ex.getMessage().contains("PUBLISHED"));
    }

    @Test
    void pastEventRejectsRegistration() {
        Club club = new Club("Drama Club", "Stage");
        // Rehydrated past event already PUBLISHED (changeStatus would reject a past publish).
        Event past = new Event(1, club, "Old Show", "Already happened",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(2),
                "Studio", 10, EventStatus.PUBLISHED, "ABC123", new Category(1, "Cultural"));
        Student s1 = student(1, "Ayna Khan");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> past.register(s1));
        assertTrue(ex.getMessage().contains("started"));
    }

    // ------------------------------------------------------------------
    // UC3: cancel → auto-promotion
    // ------------------------------------------------------------------

    @Test
    void cancelFreedSeatPromotesNextWaitlisted() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Student s2 = student(2, "Bilal Raza");
        Student s3 = student(3, "Cyra Ali");
        Registration r1 = event.register(s1);
        Registration r2 = event.register(s2);
        Registration r3 = event.register(s3);

        RegistrationStatus result = r1.cancel();

        assertEquals(RegistrationStatus.CANCELLED, result);
        assertEquals(RegistrationStatus.CANCELLED, r1.getStatus());
        assertEquals(RegistrationStatus.REGISTERED, r2.getStatus()); // promoted
        assertEquals(0, r2.getWaitlistPosition());
        assertEquals(1, r3.getWaitlistPosition());                     // resequenced
        assertEquals(0, event.getRemainingSeats());                    // seat refilled
        assertEquals(1, event.getWaitlistedCount());
    }

    @Test
    void cancelWaitlistedOnlyResequences() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Student s2 = student(2, "Bilal Raza");
        Student s3 = student(3, "Cyra Ali");
        event.register(s1);
        Registration r2 = event.register(s2);
        Registration r3 = event.register(s3);

        r2.cancel();

        assertEquals(RegistrationStatus.CANCELLED, r2.getStatus());
        assertEquals(RegistrationStatus.WAITLISTED, r3.getStatus());
        assertEquals(1, r3.getWaitlistPosition()); // moved up from 2
        assertEquals(1, event.getRegisteredCount()); // s1 still holds the seat
    }

    @Test
    void doubleCancelIsRejected() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Registration r1 = event.register(s1);
        r1.cancel();

        assertThrows(IllegalStateException.class, r1::cancel);
    }

    // ------------------------------------------------------------------
    // UC4: check-in
    // ------------------------------------------------------------------

    @Test
    void checkInCreatesAttendanceAndAttendedStatus() {
        Club club = publishedClubWithEvent(2, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Registration r1 = event.register(s1);

        assertTrue(event.verifyCheckInCode(event.getCheckInCode()));
        assertFalse(event.verifyCheckInCode("WRONG1"));

        Attendance att = r1.markAttended(CheckInMethod.CODE);

        assertNotNull(att);
        assertEquals(CheckInMethod.CODE, att.getMethod());
        assertEquals(RegistrationStatus.ATTENDED, r1.getStatus());
        assertTrue(r1.isAttended());
        assertEquals(1, event.getAttendedCount());
        assertThrows(IllegalStateException.class, () -> r1.markAttended(CheckInMethod.CODE));
    }

    @Test
    void waitlistedCannotCheckIn() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        event.register(student(1, "Ayna Khan"));
        Registration r2 = event.register(student(2, "Bilal Raza"));

        assertThrows(IllegalStateException.class, () -> r2.markAttended(CheckInMethod.CODE));
    }

    // ------------------------------------------------------------------
    // UC5: review eligibility
    // ------------------------------------------------------------------

    @Test
    void reviewRequiresAttendanceAndUniqueness() {
        Club club = publishedClubWithEvent(3, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Registration r1 = event.register(s1);

        assertFalse(event.isEligibleForReview(s1));   // not attended yet
        r1.markAttended(CheckInMethod.CODE);
        assertTrue(event.isEligibleForReview(s1));

        Review review = event.addReview(s1, 5, "Great hackathon!");
        assertEquals(5, review.getRating());
        assertEquals(5.0, event.getAverageRating());
        assertFalse(event.isEligibleForReview(s1));   // already reviewed
        assertThrows(IllegalStateException.class, () -> event.addReview(s1, 4, "Again"));
        // rating range is enforced even before eligibility (new unpersisted review)
        assertThrows(IllegalArgumentException.class,
                () -> new Review(s1, event, 6, "Bad rating"));
        assertThrows(IllegalArgumentException.class,
                () -> new Review(s1, event, 0, "Bad rating"));
    }

    // ------------------------------------------------------------------
    // UC6: edit rules
    // ------------------------------------------------------------------

    @Test
    void capacityCannotDropBelowRegistered() {
        Club club = publishedClubWithEvent(5, future());
        Event event = club.getEvents().get(0);
        event.register(student(1, "Ayna Khan"));
        event.register(student(2, "Bilal Raza"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> event.updateDetails(null, null, null, null, null, 1, null));
        assertTrue(ex.getMessage().contains("registered"));
        event.updateDetails(null, "New description", null, null, null, 2, null);
        assertEquals(2, event.getCapacity());
    }

    @Test
    void completedEventCannotBeEdited() {
        Club club = publishedClubWithEvent(5, future());
        Event event = club.getEvents().get(0);
        event.changeStatus(EventStatus.PUBLISHED, null); // already published in fixture
        event.changeStatus(EventStatus.COMPLETED, null);

        assertThrows(IllegalStateException.class,
                () -> event.updateDetails("New title", null, null, null, null, 5, null));
    }

    // ------------------------------------------------------------------
    // UC7: status transitions
    // ------------------------------------------------------------------

    @Test
    void statusMachineAllowsOnlyValidTransitions() {
        Club club = new Club("Music Society", "Live sessions");
        Category cat = new Category(1, "Cultural");
        Event e = club.createEvent("Qawwali Night", "Sufi evening",
                future(), future().plusHours(3), "Main Hall", 50, cat);

        assertEquals(EventStatus.DRAFT, e.getStatus());
        assertThrows(IllegalStateException.class, () -> e.changeStatus(EventStatus.COMPLETED, null));

        e.changeStatus(EventStatus.PUBLISHED, null);
        assertEquals(EventStatus.PUBLISHED, e.getStatus());
        assertThrows(IllegalStateException.class, () -> e.changeStatus(EventStatus.DRAFT, null));

        e.changeStatus(EventStatus.CANCELLED, "Venue unavailable");
        assertEquals(EventStatus.CANCELLED, e.getStatus());
        assertThrows(IllegalStateException.class, () -> e.changeStatus(EventStatus.PUBLISHED, null));
    }

    @Test
    void publishRequiresFutureStartAndCancelRequiresReason() {
        Club club = new Club("Music Society", "Live sessions");
        Category cat = new Category(1, "Cultural");
        Event past = club.createEvent("Old Concert", "Gone",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusHours(1),
                "Hall", 50, cat);
        assertThrows(IllegalStateException.class, () -> past.changeStatus(EventStatus.PUBLISHED, null));

        Event e = club.createEvent("New Concert", "Upcoming",
                future(), future().plusHours(2), "Hall", 50, cat);
        assertThrows(IllegalArgumentException.class, () -> e.changeStatus(EventStatus.CANCELLED, " "));
        e.changeStatus(EventStatus.CANCELLED, "Low signups");
        assertEquals(EventStatus.CANCELLED, e.getStatus());
    }

    // ------------------------------------------------------------------
    // UC8: audience
    // ------------------------------------------------------------------

    @Test
    void audienceCountsAndSelection() {
        Club club = publishedClubWithEvent(2, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Student s2 = student(2, "Bilal Raza");
        Student s3 = student(3, "Cyra Ali");
        event.register(s1);
        event.register(s2);
        event.register(s3);   // waitlisted

        assertEquals(2, event.getAudienceCounts().registeredCount());
        assertEquals(1, event.getAudienceCounts().waitlistedCount());
        assertEquals(3, event.getAudienceCounts().bothCount());
        assertEquals(2, event.getAudience(unievents.shared.AudienceType.REGISTERED).size());
        assertEquals(1, event.getAudience(unievents.shared.AudienceType.WAITLISTED).size());
        assertEquals(3, event.getAudience(unievents.shared.AudienceType.BOTH).size());
    }

    // ------------------------------------------------------------------
    // UC9/UC10: manual attendance and report
    // ------------------------------------------------------------------

    @Test
    void recordAttendanceMarksSelectedManual() {
        Club club = publishedClubWithEvent(3, future());
        Event event = club.getEvents().get(0);
        Registration r1 = event.register(student(1, "Ayna Khan"));
        Registration r2 = event.register(student(2, "Bilal Raza"));
        Registration r3 = event.register(student(3, "Cyra Ali"));
        r1.assignId(101);
        r2.assignId(102);
        r3.assignId(103);

        List<Registration> updated = event.recordAttendance(List.of(101, 102));

        assertEquals(2, updated.size());
        assertEquals(RegistrationStatus.ATTENDED, r1.getStatus());
        assertEquals(RegistrationStatus.ATTENDED, r2.getStatus());
        assertEquals(RegistrationStatus.REGISTERED, r3.getStatus());
        assertEquals(CheckInMethod.MANUAL, r1.getAttendance().getMethod());
        // marking again is idempotent (already checked in → not updated)
        assertTrue(event.recordAttendance(List.of(101)).isEmpty());
    }

    @Test
    void attendanceSheetAndReport() {
        Club club = publishedClubWithEvent(3, future());
        Event event = club.getEvents().get(0);
        Registration r1 = event.register(student(1, "Ayna Khan"));
        event.register(student(2, "Bilal Raza"));
        event.register(student(3, "Cyra Ali"));
        r1.markAttended(CheckInMethod.CODE);

        assertEquals(3, event.getAttendanceSheet().size());
        assertEquals(3, event.getParticipants().size());

        ClubOrganizer organizer = new ClubOrganizer(10, "Hamza Tariq", "hamza@nu.edu.pk",
                "hash", true, true);
        AttendanceReport report = event.generateReport(organizer);

        // registeredCount counts seat holders: 2 REGISTERED + 1 ATTENDED
        assertEquals(3, report.getRegisteredCount());
        assertEquals(1, report.getAttendedCount());
        assertEquals(3, report.getParticipants().size());
    }

    // ------------------------------------------------------------------
    // UC11: club creation
    // ------------------------------------------------------------------

    @Test
    void adminCreatesClubWithPresident() {
        Admin admin = new Admin(99, "Sara Malik", "sara@nu.edu.pk", "hash", true, "CS Dept");
        ClubOrganizer president = new ClubOrganizer(10, "Hamza Tariq", "hamza@nu.edu.pk",
                "hash", true, false);

        Club club = admin.createClub("Robotics Society", "Bots & beyond", president);

        assertEquals("Robotics Society", club.getName());
        assertTrue(club.isActive());
        assertEquals(1, club.getOrganizers().size());
        assertTrue(president.isPresident());
        assertTrue(club.getOrganizers().contains(president));
        assertEquals(club, president.getClub());
    }

    // ------------------------------------------------------------------
    // UC12: conflict resolution
    // ------------------------------------------------------------------

    @Test
    void adminResolvesConflictAppliesResolution() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        Registration r1 = event.register(s1);

        RegistrationConflict conflict = new RegistrationConflict(event,
                unievents.shared.ConflictType.DOUBLE_BOOKING);
        conflict.addConcernedRegistration(r1);

        Admin admin = new Admin(99, "Sara Malik", "sara@nu.edu.pk", "hash", true, "CS Dept");

        unievents.shared.ConflictStatus status = conflict.resolve(admin,
                ResolutionType.MOVE_TO_WAITLIST, "Seat reassigned by admin");

        assertEquals(unievents.shared.ConflictStatus.RESOLVED, status);
        assertEquals(RegistrationStatus.WAITLISTED, r1.getStatus());
        assertEquals(1, r1.getWaitlistPosition());
        assertNotNull(conflict.getResolvedAt());
        assertEquals(admin, conflict.getResolvedBy());
        assertThrows(IllegalStateException.class,
                () -> conflict.resolve(admin, ResolutionType.OTHER, "again"));
    }

    // ------------------------------------------------------------------
    // OOP: polymorphism through the User hierarchy
    // ------------------------------------------------------------------

    @Test
    void getRoleIsPolymorphic() {
        List<User> users = List.of(
                student(1, "Ayna Khan"),
                new ClubOrganizer(10, "Hamza Tariq", "hamza@nu.edu.pk", "hash", true, true),
                new Admin(99, "Sara Malik", "sara@nu.edu.pk", "hash", true, "CS Dept"));

        List<String> roles = users.stream().map(User::getRole).toList();

        assertEquals(List.of("STUDENT", "CLUB_ORGANIZER", "ADMIN"), roles);
    }

    @Test
    void notificationsCollectOnUserSupertype() {
        Club club = publishedClubWithEvent(1, future());
        Event event = club.getEvents().get(0);
        Student s1 = student(1, "Ayna Khan");
        event.register(s1);

        List<Notification> pending = s1.takePendingNotifications();
        assertEquals(1, pending.size());
        assertEquals(unievents.shared.NotificationType.REGISTRATION, pending.get(0).getType());
    }
}
