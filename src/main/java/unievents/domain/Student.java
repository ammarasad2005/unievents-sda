package unievents.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Student actor: browses, registers, checks in, reviews. */
public class Student extends User {

    private final String rollNumber;

    /** Registrations of this student — rehydrated by the persistence layer (SD-03). */
    private final List<Registration> registrations = new ArrayList<>();

    public Student(int userId, String fullName, String email, String passwordHash, boolean active,
                   String rollNumber) {
        super(userId, fullName, email, passwordHash, active);
        this.rollNumber = rollNumber;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    @Override
    public String getRole() {
        return "STUDENT";
    }

    /** Rehydration hook used by the persistence layer before reading {@link #getRegistrations()}. */
    public void loadRegistrations(List<Registration> loaded) {
        registrations.clear();
        if (loaded != null) {
            registrations.addAll(loaded);
        }
    }

    public List<Registration> getRegistrations() {
        return Collections.unmodifiableList(registrations);
    }

    /** Finds one of this student's loaded registrations by id (design class diagram). */
    public Registration findRegistration(int registrationId) {
        return registrations.stream()
                .filter(r -> r.getRegistrationId() == registrationId)
                .findFirst()
                .orElse(null);
    }
}
