# UniEvents

**University Event & Club Management — JavaFX desktop application**
FAST-NUCES | Software Design & Analysis | Section E
Members: Ammar Asad (23I-0716), Abdullah Faraz (25I-1022)

A JavaFX desktop app implementing all 12 use cases from Deliverable 5, layered
exactly as the design class diagram and sequence diagrams SD-01..SD-12 specify,
persisting to **Supabase (PostgreSQL) over JDBC**.

---

## Stack

| Layer | Technology |
|-------|------------|
| UI | JavaFX 21 (pure Java views, no FXML), Java 17+ |
| Business logic | Plain Java domain + application controllers |
| Database | Supabase (PostgreSQL) via JDBC (`org.postgresql:postgresql`) |
| Build | Maven (`mvn javafx:run`) |

---

## Project layout (mandatory packages)

```
unievents/
  ui/                     JavaFX views + role homes + navigation shell
  domain/                 Conceptual classes from the design class diagram
  application/            StudentController, ManagementController, ClubController,
                          ConflictController, Session (UI talks ONLY to these)
  infrastructure/
    persistence/          DataHandler (interface), SupabaseHandler (JDBC),
                          DatabaseController (singleton)
  shared/                 Enums + DTOs (EventSummary, EventDetails, ...)

src/main/resources/db/
  schema.sql              PostgreSQL schema (enums, tables, FKs, checks)
  seed.sql                Demo data (accounts, clubs, events, one conflict)
```

**Layer rules enforced:** UI → application controllers only; controllers
orchestrate while domain objects own the business rules; persistence is reached
only through the `DataHandler` interface (Protected Variation). No UI SQL, no
god classes.

---

## Setup

### 1. Prerequisites

- JDK 17 or newer (built and tested with Temurin 21)
- Maven 3.8+
- A Supabase project (free tier works)

### 2. Create the database

Open **Supabase Dashboard → SQL Editor** and run, in order:

1. `src/main/resources/db/schema.sql`
2. `src/main/resources/db/seed.sql`

Both scripts are transactional and the seed wipes demo tables first, so they
are safe to re-run.

### 3. Configure the connection

Pick **one** of:

**a) Environment variables** (recommended; nothing secret lands in the repo)

```bash
export UNIEVENTS_DB_URL="jdbc:postgresql://db.<PROJECT_REF>.supabase.co:5432/postgres"
export UNIEVENTS_DB_USER="postgres"
export UNIEVENTS_DB_PASSWORD="<DATABASE_PASSWORD>"
```

Use the **session pooler** URL if port 5432 is blocked by your network:

```
jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
```

**b) Properties file**

```bash
cp src/main/resources/unievents.properties.example src/main/resources/unievents.properties
# edit db.url / db.user / db.password
```

Settings precedence: env vars → system properties (`-Dunievents.db.url=...`)
→ `unievents.properties` on the classpath.

### 4. Run

```bash
mvn javafx:run
```

Login with any demo account (password for all: `unievents`):

| Role | Email |
|------|-------|
| Student | `ayna@nu.edu.pk` |
| Student | `bilal@nu.edu.pk` |
| Student | `cyra@nu.edu.pk` |
| Club Organizer (Coding Society president) | `hamza@nu.edu.pk` |
| Club Organizer (Adventure Club president) | `zoya@nu.edu.pk` |
| Admin (Student Affairs) | `sara@nu.edu.pk` |

### 5. Tests

```bash
mvn test          # 22 domain unit tests (no DB required)
```

---

## Demo script (≈10 minutes, covers all 12 use cases)

Seed state: event 1 "Hackathon 101" is at capacity (2/2) with Bilal waitlisted;
event 2 "Git & GitHub Workshop" is published with seats; event 3 is a draft;
event 4 is completed with attendance history; one conflict is open.

| # | Min | Actor (login) | Steps | Shows |
|---|-----|---------------|-------|-------|
| 1 | 1.0 | Student `ayna@nu.edu.pk` | **Browse Events**: list loads with remaining seats; filter by category *Workshop*; open details of event 2 | UC1 |
| 2 | 1.5 | Student `ayna@nu.edu.pk` | **Browse Events → Hackathon 101 → Register** → message "WAITLISTED at position 2" (event is full) | UC2 |
| 3 | 0.5 | Student `cyra@nu.edu.pk` | **My Registrations** → cancel the waitlisted entry; Bilal keeps position 1 | UC3 |
| 4 | 1.0 | Student `ayna@nu.edu.pk` | **My Registrations** → cancel the REGISTERED seat on Hackathon 101 → Bilal is auto-promoted (login as `bilal@nu.edu.pk` to show REGISTERED + the promotion notification) | UC3 |
| 5 | 1.0 | Student `bilal@nu.edu.pk` | **Check In**: enter event 1 + code `HACK12` → attendance recorded, status ATTENDED. Try a wrong code first to show the error message | UC4 |
| 6 | 1.0 | Student `bilal@nu.edu.pk` | **Review Event**: pick Hackathon 101 → eligibility check passes → 5★ + comment → average updates. Try reviewing event 2 (not attended) to show rejection | UC5 |
| 7 | 1.5 | Organizer `hamza@nu.edu.pk` | **Create Event**: new draft "Portfolio Review Day". Then edit it (capacity up). Try capacity 1 on Hackathon 101 → rejected (below registered count) | UC6 |
| 8 | 1.0 | Organizer `hamza@nu.edu.pk` | **Change Status**: publish the new draft; try an invalid transition (PUBLISHED → DRAFT) to show the rule; cancel it with a reason | UC7 |
| 9 | 1.0 | Organizer `hamza@nu.edu.pk` | **Send Announcement**: pick Hackathon 101 → audience counts shown → send to BOTH. Login as a student to show the in-app notification | UC8 |
| 10 | 1.0 | Organizer `hamza@nu.edu.pk` | **Attendance Sheet**: load event 2 (code GIT99 shown), tick a present row → Mark attended (MANUAL) | UC9 |
| 11 | 0.5 | Organizer `hamza@nu.edu.pk` | **Attendance Report**: generate for event 2 → registered/attended counts + participant list; report row saved | UC10 |
| 12 | 1.0 | Admin `sara@nu.edu.pk` | **Create Club**: "Photography Society" with Zoya as president | UC11 |
| 13 | 1.0 | Admin `sara@nu.edu.pk` | **Resolve Conflicts**: open the DOUBLE_BOOKING conflict → details → apply KEEP_REGISTERED with a reason → conflict RESOLVED and gone from the list | UC12 |

