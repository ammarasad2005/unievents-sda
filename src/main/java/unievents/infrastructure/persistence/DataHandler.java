package unievents.infrastructure.persistence;

import unievents.domain.Admin;
import unievents.domain.Announcement;
import unievents.domain.AttendanceReport;
import unievents.domain.Category;
import unievents.domain.Club;
import unievents.domain.ClubOrganizer;
import unievents.domain.Event;
import unievents.domain.Notification;
import unievents.domain.Registration;
import unievents.domain.RegistrationConflict;
import unievents.domain.Review;
import unievents.domain.Student;
import unievents.domain.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary of the application (Protected Variation): application
 * controllers depend on this interface only; {@link SupabaseHandler} is one
 * replaceable PostgreSQL/Supabase implementation.
 *
 * Method names follow the DataHandler operations of the Deliverable 5 design
 * class diagram and SD-01..SD-12.
 */
public interface DataHandler {

    // ------------------------------------------------------------------
    // Users / login
    // ------------------------------------------------------------------

    /** Finds an active user by email (thin session precondition, not a graded UC). */
    Optional<User> findUserByEmail(String email);

    /** Loads the full Student (with registrations) by user id. */
    Optional<Student> findStudentById(int userId);

    /** Loads the full ClubOrganizer (with clubs + events) by user id. */
    Optional<ClubOrganizer> findOrganizerById(int userId);

    /** Loads the Admin by user id. */
    Optional<Admin> findAdminById(int userId);

    // ------------------------------------------------------------------
    // Events (SD-01/02/06/07)
    // ------------------------------------------------------------------

    Event findEventById(int eventId);

    /** All PUBLISHED events ordered by start time (browsable catalogue). */
    List<Event> findPublishedEvents();

    /** Published events filtered by optional category, club and exact date. */
    List<Event> findEventsByFilters(Integer categoryId, Integer clubId, LocalDateTime date);

    /** Inserts (id == null) or updates an event; assigns the generated key. */
    void saveEvent(Event event);

    // ------------------------------------------------------------------
    // Categories / clubs (SD-06/SD-11)
    // ------------------------------------------------------------------

    Category findCategoryById(int categoryId);

    List<Category> findAllCategories();

    List<Club> findAllClubs();

    /** Inserts (id == null) or updates a club; assigns the generated key. */
    void saveClub(Club club);

    // ------------------------------------------------------------------
    // Registrations (SD-02/03/04/09)
    // ------------------------------------------------------------------

    /** Loads an event's registrations (with students + attendance) into the event. */
    void loadRegistrations(Event event);

    /** Loads a student's registrations (with events) into the student. */
    void loadRegistrations(Student student);

    /** Inserts (id == null) or updates a registration; assigns the generated key. */
    void saveRegistration(Registration registration);

    /** Persists a batch of registrations in one transaction. */
    void saveRegistrations(List<Registration> registrations);

    /** Inserts the attendance record attached to a registration (if present). */
    void saveAttendance(Registration registration);

    // ------------------------------------------------------------------
    // Reviews (SD-05)
    // ------------------------------------------------------------------

    void saveReview(Review review);

    /** Loads an event's reviews (with students) into the event. */
    void loadReviews(Event event);

    // ------------------------------------------------------------------
    // Announcements & notifications (SD-08)
    // ------------------------------------------------------------------

    void saveAnnouncement(Announcement announcement);

    /** Persists every pending notification of the given users in one batch. */
    void saveNotifications(List<Notification> notifications);

    /** Recent notifications of a user (inbox on the home screens). */
    List<Notification> findNotificationsFor(User user);

    // ------------------------------------------------------------------
    // Attendance reports (SD-10)
    // ------------------------------------------------------------------

    void saveAttendanceReport(AttendanceReport report);

    // ------------------------------------------------------------------
    // Conflicts (SD-12)
    // ------------------------------------------------------------------

    List<RegistrationConflict> findOpenConflicts();

    RegistrationConflict findConflictById(int conflictId);

    /** Persists conflict state + concerned-registration links. */
    void saveConflict(RegistrationConflict conflict);

    /** Persists resolution changes of registrations touched by a conflict. */
    void saveConflictedRegistrations(List<Registration> registrations);

    // ------------------------------------------------------------------
    // Transaction control (Supabase/PostgreSQL implementation)
    // ------------------------------------------------------------------

    /** Starts a multi-row transaction (register/cancel/promote, conflicts). */
    default void beginTransaction() {
        DatabaseController.getInstance().beginTransaction();
    }

    /** Commits the current transaction. */
    default void commit() {
        DatabaseController.getInstance().commit();
    }

    /** Rolls back the current transaction. */
    default void rollback() {
        DatabaseController.getInstance().rollback();
    }
}
