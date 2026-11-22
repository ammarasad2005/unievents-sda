package unievents.shared;

/** One row of the organizer's attendance sheet (SSD-09). */
public record AttendanceRow(
        int registrationId,
        String fullName,
        String rollNumber,
        boolean alreadyCheckedIn) {
}
