package unievents.application;

import unievents.domain.Admin;
import unievents.domain.Notification;
import unievents.domain.Registration;
import unievents.domain.RegistrationConflict;
import unievents.infrastructure.persistence.DataHandler;
import unievents.shared.ConflictDetails;
import unievents.shared.ConflictStatus;
import unievents.shared.ConflictSummary;
import unievents.shared.ResolutionType;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Application controller for UC12 (Resolve Registration Conflict, SD-12).
 */
public class ConflictController {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final DataHandler data;
    private final Admin admin;              // actor of UC12

    public ConflictController(Admin admin, DataHandler data) {
        this.admin = admin;
        this.data = data;
    }

    /** Open conflicts as list rows (SD-12). */
    public List<ConflictSummary> getOpenConflicts() {
        return data.findOpenConflicts().stream()
                .map(c -> new ConflictSummary(
                        c.getConflictId(),
                        c.getEvent().getTitle(),
                        c.getConcernedRegistrations().isEmpty() ? "—"
                                : c.getConcernedRegistrations().get(0).getStudent().getFullName(),
                        c.getType()))
                .toList();
    }

    /** Full detail: history, capacity, waitlist (SD-12 getConflictDetails). */
    public ConflictDetails getConflictDetails(int conflictId) {
        RegistrationConflict conflict = data.findConflictById(conflictId);
        return new ConflictDetails(
                conflict.getConflictId(),
                conflict.getEvent().getTitle(),
                conflict.getType(),
                conflict.getStatus(),
                conflict.getConcernedRegistrations(),
                conflict.getEvent().getCapacity(),
                conflict.getEvent().getWaitlist());
    }

    /**
     * Applies the admin's resolution and marks the conflict RESOLVED (SD-12).
     * Registration changes and the conflict update run in one transaction.
     */
    public ConflictStatus resolveConflict(int conflictId, ResolutionType resolution, String reason) {
        RegistrationConflict conflict = data.findConflictById(conflictId);
        try {
            data.beginTransaction();
            ConflictStatus status = conflict.resolve(admin, resolution, reason);   // domain applies
            data.saveConflictedRegistrations(conflict.getConcernedRegistrations());
            data.updateConflict(conflict);
            data.commit();
            persistNotifications();
            return status;
        } catch (RuntimeException e) {
            data.rollback();
            throw e;
        }
    }

    private void persistNotifications() {
        List<Notification> pending = admin.takePendingNotifications();
        if (!pending.isEmpty()) {
            data.saveNotifications(pending);
        }
    }
}