Re-seed any time to reset the demo: run `seed.sql` again in the SQL editor.

---

## Design ↔ code traceability

Class and method names follow the Deliverable 5 design class diagram and
SD-01..SD-12:

- Domain: `User` (abstract) ← `Student` / `ClubOrganizer` / `Admin`; `Club`,
  `Category`, `Event`, `Registration`, `Attendance`, `Review`, `Announcement`,
  `Notification`, `AttendanceReport`, `RegistrationConflict` — with the
  designed methods (`Event.register`, `Event.promoteNextWaitlisted`,
  `Registration.cancel`, `Registration.markAttended`,
  `Registration.applyResolution`, `Event.generateReport`,
  `Admin.createClub`, `RegistrationConflict.resolve`, ...).
- Controllers: `StudentController` (SD-01..05), `ManagementController`
  (SD-06..10), `ClubController` (SD-11), `ConflictController` (SD-12) with the
  designed operation names.
- Persistence: `DataHandler` interface; `DatabaseController` singleton with
  `getInstance/getConnection/beginTransaction/commit/rollback/closeConnection`.
- DTOs: `EventSummary`, `EventDetails`, `RegistrationSummary`,
  `AttendanceRow`, `AudienceCounts`, `Participant`, `ConflictSummary`,
  `ConflictDetails` with the SSD field lists.

### Documented deltas from Deliverable 5

1. **`SupabaseHandler` instead of `MySQLHandler`** — same `DataHandler`
   contract; PostgreSQL JDBC instead of MySQL (per kickoff decision).
2. **Check-in method enum `CODE`** — SD-04 labels the self-service path
   `SELF_CODE`; the shared enum (and the DB enum type `check_in_method`) uses
   `CODE` for both self and manual paths.
3. **Conflict `save` vs `update`** — SD-12 shows one `save(conflict)`; the
   interface exposes `saveConflict` (insert) and `updateConflict` (state
   change after resolution) because the schema keys conflicts by id.
4. **Small additions the design implies but does not name**: transaction
   helpers on `DataHandler` (default methods over the `DatabaseController`
   singleton), `findAllOrganizers`/`findAllCategories`/`findAllClubs` lookups
   for the form screens, and a notification-inbox query.

---

## OOP & GRASP demonstrated

| Principle | Where |
|-----------|-------|
| Encapsulation | Private fields everywhere; status changes only through domain methods (`changeStatus`, `cancel`, `promote`), never setters |
| Abstraction | `abstract User` with abstract `getRole()`; `DataHandler` interface |
| Inheritance | `Student`, `ClubOrganizer`, `Admin` extend `User` |
| Polymorphism | `getRole()` dispatched through `User` references; notifications received via the `User` supertype; **no `instanceof` / type-switch in any business logic** (role routing happens once in the UI navigation through `getRole()`) |
| Controller | `StudentController` / `ManagementController` / `ClubController` / `ConflictController`; UI never touches the DAO |
| Information Expert | `Event.register`, `Event.getRemainingSeats`, `Registration.cancel`, `Event.generateReport`, `RegistrationConflict.resolve` |
| Creator | `Event` creates `Registration`; `Registration` creates `Attendance`; `Club` creates `Event` |
| Low Coupling | UI → controllers → domain/`DataHandler`; persistence swappable behind the interface |
| High Cohesion | Student vs management vs club-administration vs conflict flows in separate controllers |
| Protected Variation | `DataHandler` interface; `SupabaseHandler` replaceable |
| Singleton | `DatabaseController` (double-checked locking) |

---

## Non-functional behaviour

- Invalid input never crashes the app: every screen validates and shows
  messages (capacity > 0, rating 1–5, required fields, duplicate registrations,
  invalid status transitions, invalid check-in codes).
- Register/cancel/promote, attendance marking and conflict resolution run in
  JDBC transactions.
- `SQLException` is wrapped in `IllegalStateException` at the persistence
  boundary — nothing unchecked leaks to the UI.

## Verification performed

- `mvn test` — 22 domain unit tests (registration, waitlist, promotion,
  check-in, review eligibility, status machine, conflict resolution,
  polymorphism).
- `EndToEndMain` — 38 checks driving all 12 use cases through the real
  `SupabaseHandler` against a PostgreSQL instance loaded with
  `schema.sql` + `seed.sql`.
- `UiFlowTest` — 14 headless UI checks (JavaFX/Monocle): login as each role,
  home screens, table data for browse/my-events/conflicts.
