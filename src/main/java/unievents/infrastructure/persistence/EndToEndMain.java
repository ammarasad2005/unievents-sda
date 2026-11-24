package unievents.infrastructure.persistence;

import unievents.application.ClubController;
import unievents.application.ConflictController;
import unievents.application.ManagementController;
import unievents.application.StudentController;
import unievents.domain.Admin;
import unievents.domain.Club;
import unievents.domain.ClubOrganizer;
import unievents.domain.Student;
import unievents.domain.User;
import unievents.shared.AttendanceRow;
import unievents.shared.ConflictDetails;
import unievents.shared.ConflictSummary;
import unievents.shared.EventDetails;
import unievents.shared.EventStatus;
import unievents.shared.EventSummary;
import unievents.shared.RegistrationStatus;
import unievents.shared.RegistrationSummary;
import unievents.shared.ResolutionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * End-to-end integration test of all 12 use cases against a real PostgreSQL
 * database (the same SupabaseHandler JDBC path used in production; only the
 * URL points at a local test instance).
 *
 * Run: java -DUNIEVENTS_DB_URL=... -DUNIEVENTS_DB_USER=... unievents.infrastructure.persistence.EndToEndMain
 * (env vars UNIEVENTS_DB_URL / UNIEVENTS_DB_USER / UNIEVENTS_DB_PASSWORD also work)
 */
public final class EndToEndMain {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        DataHandler data = new SupabaseHandler();

        // ---- session: login as student (thin precondition) ----------------
        User ayna = data.findUserByEmail("ayna@nu.edu.pk").orElseThrow();
        check("login student", ayna instanceof Student && "STUDENT".equals(ayna.getRole()));

        Student student = (Student) ayna;
        StudentController sc = new StudentController(student, data);

        // ---- UC1: browse --------------------------------------------------
        List<EventSummary> published = sc.getPublishedEvents();
        check("UC1 published events listed", published.size() == 2);
        EventDetails d1 = sc.getEventDetails(1);
        check("UC1 details carry remaining seats", d1.remainingSeats() == 0);
        List<EventSummary> filtered = sc.filterEvents(null, null, null);
        check("UC1 filters (none) return all", filtered.size() == 2);
        List<EventSummary> byCategory = sc.filterEvents(4, null, null);
        check("UC1 filter by category Workshop", byCategory.size() == 1
                && byCategory.get(0).eventId() == 2);

        // ---- UC2: register (waitlist because event 1 is full) -------------
        // Cyra (user 3) has no seed registrations; Ayna is already REGISTERED on event 1.
        User cyraUser = data.findUserByEmail("cyra@nu.edu.pk").orElseThrow();
        Student cyra = (Student) cyraUser;
        StudentController cc1 = new StudentController(cyra, data);
        unievents.domain.Registration r3 = cc1.registerForEvent(1);
        check("UC2 full event waitlists", r3.getStatus() == RegistrationStatus.WAITLISTED && r3.getWaitlistPosition() == 2);
        unievents.domain.Registration r4 = cc1.registerForEvent(2);
        check("UC2 seat available registers", r4.getStatus() == RegistrationStatus.REGISTERED);
        boolean dupRejected = false;
        try {
            cc1.registerForEvent(2);
        } catch (IllegalStateException e) {
            dupRejected = true;
        }
        check("UC2 duplicate rejected", dupRejected);

        // ---- UC3: cancel → auto-promote ------------------------------------
        List<RegistrationSummary> cyraRegs = cc1.getMyRegistrations();
        check("UC3 my registrations listed", cyraRegs.size() == 2);
        int cancelId = cyraRegs.stream()
                .filter(s -> s.eventId() == 1 && s.status() == RegistrationStatus.WAITLISTED
                        && s.waitlistPosition() != null && s.waitlistPosition() == 2)
                .map(RegistrationSummary::registrationId).findFirst().orElse(-1);
        RegistrationStatus cancelled = cc1.cancelRegistration(cancelId);
        check("UC3 cancel returns CANCELLED", cancelled == RegistrationStatus.CANCELLED);

        // now cancel Ayna's seat on event 1 → Bilal (waitlist #1) should be promoted
        List<RegistrationSummary> aynaRegs = sc.getMyRegistrations();
        int seatId = aynaRegs.stream()
                .filter(s -> s.eventId() == 1 && s.status() == RegistrationStatus.REGISTERED)
                .map(RegistrationSummary::registrationId).findFirst().orElse(-1);
        sc.cancelRegistration(seatId);
        Student bilal = (Student) data.findUserByEmail("bilal@nu.edu.pk").orElseThrow();
        StudentController bc = new StudentController(bilal, data);
        RegistrationSummary bilalReg = bc.getMyRegistrations().stream()
                .filter(s -> s.eventId() == 1).findFirst().orElse(null);
        check("UC3 waitlisted auto-promoted after seat freed",
                bilalReg != null && bilalReg.status() == RegistrationStatus.REGISTERED);

        // ---- UC4: check-in --------------------------------------------------
        // Bilal now REGISTERED on event 1 → invalid code first, then valid
        boolean invalidRejected = false;
        try {
            bc.checkInToEvent(1, "WRONG9");
        } catch (IllegalStateException e) {
            invalidRejected = true;
        }
        check("UC4 invalid code rejected", invalidRejected);
        unievents.domain.Attendance att = bc.checkInToEvent(1, "HACK12");
        check("UC4 valid code checks in", att.getMethod() == unievents.shared.CheckInMethod.CODE);
        RegistrationSummary bilalAfter = bc.getMyRegistrations().stream()
                .filter(s -> s.eventId() == 1).findFirst().orElse(null);
        check("UC4 status ATTENDED", bilalAfter.status() == RegistrationStatus.ATTENDED);

