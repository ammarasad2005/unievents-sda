package unievents.domain;

import unievents.shared.CheckInMethod;

import java.time.LocalDateTime;

/**
 * One check-in record; created exclusively by {@link Registration#markAttended}.
 * Association "Registration results in Attendance 1 → 0..1".
 */
public class Attendance {

    private Integer attendanceId;       // null until persisted
    private final LocalDateTime checkedInAt;
    private final CheckInMethod method;

    /** Domain constructor (package-private: only Registration creates these). */
    Attendance(CheckInMethod method) {
        this.checkedInAt = LocalDateTime.now();
        this.method = method;
    }

    /** Rehydration constructor used by the persistence layer. */
    public Attendance(Integer attendanceId, LocalDateTime checkedInAt, CheckInMethod method) {
        this.attendanceId = attendanceId;
        this.checkedInAt = checkedInAt;
        this.method = method;
    }

    public Integer getAttendanceId() {
        return attendanceId;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public CheckInMethod getMethod() {
        return method;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int attendanceId) {
        this.attendanceId = attendanceId;
    }
}
