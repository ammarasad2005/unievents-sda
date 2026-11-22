package unievents.shared;

/** Administrative decision applied when resolving a registration conflict. */
public enum ResolutionType {
    KEEP_REGISTERED, MOVE_TO_WAITLIST, CANCEL_REGISTRATION, INCREASE_CAPACITY, OTHER
}
