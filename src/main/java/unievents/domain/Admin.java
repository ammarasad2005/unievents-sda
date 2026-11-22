package unievents.domain;

/** Admin actor: creates clubs, resolves registration conflicts. */
public class Admin extends User {

    private final String department;

    public Admin(int userId, String fullName, String email, String passwordHash, boolean active,
                 String department) {
        super(userId, fullName, email, passwordHash, active);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }

    /**
     * Creates a club; the president (optional, SSD-11 presidentId [0..1]) is
     * immediately added as its first organizer (SD-11).
     */
    public Club createClub(String name, String description, ClubOrganizer president) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Club name is required.");
        }
        Club club = new Club(name.trim(), description);
        if (president != null) {
            club.addOrganizer(president);
            president.assignAsPresident(club);
        }
        return club;
    }
}
