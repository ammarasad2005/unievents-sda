package unievents.application;

import unievents.domain.Admin;
import unievents.domain.Club;
import unievents.domain.ClubOrganizer;
import unievents.infrastructure.persistence.DataHandler;

import java.util.List;

/**
 * Application controller for UC11 (Create Club, SD-11).
 *
 * High Cohesion: club administration only — event management and conflict
 * resolution live in their own controllers.
 */
public class ClubController {

    private final DataHandler data;
    private final Admin admin;              // actor of UC11

    public ClubController(Admin admin, DataHandler data) {
        this.admin = admin;
        this.data = data;
    }

    /**
     * Creates a club; the optional president becomes its first organizer
     * (SD-11: findOrganizerById → createClub → addOrganizer → save).
     *
     * @param presidentId optional organizer user id
     */
    public Club createClub(String name, String description, Integer presidentId) {
        ClubOrganizer president = null;
        if (presidentId != null) {
            president = data.findOrganizerById(presidentId).orElseThrow(
                    () -> new IllegalStateException("Organizer " + presidentId + " not found."));
        }
        Club club = admin.createClub(name, description, president);   // domain
        data.saveClub(club);
        return club;
    }

    /** Organizers available to be assigned as president (SD-11 helper). */
    public List<ClubOrganizer> getOrganizers() {
        return data.findAllOrganizers();
    }
}
