package unievents.domain;

import unievents.shared.ConflictStatus;
import unievents.shared.ConflictType;
import unievents.shared.ResolutionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An anomaly in a student's registration history that an admin must resolve
 * (UC12). Concerns 1..* registrations (conflict_registrations link table).
 */
public class RegistrationConflict {

    private Integer conflictId;         // null until persisted
    private final Event event;
    private final ConflictType type;
    private ConflictStatus status;
    private final LocalDateTime detectedAt;
    private ResolutionType resolution;
    private String reason;
    private LocalDateTime resolvedAt;
    private Admin resolvedBy;
    private final List<Registration> concernedRegistrations = new ArrayList<>();

    /** Domain constructor (a conflict is detected on an event). */
    public RegistrationConflict(Event event, ConflictType type) {
        this(null, event, type, ConflictStatus.OPEN, LocalDateTime.now(), null, null, null, null);
    }

    /** Full constructor; also used for rehydration by the persistence layer. */
    public RegistrationConflict(Integer conflictId, Event event, ConflictType type, ConflictStatus status,
                                LocalDateTime detectedAt, ResolutionType resolution, String reason,
                                LocalDateTime resolvedAt, Admin resolvedBy) {
        this.event = event;
        this.type = type;
        this.status = status;
        this.detectedAt = detectedAt == null ? LocalDateTime.now() : detectedAt;
        this.resolution = resolution;
        this.reason = reason;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.conflictId = conflictId;
    }

    public Integer getConflictId() {
        return conflictId;
    }

    public Event getEvent() {
        return event;
    }

    public ConflictType getType() {
        return type;
    }

    public ConflictStatus getStatus() {
        return status;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public ResolutionType getResolution() {
        return resolution;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public Admin getResolvedBy() {
        return resolvedBy;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int conflictId) {
        this.conflictId = conflictId;
    }

    /** Association "Conflict concerns 1..* Registration". */
    public void addConcernedRegistration(Registration registration) {
        if (registration != null && !concernedRegistrations.contains(registration)) {
            concernedRegistrations.add(registration);
        }
    }

    public List<Registration> getConcernedRegistrations() {
        return Collections.unmodifiableList(concernedRegistrations);
    }

    /** Rehydration hook for the concerned-registrations link. */
    public void loadConcernedRegistrations(List<Registration> loaded) {
        concernedRegistrations.clear();
        if (loaded != null) {
            concernedRegistrations.addAll(loaded);
        }
    }

    // ------------------------------------------------------------------
    // UC12: Resolution
    // ------------------------------------------------------------------

    /**
     * Applies the admin's resolution to every concerned registration, then
     * marks the conflict RESOLVED (SD-12 resolve).
     *
     * @return the new conflict status (RESOLVED)
     */
    public ConflictStatus resolve(Admin admin, ResolutionType resolution, String reason) {
        if (admin == null) {
            throw new IllegalArgumentException("An admin is required to resolve a conflict.");
        }
        if (resolution == null) {
            throw new IllegalArgumentException("A resolution is required.");
        }
        if (status == ConflictStatus.RESOLVED) {
            throw new IllegalStateException("This conflict is already resolved.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to resolve a conflict.");
        }
        for (Registration registration : concernedRegistrations) {
            registration.applyResolution(resolution);
        }
        this.resolution = resolution;
        this.reason = reason.trim();
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = admin;
        this.status = ConflictStatus.RESOLVED;
        return status;
    }
}
