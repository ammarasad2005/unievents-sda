package unievents.shared;

/**
 * How an attendance record was produced.
 * SD-04 labels the self-service path {@code SELF_CODE}; the shared enum (and the
 * database enum type) uses {@code CODE} — documented as a naming delta in the README.
 */
public enum CheckInMethod {
    CODE, MANUAL
}
