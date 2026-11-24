-- UniEvents — demo seed data (run AFTER schema.sql on Supabase/PostgreSQL)
-- FAST-NUCES SDA | Section E | 23I-0716 · 25I-1022
--
-- Demo accounts (password for every account: unievents)
--   Student:      ayna@nu.edu.pk        (has seat + waitlist history)
--   Student:      bilal@nu.edu.pk
--   Student:      cyra@nu.edu.pk
--   Organizer:    hamza@nu.edu.pk       (president, Coding Society)
--   Organizer:    zoya@nu.edu.pk        (president, Adventure Club)
--   Admin:        sara@nu.edu.pk        (Student Affairs)
--
-- Event dates are anchored relative to a demo run date. Adjust start_date_time
-- values if you re-seed months later — published future events are required
-- for the registration demo.

BEGIN;

-- Housekeeping for re-seeding (order respects FKs)
DELETE FROM notifications;
DELETE FROM conflict_registrations;
DELETE FROM registration_conflicts;
DELETE FROM attendance_reports;
DELETE FROM announcements;
DELETE FROM reviews;
DELETE FROM attendances;
DELETE FROM registrations;
DELETE FROM events;
DELETE FROM club_organizer_assignments;
DELETE FROM clubs;
DELETE FROM categories;
DELETE FROM admins;
DELETE FROM club_organizers;
DELETE FROM students;
DELETE FROM users;

