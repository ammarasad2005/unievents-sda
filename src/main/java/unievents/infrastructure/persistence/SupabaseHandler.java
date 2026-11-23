package unievents.infrastructure.persistence;

import unievents.domain.Admin;
import unievents.domain.Announcement;
import unievents.domain.Attendance;
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
import unievents.shared.CheckInMethod;
import unievents.shared.ConflictStatus;
import unievents.shared.ConflictType;
import unievents.shared.EventStatus;
import unievents.shared.NotificationType;
import unievents.shared.RegistrationStatus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link DataHandler} for Supabase (PostgreSQL).
 *
 * Every method wraps SQLExceptions into IllegalStateException so nothing
 * unchecked leaks to the UI; the UI shows the message. Multi-row writes run
 * inside transactions via the {@link DatabaseController} singleton.
 */
public class SupabaseHandler implements DataHandler {

    private final DatabaseController db;

    public SupabaseHandler() {
        this.db = DatabaseController.getInstance();
    }

    /** Test seam: inject a configured controller. */
    SupabaseHandler(DatabaseController db) {
        this.db = db;
    }

    // ==================================================================
    // Users
    // ==================================================================

    @Override
    public Optional<User> findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT u.user_id, u.full_name, u.email, u.password_hash, u.is_active, u.role,
                       s.roll_number, o.is_president, a.department
                FROM users u
                LEFT JOIN students s ON s.user_id = u.user_id
                LEFT JOIN club_organizers o ON o.user_id = u.user_id
                LEFT JOIN admins a ON a.user_id = u.user_id
                WHERE LOWER(u.email) = LOWER(?) AND u.is_active = TRUE
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUserRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Login lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Student> findStudentById(int userId) {
        String sql = """
                SELECT u.user_id, u.full_name, u.email, u.password_hash, u.is_active, u.role,
                       s.roll_number
                FROM users u
                JOIN students s ON s.user_id = u.user_id
                WHERE u.user_id = ? AND u.is_active = TRUE
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(rs.getInt("user_id"), rs.getString("full_name"),
                            rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("is_active"),
                            rs.getString("roll_number"));
                    loadRegistrations(student);
                    return Optional.of(student);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Student lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ClubOrganizer> findOrganizerById(int userId) {
        String sql = """
                SELECT u.user_id, u.full_name, u.email, u.password_hash, u.is_active, u.role,
                       o.is_president
                FROM users u
                JOIN club_organizers o ON o.user_id = u.user_id
                WHERE u.user_id = ? AND u.is_active = TRUE
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClubOrganizer organizer = new ClubOrganizer(rs.getInt("user_id"),
                            rs.getString("full_name"), rs.getString("email"),
                            rs.getString("password_hash"), rs.getBoolean("is_active"),
                            rs.getBoolean("is_president"));
                    loadOrganizerGraph(organizer);
                    return Optional.of(organizer);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Organizer lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Admin> findAdminById(int userId) {
        String sql = """
                SELECT u.user_id, u.full_name, u.email, u.password_hash, u.is_active, u.role,
                       a.department
                FROM users u
                JOIN admins a ON a.user_id = u.user_id
                WHERE u.user_id = ? AND u.is_active = TRUE
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Admin(rs.getInt("user_id"), rs.getString("full_name"),
                        rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("is_active"),
                        rs.getString("department")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Admin lookup failed: " + e.getMessage(), e);
        }
    }

    /** Polymorphic row mapping: builds the right User subtype from the role column. */
    private User mapUserRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String fullName = rs.getString("full_name");
        String email = rs.getString("email");
        String hash = rs.getString("password_hash");
        boolean active = rs.getBoolean("is_active");
        String role = rs.getString("role");
        return switch (role) {
            case "STUDENT" -> new Student(id, fullName, email, hash, active, rs.getString("roll_number"));
            case "CLUB_ORGANIZER" -> new ClubOrganizer(id, fullName, email, hash, active,
                    rs.getBoolean("is_president"));
            case "ADMIN" -> new Admin(id, fullName, email, hash, active, rs.getString("department"));
            default -> throw new IllegalStateException("Unknown role in users table: " + role);
        };
    }

    // ==================================================================
    // Events
    // ==================================================================

    @Override
    public Event findEventById(int eventId) {
        String sql = """
                SELECT e.*, c.name AS club_name, cat.name AS category_name
                FROM events e
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                WHERE e.event_id = ?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Event " + eventId + " not found.");
                }
                Event event = mapEventRow(rs, null);
                loadRegistrations(event);
                loadReviews(event);
                return event;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Event lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Event> findPublishedEvents() {
        String sql = """
                SELECT e.*, c.name AS club_name, cat.name AS category_name
                FROM events e
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                WHERE e.status = 'PUBLISHED'
                ORDER BY e.start_date_time
                """;
        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(mapEventRow(rs, null));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Published events query failed: " + e.getMessage(), e);
        }
        events.forEach(this::loadRegistrations);
        return events;
    }

