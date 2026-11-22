package unievents.shared;

import java.time.LocalDateTime;

/**
 * Read-only projection of an event for the detail screen (SSD-01 / SSD-02 / SSD-06).
 * Carries everything the student needs before registering (including the average
 * rating) and everything the organizer's edit form needs (including categoryId).
 */
public record EventDetails(
        int eventId,
        String title,
        String description,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String venue,
        int capacity,
        int remainingSeats,
        int categoryId,
        String categoryName,
        String clubName,
        double averageRating) {
}
