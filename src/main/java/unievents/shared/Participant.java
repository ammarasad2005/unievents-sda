package unievents.shared;

/** One participant line of an attendance report (SSD-10). */
public record Participant(
        String fullName,
        String rollNumber,
        RegistrationStatus status) {
}
