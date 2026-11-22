package unievents.domain;

import unievents.shared.AudienceType;

import java.util.ArrayList;
import java.util.List;

/** Club organizer actor: runs club events (create/edit/publish/cancel, attendance, reports). */
public class ClubOrganizer extends User {

    private boolean president;

    /** Clubs this organizer belongs to (usually one; primary club = first). */
    private final List<Club> clubs = new ArrayList<>();

    public ClubOrganizer(int userId, String fullName, String email, String passwordHash, boolean active,
                         boolean president) {
        super(userId, fullName, email, passwordHash, active);
        this.president = president;
    }

    public boolean isPresident() {
        return president;
    }

    @Override
    public String getRole() {
        return "CLUB_ORGANIZER";
    }

    /** Primary club (design class diagram: getClub(): Club). */
    public Club getClub() {
        return clubs.isEmpty() ? null : clubs.get(0);
    }

    public List<Club> getClubs() {
        return new ArrayList<>(clubs);
    }

    /** Events across the organizer's club(s) — entry list for UC6–UC10. */
    public List<Event> getMyEvents() {
        List<Event> events = new ArrayList<>();
        for (Club club : clubs) {
            events.addAll(club.getEvents());
        }
        return events;
    }

    /**
     * Creates an announcement for an event and notifies every recipient
     * in the chosen audience (SD-08: Announcement.notifyRecipients).
     */
    public Announcement createAnnouncement(Event event, String subject, String message, AudienceType audience) {
        if (event == null) {
            throw new IllegalArgumentException("An event is required.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Announcement subject is required.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Announcement message is required.");
        }
        Announcement announcement = new Announcement(event, this, subject.trim(), message.trim(), audience);
        List<Student> recipients = event.getAudience(audience);
        announcement.notifyRecipients(recipients);
        return announcement;
    }

    /** Marks this organizer as president of the given club (SD-11). */
    public void assignAsPresident(Club club) {
        this.president = true;
        linkClub(club);
    }

    /** Rehydration hook: replaces the club set (persistence layer only). */
    public void loadClubs(List<Club> loaded) {
        clubs.clear();
        if (loaded != null) {
            loaded.forEach(this::linkClub);
        }
    }

    /** Called by {@link Club#addOrganizer(ClubOrganizer)} to keep both sides in sync. */
    void linkClub(Club club) {
        if (club != null && !clubs.contains(club)) {
            clubs.add(club);
        }
    }
}
