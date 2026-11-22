package unievents.shared;

import java.time.LocalDateTime;

/**
 * Read-only projection of a published event for list screens (SSD-01 / SSD-06).
 * Factory-style constructor keeps the UI decoupled from domain internals.
 */
public record EventSummary(
        int eventId,
        String title,
        String clubName,
        LocalDateTime startDateTime,
        String venue,
        int remainingSeats,
        EventStatus status) {
}
