package unievents.domain;

import unievents.shared.AttendanceRow;
import unievents.shared.CheckInMethod;
import unievents.shared.Participant;
import unievents.shared.RegistrationStatus;
import unievents.shared.ResolutionType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A student's registration for an event (seat or waitlist entry).
 *
 * GRASP Information Expert for its own lifecycle: cancel, promote, markAttended.
 * GRASP Creator for {@link Attendance} (markAttended creates it).
 */
public class Registration {

    private Integer registrationId;     // null until persisted
    private final Student student;
    private final Event event;
    private final LocalDateTime registeredAt;
    private RegistrationStatus status;
    private Integer waitlistPosition;   // non-null only while WAITLISTED
    private Attendance attendance;      // 0..1

    /** New-registration constructor (created by Event.register). */
    Registration(Student student, Event event, RegistrationStatus status, Integer waitlistPosition) {
        this.student = student;
        this.event = event;
        this.registeredAt = LocalDateTime.now();
        this.status = status;
        this.waitlistPosition = waitlistPosition;
    }

    /** Rehydration constructor used by the persistence layer. */
    public Registration(Integer registrationId, Student student, Event event,
                        LocalDateTime registeredAt, RegistrationStatus status, Integer waitlistPosition) {
        this.registrationId = registrationId;
        this.student = student;
        this.event = event;
        this.registeredAt = registeredAt;
        this.status = status;
        this.waitlistPosition = waitlistPosition;
    }

    public Integer getRegistrationId() {
        return registrationId;
    }

    public Student getStudent() {
        return student;
    }

    public Event getEvent() {
        return event;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public int getWaitlistPosition() {
        return waitlistPosition == null ? 0 : waitlistPosition;
    }

    public Attendance getAttendance() {
        return attendance;
    }

    /** Persistence hook — attaches an already-stored attendance record (no state change). */
    public void attachAttendance(Attendance attendance) {
        this.attendance = attendance;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int registrationId) {
        this.registrationId = registrationId;
    }

    /** true while the registration holds a seat or a waitlist position. */
    public boolean isActiveRegistration() {
        return status == RegistrationStatus.REGISTERED || status == RegistrationStatus.WAITLISTED;
    }

    /** true once this registration produced an attendance record. */
    public boolean isAttended() {
        return status == RegistrationStatus.ATTENDED;
    }

    // ------------------------------------------------------------------
    // UC3: Cancel with automatic waitlist promotion
    // ------------------------------------------------------------------

    /**
     * Cancels this registration (SD-03). When a REGISTERED seat is freed and
     * the waitlist is non-empty, the event promotes its next waitlisted
     * student; when a WAITLISTED entry leaves, remaining positions close up.
     * The returned status is CANCELLED.
     */
    public RegistrationStatus cancel() {
        if (status == RegistrationStatus.CANCELLED) {
            throw new IllegalStateException("This registration is already cancelled.");
        }
        if (status == RegistrationStatus.ATTENDED) {
            throw new IllegalStateException("An attended registration cannot be cancelled.");
        }
        boolean seatFreed = (status == RegistrationStatus.REGISTERED);
        this.status = RegistrationStatus.CANCELLED;
        this.waitlistPosition = null;
        if (seatFreed) {
            event.promoteNextWaitlisted();
        } else {
            event.resequenceWaitlist();
        }
        return status;
    }

    /** Waitlist promotion (called by Event.promoteNextWaitlisted). */
    void promote() {
        if (status != RegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Only waitlisted registrations can be promoted.");
        }
        this.status = RegistrationStatus.REGISTERED;
        this.waitlistPosition = null;
    }

    /** Position update after waitlist resequencing (package-private). */
    void setWaitlistPosition(int position) {
        if (status == RegistrationStatus.WAITLISTED) {
            this.waitlistPosition = position;
        }
    }

    // ------------------------------------------------------------------
    // UC4 / UC9: Attendance
    // ------------------------------------------------------------------

    /**
     * Marks this registration attended (SD-04 self check-in / SD-09 manual).
     * Creates the Attendance record (Creator).
     *
     * @throws IllegalStateException when already attended or cancelled
     */
    public Attendance markAttended(CheckInMethod method) {
        if (status == RegistrationStatus.ATTENDED) {
            throw new IllegalStateException("Already checked in for this event.");
        }
        if (status == RegistrationStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled registration cannot be marked attended.");
        }
        if (status == RegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("A waitlisted registration has no seat to check into.");
        }
        this.attendance = new Attendance(method);
        this.status = RegistrationStatus.ATTENDED;
        return attendance;
    }

    /** Attendance row projection for the organizer's sheet (SD-09). */
    public AttendanceRow toAttendanceRow() {
        return new AttendanceRow(registrationId == null ? 0 : registrationId,
                student.getFullName(), student.getRollNumber(), isAttended());
    }

    /** Participant projection for the attendance report (SD-10). */
    public Participant toParticipant() {
        return new Participant(student.getFullName(), student.getRollNumber(), status);
    }

    // ------------------------------------------------------------------
    // UC12: Conflict resolution
    // ------------------------------------------------------------------

    /**
     * Applies an admin resolution to this registration (SD-12).
     * MOVE_TO_WAITLIST appends at the end of the waitlist;
     * CANCEL_REGISTRATION cancels without promotion; KEEP_REGISTERED and
     * INCREASE_CAPACITY leave the seat in place (capacity is changed on the
     * event by the controller after the conflict is resolved).
     */
    public void applyResolution(ResolutionType resolution) {
        if (resolution == null) {
            throw new IllegalArgumentException("A resolution is required.");
        }
        switch (resolution) {
            case KEEP_REGISTERED, INCREASE_CAPACITY -> {
                if (status == RegistrationStatus.CANCELLED) {
                    throw new IllegalStateException("A cancelled registration cannot be re-registered.");
                }
                // seat stays as-is
            }
            case MOVE_TO_WAITLIST -> {
                if (status == RegistrationStatus.REGISTERED) {
                    int nextPosition = event.getWaitlist().size() + 1;   // computed first: this registration is not yet waitlisted
                    this.status = RegistrationStatus.WAITLISTED;
                    this.waitlistPosition = nextPosition;
                } else if (status != RegistrationStatus.WAITLISTED) {
                    throw new IllegalStateException("Only seated registrations can be moved to the waitlist.");
                }
            }
            case CANCEL_REGISTRATION -> {
                if (status != RegistrationStatus.CANCELLED) {
                    this.status = RegistrationStatus.CANCELLED;
                    this.waitlistPosition = null;
                }
            }
            case OTHER -> {
                // no automatic action; admin decision recorded in reason
            }
        }
    }

    @Override
    public String toString() {
        return "Registration #" + registrationId + " " + student.getFullName()
                + " → " + event.getTitle() + " [" + status + "]";
    }
}
