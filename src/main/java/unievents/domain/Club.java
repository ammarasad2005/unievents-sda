package unievents.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A university club that hosts events and is run by one or more organizers.
 *
 * GRASP Information Expert for "which events belong to this club" and
 * Creator for {@code Event} (SD-06: club.createEvent(...)).
 */
public class Club {

    private Integer clubId;              // null until persisted
    private String name;
    private String description;
    private boolean active;
    private final List<ClubOrganizer> organizers = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();

    /** New-club constructor (Admin.createClub uses this). */
    public Club(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Club name is required.");
        }
        this.name = name.trim();
        this.description = description;
        this.active = true;
    }

    /** Rehydration constructor used by the persistence layer. */
    public Club(Integer clubId, String name, String description, boolean active) {
        this(name, description);
        this.clubId = clubId;
        this.active = active;
    }

    public Integer getClubId() {
        return clubId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int clubId) {
        this.clubId = clubId;
    }

    /** Association "Club is run by ClubOrganizer 1 → 1..*". */
    public void addOrganizer(ClubOrganizer organizer) {
        if (organizer == null) {
            throw new IllegalArgumentException("Organizer cannot be null.");
        }
        if (!organizers.contains(organizer)) {
            organizers.add(organizer);
            organizer.linkClub(this);
        }
    }

    public List<ClubOrganizer> getOrganizers() {
        return Collections.unmodifiableList(organizers);
    }

    /** Rehydration hook for events (persistence layer only). */
    public void loadEvents(List<Event> loaded) {
        events.clear();
        if (loaded != null) {
            events.addAll(loaded);
        }
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Creator (SD-06): the club creates its own events as DRAFT.
     * Validation of individual fields happens inside {@link Event}.
     */
    public Event createEvent(String title, String description,
                             java.time.LocalDateTime startDateTime, java.time.LocalDateTime endDateTime,
                             String venue, int capacity, Category category) {
        Event event = new Event(this, title, description, startDateTime, endDateTime,
                venue, capacity, category);
        events.add(event);
        return event;
    }

    @Override
    public String toString() {
        return name;
    }

    /** Utility used when displaying events sorted by start time. */
    public static List<Event> sortedByStart(List<Event> events) {
        List<Event> copy = new ArrayList<>(events);
        copy.sort(Comparator.comparing(Event::getStartDateTime));
        return copy;
    }
}
