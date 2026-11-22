-- UniEvents — PostgreSQL / Supabase schema
-- FAST-NUCES SDA | Section E | 23I-0716 · 25I-1022
-- Aligns with Deliverable 5 design class diagram

BEGIN;

-- ---------------------------------------------------------------------------
-- Enums (PostgreSQL)
-- ---------------------------------------------------------------------------
CREATE TYPE event_status AS ENUM (
  'DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'
);

CREATE TYPE registration_status AS ENUM (
  'REGISTERED', 'WAITLISTED', 'CANCELLED', 'ATTENDED'
);

CREATE TYPE check_in_method AS ENUM (
  'CODE', 'MANUAL'
);

CREATE TYPE notification_type AS ENUM (
  'REGISTRATION', 'WAITLIST', 'PROMOTION', 'EVENT_CANCELLED', 'ANNOUNCEMENT'
);

CREATE TYPE audience_type AS ENUM (
  'REGISTERED', 'WAITLISTED', 'BOTH'
);

CREATE TYPE conflict_type AS ENUM (
  'OVERBOOKING', 'DOUBLE_BOOKING', 'CAPACITY_BREACH', 'OTHER'
);

CREATE TYPE conflict_status AS ENUM (
  'OPEN', 'RESOLVED', 'DISMISSED'
);

CREATE TYPE resolution_type AS ENUM (
  'KEEP_REGISTERED', 'MOVE_TO_WAITLIST', 'CANCEL_REGISTRATION', 'INCREASE_CAPACITY', 'OTHER'
);

CREATE TYPE user_role AS ENUM (
  'STUDENT', 'CLUB_ORGANIZER', 'ADMIN'
);

-- ---------------------------------------------------------------------------
-- Users (base) + role subtypes
-- ---------------------------------------------------------------------------
CREATE TABLE users (
  user_id       SERIAL PRIMARY KEY,
  full_name     VARCHAR(150) NOT NULL,
  email         VARCHAR(180) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  role          user_role NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE students (
  user_id     INT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  roll_number VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE club_organizers (
  user_id      INT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  is_president BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE admins (
  user_id    INT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  department VARCHAR(100)
);

-- ---------------------------------------------------------------------------
-- Clubs, categories, events
-- ---------------------------------------------------------------------------
CREATE TABLE clubs (
  club_id     SERIAL PRIMARY KEY,
  name        VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_by  INT REFERENCES admins(user_id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Club is run by 1..* organizers
CREATE TABLE club_organizer_assignments (
  club_id      INT NOT NULL REFERENCES clubs(club_id) ON DELETE CASCADE,
  organizer_id INT NOT NULL REFERENCES club_organizers(user_id) ON DELETE CASCADE,
  assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (club_id, organizer_id)
);

CREATE TABLE categories (
  category_id SERIAL PRIMARY KEY,
  name        VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE events (
  event_id        SERIAL PRIMARY KEY,
  club_id         INT NOT NULL REFERENCES clubs(club_id) ON DELETE CASCADE,
  category_id     INT NOT NULL REFERENCES categories(category_id),
  title           VARCHAR(200) NOT NULL,
  description     TEXT,
  start_date_time TIMESTAMPTZ NOT NULL,
  end_date_time   TIMESTAMPTZ NOT NULL,
  venue           VARCHAR(200) NOT NULL,
  capacity        INT NOT NULL CHECK (capacity > 0),
  status          event_status NOT NULL DEFAULT 'DRAFT',
  check_in_code   VARCHAR(32) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT events_time_chk CHECK (end_date_time > start_date_time)
);

CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_club ON events(club_id);
CREATE INDEX idx_events_category ON events(category_id);
CREATE INDEX idx_events_start ON events(start_date_time);

-- ---------------------------------------------------------------------------
-- Registrations & attendance
-- ---------------------------------------------------------------------------
CREATE TABLE registrations (
  registration_id   SERIAL PRIMARY KEY,
  event_id          INT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  student_id        INT NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  registered_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status            registration_status NOT NULL,
  waitlist_position INT,
  CONSTRAINT registrations_unique_student_event UNIQUE (event_id, student_id),
  CONSTRAINT registrations_waitlist_chk CHECK (
    (status = 'WAITLISTED' AND waitlist_position IS NOT NULL AND waitlist_position > 0)
    OR (status <> 'WAITLISTED' AND waitlist_position IS NULL)
  )
);

CREATE INDEX idx_registrations_event ON registrations(event_id);
CREATE INDEX idx_registrations_student ON registrations(student_id);
CREATE INDEX idx_registrations_status ON registrations(status);

CREATE TABLE attendances (
  attendance_id   SERIAL PRIMARY KEY,
  registration_id INT NOT NULL UNIQUE REFERENCES registrations(registration_id) ON DELETE CASCADE,
  checked_in_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  method          check_in_method NOT NULL
);

-- ---------------------------------------------------------------------------
-- Reviews, announcements, notifications
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
  review_id  SERIAL PRIMARY KEY,
  event_id   INT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  student_id INT NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  rating     INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment    TEXT,
  posted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT reviews_unique_student_event UNIQUE (event_id, student_id)
);

CREATE TABLE announcements (
  announcement_id SERIAL PRIMARY KEY,
  event_id        INT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  organizer_id    INT NOT NULL REFERENCES club_organizers(user_id),
  subject         VARCHAR(200) NOT NULL,
  message         TEXT NOT NULL,
  audience        audience_type NOT NULL,
  sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
  notification_id SERIAL PRIMARY KEY,
  user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  message         TEXT NOT NULL,
  type            notification_type NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_read         BOOLEAN NOT NULL DEFAULT FALSE,
  announcement_id INT REFERENCES announcements(announcement_id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_user ON notifications(user_id);

-- ---------------------------------------------------------------------------
-- Reports & conflicts
-- ---------------------------------------------------------------------------
CREATE TABLE attendance_reports (
  report_id        SERIAL PRIMARY KEY,
  event_id         INT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  organizer_id     INT NOT NULL REFERENCES club_organizers(user_id),
  generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  registered_count INT NOT NULL DEFAULT 0,
  attended_count   INT NOT NULL DEFAULT 0
);

CREATE TABLE registration_conflicts (
  conflict_id  SERIAL PRIMARY KEY,
  event_id     INT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  type         conflict_type NOT NULL,
  status       conflict_status NOT NULL DEFAULT 'OPEN',
  detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolution   resolution_type,
  reason       TEXT,
  resolved_at  TIMESTAMPTZ,
  resolved_by  INT REFERENCES admins(user_id)
);

-- Conflict concerns 1..* registrations
CREATE TABLE conflict_registrations (
  conflict_id     INT NOT NULL REFERENCES registration_conflicts(conflict_id) ON DELETE CASCADE,
  registration_id INT NOT NULL REFERENCES registrations(registration_id) ON DELETE CASCADE,
  PRIMARY KEY (conflict_id, registration_id)
);

COMMIT;
