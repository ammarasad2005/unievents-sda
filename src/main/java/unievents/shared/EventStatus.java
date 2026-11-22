package unievents.shared;

/** Lifecycle of an {@code Event}. Transitions are validated in the domain. */
public enum EventStatus {
    DRAFT, PUBLISHED, CANCELLED, COMPLETED
}