    @Override
    public List<Event> findEventsByFilters(Integer categoryId, Integer clubId, LocalDateTime date) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.*, c.name AS club_name, cat.name AS category_name
                FROM events e
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                WHERE e.status = 'PUBLISHED'
                """);
        List<Object> params = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND e.category_id = ?");
            params.add(categoryId);
        }
        if (clubId != null) {
            sql.append(" AND e.club_id = ?");
            params.add(clubId);
        }
        if (date != null) {
            sql.append(" AND e.start_date_time >= ? AND e.start_date_time < ?");
            params.add(Timestamp.valueOf(date.toLocalDate().atStartOfDay()));
            params.add(Timestamp.valueOf(date.toLocalDate().plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY e.start_date_time");

        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapEventRow(rs, null));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Event filter query failed: " + e.getMessage(), e);
        }
        events.forEach(this::loadRegistrations);
        return events;
    }

    @Override
    public void saveEvent(Event event) {
        if (event.getEventId() == null) {
            insertEvent(event);
        } else {
            updateEvent(event);
        }
    }

    private void insertEvent(Event event) {
        String sql = """
                INSERT INTO events (club_id, category_id, title, description,
                                    start_date_time, end_date_time, venue, capacity, status, check_in_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            bindEvent(ps, event);
            ps.executeUpdate();
            event.assignId(generatedKey(ps, "event"));
        } catch (SQLException e) {
            throw new IllegalStateException("Event insert failed: " + e.getMessage(), e);
        }
    }

    private void updateEvent(Event event) {
        String sql = """
                UPDATE events
                SET club_id = ?, category_id = ?, title = ?, description = ?,
                    start_date_time = ?, end_date_time = ?, venue = ?, capacity = ?,
                    status = ?, check_in_code = ?
                WHERE event_id = ?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            bindEvent(ps, event);
            ps.setInt(11, event.getEventId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Event update failed: " + e.getMessage(), e);
        }
    }

    private void bindEvent(PreparedStatement ps, Event event) throws SQLException {
        ps.setInt(1, event.getClub().getClubId());
        ps.setInt(2, event.getCategory().getCategoryId());
        ps.setString(3, event.getTitle());
        ps.setString(4, event.getDescription());
        ps.setTimestamp(5, Timestamp.valueOf(event.getStartDateTime()));
        ps.setTimestamp(6, Timestamp.valueOf(event.getEndDateTime()));
        ps.setString(7, event.getVenue());
        ps.setInt(8, event.getCapacity());
        ps.setString(9, event.getStatus().name());
        ps.setString(10, event.getCheckInCode());
    }

    /** Builds an Event (and its Club + Category shells) from a joined row. */
    private Event mapEventRow(ResultSet rs, Club knownClub) throws SQLException {
        Club club = knownClub != null ? knownClub : new Club(
                rs.getInt("club_id"), rs.getString("club_name"), null, true);
        Category category = new Category(rs.getInt("category_id"), rs.getString("category_name"));
        Timestamp start = rs.getTimestamp("start_date_time");
        Timestamp end = rs.getTimestamp("end_date_time");
        return new Event(rs.getInt("event_id"), club, rs.getString("title"),
                rs.getString("description"),
                start == null ? null : start.toLocalDateTime(),
                end == null ? null : end.toLocalDateTime(),
                rs.getString("venue"), rs.getInt("capacity"),
                EventStatus.valueOf(rs.getString("status")), rs.getString("check_in_code"),
                category);
    }

    // ==================================================================
    // Categories / clubs
    // ==================================================================

    @Override
    public Category findCategoryById(int categoryId) {
        String sql = "SELECT category_id, name FROM categories WHERE category_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Category " + categoryId + " not found.");
                }
                return new Category(rs.getInt("category_id"), rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Category lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Category> findAllCategories() {
        String sql = "SELECT category_id, name FROM categories ORDER BY name";
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(new Category(rs.getInt("category_id"), rs.getString("name")));
            }
            return categories;
        } catch (SQLException e) {
            throw new IllegalStateException("Category list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Club> findAllClubs() {
        String sql = "SELECT club_id, name, description, is_active FROM clubs ORDER BY name";
        List<Club> clubs = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                clubs.add(new Club(rs.getInt("club_id"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("is_active")));
            }
            return clubs;
        } catch (SQLException e) {
            throw new IllegalStateException("Club list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveClub(Club club) {
        if (club.getClubId() == null) {
            insertClub(club);
        } else {
            updateClub(club);
        }
    }

    private void insertClub(Club club) {
        String insertClub = """
                INSERT INTO clubs (name, description, is_active, created_by)
                VALUES (?, ?, ?, ?)
                """;
        try {
            db.beginTransaction();
            try (PreparedStatement ps = db.getConnection().prepareStatement(insertClub,
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, club.getName());
                ps.setString(2, club.getDescription());
                ps.setBoolean(3, club.isActive());
                ps.setInt(4, 99);                       // seeded admin (creator)
                ps.executeUpdate();
                club.assignId(generatedKey(ps, "club"));
            }
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "INSERT INTO club_organizer_assignments (club_id, organizer_id) VALUES (?, ?) " +
                            "ON CONFLICT DO NOTHING")) {
                for (ClubOrganizer organizer : club.getOrganizers()) {
                    ps.setInt(1, club.getClubId());
                    ps.setInt(2, organizer.getUserId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            db.commit();
        } catch (SQLException | RuntimeException e) {
            db.rollback();
            throw new IllegalStateException("Club insert failed: " + rootMessage(e), e);
        }
    }

    private void updateClub(Club club) {
        String sql = "UPDATE clubs SET name = ?, description = ?, is_active = ? WHERE club_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, club.getName());
            ps.setString(2, club.getDescription());
            ps.setBoolean(3, club.isActive());
            ps.setInt(4, club.getClubId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Club update failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // Registrations & attendance
    // ==================================================================

    @Override
    public void loadRegistrations(Event event) {
        String sql = """
                SELECT r.registration_id, r.registered_at, r.status, r.waitlist_position,
                       u.user_id, u.full_name, u.email, u.password_hash, u.is_active, s.roll_number,
                       att.attendance_id, att.checked_in_at, att.method
                FROM registrations r
                JOIN students s ON s.user_id = r.student_id
                JOIN users u ON u.user_id = s.user_id
                LEFT JOIN attendances att ON att.registration_id = r.registration_id
                WHERE r.event_id = ?
                ORDER BY r.registration_id
                """;
        List<Registration> registrations = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, event.getEventId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registrations.add(mapRegistrationRow(rs, event));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Registration load failed: " + e.getMessage(), e);
        }
        event.loadRegistrations(registrations);
    }

    @Override
    public void loadRegistrations(Student student) {
        String sql = """
                SELECT r.registration_id, r.registered_at, r.status,
                       r.waitlist_position,
                       e.*, c.name AS club_name, cat.name AS category_name,
                       att.attendance_id, att.checked_in_at, att.method
                FROM registrations r
                JOIN events e ON e.event_id = r.event_id
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                LEFT JOIN attendances att ON att.registration_id = r.registration_id
                WHERE r.student_id = ?
                ORDER BY r.registration_id
                """;
        List<Registration> registrations = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, student.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Event event = mapEventRow(rs, null);
                    Registration reg = new Registration(rs.getInt("registration_id"), student, event,
                            toLocalDateTime(rs.getTimestamp("registered_at")),
                            RegistrationStatus.valueOf(rs.getString("status")),
                            (Integer) rs.getObject("waitlist_position"));
                    attachAttendance(reg, rs);
                    registrations.add(reg);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("My registrations load failed: " + e.getMessage(), e);
        }
        student.loadRegistrations(registrations);
    }

    private Registration mapRegistrationRow(ResultSet rs, Event event) throws SQLException {
        Student student = new Student(rs.getInt("user_id"), rs.getString("full_name"),
                rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("is_active"),
                rs.getString("roll_number"));
        Registration reg = new Registration(rs.getInt("registration_id"), student, event,
                toLocalDateTime(rs.getTimestamp("registered_at")),
                RegistrationStatus.valueOf(rs.getString("status")),
                (Integer) rs.getObject("waitlist_position"));
        attachAttendance(reg, rs);
        return reg;
    }

    private void attachAttendance(Registration reg, ResultSet rs) throws SQLException {
        int attendanceId = rs.getInt("attendance_id");
        if (!rs.wasNull()) {
            Timestamp at = rs.getTimestamp("checked_in_at");
            Attendance attendance = new Attendance(attendanceId,
                    at == null ? null : at.toLocalDateTime(),
                    CheckInMethod.valueOf(rs.getString("method")));
            reg.attachAttendance(attendance);
        }
    }

    @Override
    public void saveRegistration(Registration registration) {
        if (registration.getRegistrationId() == null) {
            insertRegistration(registration);
        } else {
            updateRegistration(registration);
        }
    }

    @Override
    public void saveRegistrations(List<Registration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        try {
            db.beginTransaction();
            for (Registration r : registrations) {
                saveRegistration(r);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            throw e;
        }
    }

    private void insertRegistration(Registration registration) {
        String sql = """
                INSERT INTO registrations (event_id, student_id, registered_at, status, waitlist_position)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, registration.getEvent().getEventId());
            ps.setInt(2, registration.getStudent().getUserId());
            ps.setTimestamp(3, Timestamp.valueOf(registration.getRegisteredAt()));
            ps.setString(4, registration.getStatus().name());
            if (registration.getWaitlistPosition() > 0) {
                ps.setInt(5, registration.getWaitlistPosition());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.executeUpdate();
            registration.assignId(generatedKey(ps, "registration"));
        } catch (SQLException e) {
            throw new IllegalStateException("Registration insert failed: " + e.getMessage(), e);
        }
    }

    private void updateRegistration(Registration registration) {
        String sql = """
                UPDATE registrations SET status = ?, waitlist_position = ?
                WHERE registration_id = ?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, registration.getStatus().name());
            if (registration.getWaitlistPosition() > 0) {
                ps.setInt(2, registration.getWaitlistPosition());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setInt(3, registration.getRegistrationId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Registration update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveAttendance(Registration registration) {
        Attendance attendance = registration.getAttendance();
        if (attendance == null || attendance.getAttendanceId() != null) {
            return;
        }
        String sql = """
                INSERT INTO attendances (registration_id, checked_in_at, method)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, registration.getRegistrationId());
            ps.setTimestamp(2, Timestamp.valueOf(attendance.getCheckedInAt()));
            ps.setString(3, attendance.getMethod().name());
            ps.executeUpdate();
            attendance.assignId(generatedKey(ps, "attendance"));
        } catch (SQLException e) {
            throw new IllegalStateException("Attendance insert failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // Reviews
    // ==================================================================

    @Override
    public void saveReview(Review review) {
        if (review.getReviewId() == null) {
            String sql = """
                    INSERT INTO reviews (event_id, student_id, rating, comment, posted_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, review.getEvent().getEventId());
                ps.setInt(2, review.getStudent().getUserId());
                ps.setInt(3, review.getRating());
                ps.setString(4, review.getComment());
                ps.setTimestamp(5, Timestamp.valueOf(review.getPostedAt()));
                ps.executeUpdate();
                review.assignId(generatedKey(ps, "review"));
            } catch (SQLException e) {
                throw new IllegalStateException("Review insert failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void loadReviews(Event event) {
        String sql = """
                SELECT rv.review_id, rv.rating, rv.comment, rv.posted_at,
                       u.user_id, u.full_name, u.email, u.password_hash, u.is_active, s.roll_number
                FROM reviews rv
                JOIN students s ON s.user_id = rv.student_id
                JOIN users u ON u.user_id = s.user_id
                WHERE rv.event_id = ?
                ORDER BY rv.posted_at
                """;
        List<Review> reviews = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, event.getEventId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Student student = new Student(rs.getInt("user_id"), rs.getString("full_name"),
                            rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("is_active"),
                            rs.getString("roll_number"));
                    Timestamp posted = rs.getTimestamp("posted_at");
                    reviews.add(new Review(student, event, rs.getInt("rating"), rs.getString("comment"),
                            posted == null ? null : posted.toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Review load failed: " + e.getMessage(), e);
        }
        event.loadReviews(reviews);
    }

    // ==================================================================
    // Announcements & notifications
    // ==================================================================

    @Override
    public void saveAnnouncement(Announcement announcement) {
        String sql = """
                INSERT INTO announcements (event_id, organizer_id, subject, message, audience, sent_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, announcement.getEvent().getEventId());
            ps.setInt(2, announcement.getSender().getUserId());
            ps.setString(3, announcement.getSubject());
            ps.setString(4, announcement.getMessage());
            ps.setString(5, announcement.getAudience().name());
            ps.setTimestamp(6, Timestamp.valueOf(announcement.getSentAt()));
            ps.executeUpdate();
            announcement.assignId(generatedKey(ps, "announcement"));
        } catch (SQLException e) {
            throw new IllegalStateException("Announcement insert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveNotifications(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO notifications (user_id, message, type, created_at, is_read, announcement_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            for (Notification n : notifications) {
                ps.setInt(1, n.getRecipient().getUserId());
                ps.setString(2, n.getMessage());
                ps.setString(3, n.getType().name());
                ps.setTimestamp(4, Timestamp.valueOf(n.getCreatedAt()));
                ps.setBoolean(5, n.isRead());
                if (n.getAnnouncement() != null && n.getAnnouncement().getAnnouncementId() != null) {
                    ps.setInt(6, n.getAnnouncement().getAnnouncementId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Notification insert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Notification> findNotificationsFor(User user) {
        String sql = """
                SELECT notification_id, message, type, created_at, is_read
                FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """;
        List<Notification> notifications = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, user.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp at = rs.getTimestamp("created_at");
                    notifications.add(new Notification(rs.getInt("notification_id"), user,
                            rs.getString("message"), NotificationType.valueOf(rs.getString("type")),
                            at == null ? null : at.toLocalDateTime(),
                            rs.getBoolean("is_read"), null));
                }
            }
            return notifications;
        } catch (SQLException e) {
            throw new IllegalStateException("Notification load failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // Attendance reports
    // ==================================================================

    @Override
    public void saveAttendanceReport(AttendanceReport report) {
        String sql = """
                INSERT INTO attendance_reports (event_id, organizer_id, generated_at,
                                                registered_count, attended_count)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, report.getEvent().getEventId());
            ps.setInt(2, report.getOrganizer().getUserId());
            ps.setTimestamp(3, Timestamp.valueOf(report.getGeneratedAt()));
            ps.setInt(4, report.getRegisteredCount());
            ps.setInt(5, report.getAttendedCount());
            ps.executeUpdate();
            report.assignId(generatedKey(ps, "attendance report"));
        } catch (SQLException e) {
            throw new IllegalStateException("Attendance report insert failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // Conflicts
    // ==================================================================

    @Override
    public List<RegistrationConflict> findOpenConflicts() {
        String sql = """
                SELECT rc.conflict_id, rc.type, rc.status,
                       rc.detected_at,
                       e.*, c.name AS club_name, cat.name AS category_name
                FROM registration_conflicts rc
                JOIN events e ON e.event_id = rc.event_id
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                WHERE rc.status = 'OPEN'
                ORDER BY rc.detected_at
                """;
        List<RegistrationConflict> conflicts = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Event event = mapEventRow(rs, null);
                RegistrationConflict conflict = new RegistrationConflict(rs.getInt("conflict_id"),
                        event, ConflictType.valueOf(rs.getString("type")),
                        ConflictStatus.valueOf(rs.getString("status")),
                        toLocalDateTime(rs.getTimestamp("detected_at")),
                        null, null, null, null);
                loadConcernedRegistrations(conflict);
                conflicts.add(conflict);
            }
            return conflicts;
        } catch (SQLException e) {
            throw new IllegalStateException("Open conflicts query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RegistrationConflict findConflictById(int conflictId) {
        String sql = """
                SELECT rc.conflict_id, rc.type, rc.status,
                       rc.detected_at,
                       e.*, c.name AS club_name, cat.name AS category_name
                FROM registration_conflicts rc
                JOIN events e ON e.event_id = rc.event_id
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                WHERE rc.conflict_id = ?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, conflictId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Conflict " + conflictId + " not found.");
                }
                Event event = mapEventRow(rs, null);
                loadRegistrations(event);
                RegistrationConflict conflict = new RegistrationConflict(rs.getInt("conflict_id"),
                        event, ConflictType.valueOf(rs.getString("type")),
                        ConflictStatus.valueOf(rs.getString("status")),
                        toLocalDateTime(rs.getTimestamp("detected_at")),
                        null, null, null, null);
                loadConcernedRegistrations(conflict);
                return conflict;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Conflict lookup failed: " + e.getMessage(), e);
        }
    }

    private void loadConcernedRegistrations(RegistrationConflict conflict) throws SQLException {
        String sql = """
                SELECT r.registration_id, r.registered_at, r.status, r.waitlist_position,
                       u.user_id, u.full_name, u.email, u.password_hash, u.is_active, s.roll_number
                FROM conflict_registrations cr
                JOIN registrations r ON r.registration_id = cr.registration_id
                JOIN students s ON s.user_id = r.student_id
                JOIN users u ON u.user_id = s.user_id
                WHERE cr.conflict_id = ?
                """;
        List<Registration> concerned = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, conflict.getConflictId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    concerned.add(mapRegistrationRow(rs, conflict.getEvent()));
                }
            }
        }
        conflict.loadConcernedRegistrations(concerned);
    }

    @Override
    public void saveConflict(RegistrationConflict conflict) {
        String sql = """
                INSERT INTO registration_conflicts (event_id, type, status, detected_at,
                                                    resolution, reason, resolved_at, resolved_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, conflict.getEvent().getEventId());
            ps.setString(2, conflict.getType().name());
            ps.setString(3, conflict.getStatus().name());
            ps.setTimestamp(4, Timestamp.valueOf(conflict.getDetectedAt()));
            ps.setString(5, conflict.getResolution() == null ? null : conflict.getResolution().name());
            ps.setString(6, conflict.getReason());
            ps.setTimestamp(7, conflict.getResolvedAt() == null ? null : Timestamp.valueOf(conflict.getResolvedAt()));
            if (conflict.getResolvedBy() != null) {
                ps.setInt(8, conflict.getResolvedBy().getUserId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.executeUpdate();
            conflict.assignId(generatedKey(ps, "conflict"));
        } catch (SQLException e) {
            throw new IllegalStateException("Conflict save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveConflictedRegistrations(List<Registration> registrations) {
        saveRegistrations(registrations);
    }

    /** Updates conflict state after a resolve (admin flow). */
    public void updateConflict(RegistrationConflict conflict) {
        String sql = """
                UPDATE registration_conflicts
                SET status = ?, resolution = ?, reason = ?, resolved_at = ?, resolved_by = ?
                WHERE conflict_id = ?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, conflict.getStatus().name());
            ps.setString(2, conflict.getResolution() == null ? null : conflict.getResolution().name());
            ps.setString(3, conflict.getReason());
            ps.setTimestamp(4, conflict.getResolvedAt() == null ? null : Timestamp.valueOf(conflict.getResolvedAt()));
            if (conflict.getResolvedBy() != null) {
                ps.setInt(5, conflict.getResolvedBy().getUserId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setInt(6, conflict.getConflictId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Conflict update failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // Organizer graph (clubs + events of one organizer)
    // ==================================================================

    /** Loads the organizer's clubs and each club's events into the object graph. */
    private void loadOrganizerGraph(ClubOrganizer organizer) throws SQLException {
        String clubsSql = """
                SELECT c.club_id, c.name, c.description, c.is_active
                FROM clubs c
                JOIN club_organizer_assignments a ON a.club_id = c.club_id
                WHERE a.organizer_id = ? AND c.is_active = TRUE
                ORDER BY c.club_id
                """;
        List<Club> clubs = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(clubsSql)) {
            ps.setInt(1, organizer.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clubs.add(new Club(rs.getInt("club_id"), rs.getString("name"),
                            rs.getString("description"), rs.getBoolean("is_active")));
                }
            }
        }
        String eventsSql = """
                SELECT e.*, c.name AS club_name, cat.name AS category_name
                FROM events e
                JOIN clubs c ON c.club_id = e.club_id
                JOIN categories cat ON cat.category_id = e.category_id
                JOIN club_organizer_assignments a ON a.club_id = e.club_id
                WHERE a.organizer_id = ?
                ORDER BY e.start_date_time
                """;
        for (Club club : clubs) {
            List<Event> events = new ArrayList<>();
            try (PreparedStatement ps = db.getConnection().prepareStatement(eventsSql)) {
                ps.setInt(1, organizer.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getInt("club_id") == club.getClubId()) {
                            events.add(mapEventRow(rs, club));
                        }
                    }
                }
            }
            club.loadEvents(events);
            events.forEach(this::loadRegistrations);
        }
        organizer.loadClubs(clubs);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private int generatedKey(PreparedStatement ps, String what) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new IllegalStateException("No generated key returned for " + what + " insert.");
    }

    private LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private String rootMessage(Throwable t) {
        return t.getMessage();
    }
}