        // ---- UC5: review -----------------------------------------------------
        boolean eligible = bc.checkReviewEligibility(1);
        check("UC5 attended student eligible", eligible);
        unievents.domain.Review review = bc.submitReview(1, 5, "Energizing hackathon!");
        check("UC5 review saved", review.getReviewId() != null);
        boolean secondRejected = false;
        try {
            bc.submitReview(1, 4, "second");
        } catch (IllegalStateException e) {
            secondRejected = true;
        }
        check("UC5 second review rejected", secondRejected);
        check("UC5 average updates", sc.getAverageRating(1) == 5.0);

        // ---- organizer flows (UC6–UC10) --------------------------------------
        ClubOrganizer hamza = data.findOrganizerById(10).orElseThrow();
        ManagementController mc = new ManagementController(hamza, data);

        List<EventSummary> myEvents = mc.getMyEvents();
        check("UC6 my events listed", myEvents.size() == 4);

        unievents.domain.Event created = mc.saveEvent(null, "Portfolio Review Day",
                "One-on-one portfolio feedback", LocalDateTime.now().plusDays(21).withHour(11),
                LocalDateTime.now().plusDays(21).withHour(14), "Design Studio", 25, 4);
        check("UC6 create draft", created.getStatus() == EventStatus.DRAFT
                && created.getEventId() != null);

        mc.saveEvent(created.getEventId(), "Portfolio Review Day (updated)",
                null, null, null, null, 30, 4);
        EventDetails updated = mc.getEventDetails(created.getEventId());
        check("UC6 edit updates fields", updated.capacity() == 30
                && updated.title().contains("updated"));

        boolean capacityRejected = false;
        try {
            mc.saveEvent(1, null, null, null, null, null, 1, 1); // event 1 has 2 active seats
        } catch (IllegalStateException e) {
            capacityRejected = true;
        }
        check("UC6 capacity below registered rejected", capacityRejected);

        // UC7: publish the draft, then invalid transition
        EventStatus publishedStatus = mc.changeEventStatus(created.getEventId(), EventStatus.PUBLISHED, null);
        check("UC7 draft published", publishedStatus == EventStatus.PUBLISHED);
        boolean badTransition = false;
        try {
            mc.changeEventStatus(created.getEventId(), EventStatus.DRAFT, null);
        } catch (IllegalStateException e) {
            badTransition = true;
        }
        check("UC7 invalid transition rejected", badTransition);
        EventStatus cancelledStatus = mc.changeEventStatus(created.getEventId(),
                EventStatus.CANCELLED, "Speaker unavailable");
        check("UC7 cancel with reason", cancelledStatus == EventStatus.CANCELLED);

        // UC8: announcement with audience counts
        var counts = mc.getAudienceCounts(1);
        check("UC8 audience counts", counts.registeredCount() >= 1 && counts.bothCount() >= counts.registeredCount());
        var announcement = mc.sendAnnouncement(1, "Hackathon prep",
                "Bring your laptops at 9:00 sharp.", unievents.shared.AudienceType.BOTH);
        check("UC8 announcement saved", announcement.getAnnouncementId() != null);
        List<unievents.domain.Notification> inbox = bc.getMyNotifications();
        check("UC8 recipients notified", inbox.stream().anyMatch(
                n -> n.getType() == unievents.shared.NotificationType.ANNOUNCEMENT));

        // UC9: attendance sheet + manual marking
        var sheet = mc.getAttendanceSheet(2);
        check("UC9 sheet loads with code", "GIT99".equals(sheet.checkInCode()) && !sheet.rows().isEmpty());
        AttendanceRow firstRow = sheet.rows().stream()
                .filter(r -> !r.alreadyCheckedIn()).findFirst().orElse(null);
        int saved = mc.recordAttendance(2, List.of(firstRow.registrationId()));
        check("UC9 manual attendance saved", saved == 1);
        var sheet2 = mc.getAttendanceSheet(2);
        check("UC9 row now attended", sheet2.rows().stream()
                .anyMatch(r -> r.registrationId() == firstRow.registrationId() && r.alreadyCheckedIn()));

        // UC10: report
        var report = mc.generateAttendanceReport(2);
        check("UC10 report saved with counts",
                report.report().getReportId() != null && report.participants().size() >= 2);

        // ---- admin flows (UC11, UC12) ----------------------------------------
        Admin sara = (Admin) data.findUserByEmail("sara@nu.edu.pk").orElseThrow();
        ClubController cc = new ClubController(sara, data);

        Club robotics = cc.createClub("Photography Society", "Frames and light", null);
        check("UC11 club created", robotics.getClubId() != null);
        Club withPresident = cc.createClub("Debate Society", "Argumentation", 11);
        check("UC11 club with president", withPresident.getOrganizers().size() == 1);

        ConflictController kc = new ConflictController(sara, data);
        List<ConflictSummary> open = kc.getOpenConflicts();
        check("UC12 open conflicts listed", !open.isEmpty());
        ConflictDetails details = kc.getConflictDetails(open.get(0).conflictId());
        check("UC12 details carry capacity + waitlist", details.capacity() > 0);
        unievents.shared.ConflictStatus resolved = kc.resolveConflict(open.get(0).conflictId(),
                ResolutionType.KEEP_REGISTERED, "Verified manually; seat stands.");
        check("UC12 resolved", resolved == unievents.shared.ConflictStatus.RESOLVED);
        List<ConflictSummary> openAfter = kc.getOpenConflicts();
        check("UC12 no longer open", openAfter.isEmpty());

        System.out.println();
        System.out.println("END-TO-END RESULT: passed=" + passed + " failed=" + failed);
        DatabaseController.getInstance().closeConnection();
        System.exit(failed == 0 ? 0 : 3);
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
        }
    }
}
