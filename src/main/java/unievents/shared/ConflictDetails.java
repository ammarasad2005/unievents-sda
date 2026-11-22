package unievents.shared;

import unievents.domain.Registration;

import java.util.List;

/**
 * Admin-facing detail of a registration conflict (SSD-12).
 * Holds domain references because the resolution flow needs to call
 * {@code Registration.applyResolution(...)} on each concerned registration.
 */
public record ConflictDetails(
        int conflictId,
        String eventTitle,
        ConflictType type,
        ConflictStatus status,
        List<Registration> registrationHistory,
        int capacity,
        List<Registration> waitlist) {
}
