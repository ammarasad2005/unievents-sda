package unievents.shared;

import java.time.LocalDateTime;

/** Read-only projection of the logged-in student's own registration (SSD-03). */
public record RegistrationSummary(
        int registrationId,
        int eventId,
        String eventTitle,
        LocalDateTime startDateTime,
        String venue,
        RegistrationStatus status,
        Integer waitlistPosition) {
}