-- ---------------------------------------------------------------------------
-- Users (password hash = sha256('unievents'))
-- ---------------------------------------------------------------------------
INSERT INTO users (user_id, full_name, email, password_hash, is_active, role) VALUES
  (1, 'Ayna Khan',    'ayna@nu.edu.pk',   'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'STUDENT'),
  (2, 'Bilal Raza',   'bilal@nu.edu.pk',  'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'STUDENT'),
  (3, 'Cyra Ali',     'cyra@nu.edu.pk',   'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'STUDENT'),
  (4, 'Dawood Iqbal', 'dawood@nu.edu.pk', 'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'STUDENT'),
  (10, 'Hamza Tariq', 'hamza@nu.edu.pk',  'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'CLUB_ORGANIZER'),
  (11, 'Zoya Ahmed',  'zoya@nu.edu.pk',   'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'CLUB_ORGANIZER'),
  (99, 'Sara Malik',  'sara@nu.edu.pk',   'c91358ea719fbc40269577d5ae17d5537239d25d465e74f38c275f17929b3ab6', TRUE, 'ADMIN');

SELECT setval('users_user_id_seq', 99, true);

INSERT INTO students (user_id, roll_number) VALUES
  (1, '23I-0716'), (2, '23I-0888'), (3, '24I-1010'), (4, '25I-1022');

INSERT INTO club_organizers (user_id, is_president) VALUES
  (10, TRUE), (11, TRUE);

INSERT INTO admins (user_id, department) VALUES
  (99, 'Student Affairs');

-- ---------------------------------------------------------------------------
-- Clubs, categories
-- ---------------------------------------------------------------------------
INSERT INTO clubs (club_id, name, description, is_active, created_by) VALUES
  (1, 'Coding Society', 'Competitive programming, hackathons and tech talks.', TRUE, 99),
  (2, 'Adventure Club', 'Trekking, camping and outdoor leadership.', TRUE, 99);

SELECT setval('clubs_club_id_seq', 2, true);

INSERT INTO club_organizer_assignments (club_id, organizer_id) VALUES
  (1, 10),   -- Hamza runs Coding Society
  (2, 11);   -- Zoya runs Adventure Club

INSERT INTO categories (category_id, name) VALUES
  (1, 'Tech'), (2, 'Cultural'), (3, 'Sports'), (4, 'Workshop');

SELECT setval('categories_category_id_seq', 4, true);

-- ---------------------------------------------------------------------------
-- Events (dates: pick dates in the near future relative to demo day)
--   Event 1: capacity 2 with 2 registrations → full (waitlist demo, UC2)
--   Event 2: normal capacity, published (registration/check-in/review demo)
--   Event 3: DRAFT (create/publish demo, UC6/UC7)
--   Event 4: COMPLETED with attendance + review history (UC5/UC10 demo)
-- ---------------------------------------------------------------------------
INSERT INTO events (event_id, club_id, category_id, title, description,
                    start_date_time, end_date_time, venue, capacity, status, check_in_code) VALUES
  (1, 1, 1, 'Hackathon 101',
   'Overnight build sprint: form teams, pick a problem, ship a prototype by morning.',
   NOW() + INTERVAL '7 days 09:00', NOW() + INTERVAL '7 days 21:00', 'Auditorium A', 2,
   'PUBLISHED', 'HACK12'),
  (2, 1, 4, 'Git & GitHub Workshop',
   'Hands-on version control: branching, pull requests and resolving conflicts.',
   NOW() + INTERVAL '3 days 14:00', NOW() + INTERVAL '3 days 16:00', 'Lab 3-14', 40,
   'PUBLISHED', 'GIT99'),
  (3, 1, 1, 'AI Fireside Chat',
   'Panel with faculty and industry guests on applied AI careers.',
   NOW() + INTERVAL '14 days 16:00', NOW() + INTERVAL '14 days 18:00', 'Seminar Hall', 60,
   'DRAFT', 'AICHAT'),
  (4, 1, 1, 'Intro to Kotlin',
   'Kotlin fundamentals for Java developers (completed past event).',
   (CURRENT_DATE - INTERVAL '10 days') + TIME '10:00',
   (CURRENT_DATE - INTERVAL '10 days') + TIME '12:00',
   'Lab 3-01', 3, 'COMPLETED', 'KOT001');

SELECT setval('events_event_id_seq', 4, true);

-- ---------------------------------------------------------------------------
-- Registrations
--   Event 1 (capacity 2): Ayna + Dawood REGISTERED → full; Bilal WAITLISTED #1
--   Event 2: Ayna REGISTERED (check-in + review demo)
--   Event 4 (completed): Ayna ATTENDED, Dawood ATTENDED (via attendances)
-- ---------------------------------------------------------------------------
INSERT INTO registrations (registration_id, event_id, student_id, status, waitlist_position) VALUES
  (1, 1, 1, 'REGISTERED', NULL),   -- Ayna → Event 1
  (2, 1, 4, 'REGISTERED', NULL),   -- Dawood → Event 1 (fills capacity)
  (3, 1, 2, 'WAITLISTED', 1),      -- Bilal → Event 1 waitlist
  (4, 2, 1, 'REGISTERED', NULL),   -- Ayna → Event 2
  (5, 4, 1, 'ATTENDED', NULL),     -- Ayna → Event 4 (past, attended)
  (6, 4, 4, 'ATTENDED', NULL);     -- Dawood → Event 4 (past, attended)

SELECT setval('registrations_registration_id_seq', 6, true);

-- ---------------------------------------------------------------------------
-- Attendance (Event 4: two self check-ins with CODE)
-- ---------------------------------------------------------------------------
INSERT INTO attendances (attendance_id, registration_id, method) VALUES
  (1, 5, 'CODE'),
  (2, 6, 'CODE');

SELECT setval('attendances_attendance_id_seq', 2, true);

-- ---------------------------------------------------------------------------
-- Reviews (Event 4)
-- ---------------------------------------------------------------------------
INSERT INTO reviews (event_id, student_id, rating, comment) VALUES
  (4, 1, 5, 'Crash course was packed with practical exercises.'),
  (4, 4, 4, 'Wanted more time for the labs.');

-- ---------------------------------------------------------------------------
-- One open conflict for UC12: Dawood double-booked between Event 1 and a
-- cancelled-then-rebooked history. Kept simple: OVERBOOKING on Event 1.
-- ---------------------------------------------------------------------------
INSERT INTO registration_conflicts (conflict_id, event_id, type, status) VALUES
  (1, 1, 'DOUBLE_BOOKING', 'OPEN');

SELECT setval('registration_conflicts_conflict_id_seq', 1, true);

INSERT INTO conflict_registrations (conflict_id, registration_id) VALUES
  (1, 2);   -- concerns Dawood's registration on Event 1

COMMIT;
