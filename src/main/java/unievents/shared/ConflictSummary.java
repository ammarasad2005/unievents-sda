package unievents.shared;

/** One line of the admin's open-conflict list (SSD-12). */
public record ConflictSummary(
        int conflictId,
        String eventTitle,
        String fullName,
        ConflictType type) {
}
