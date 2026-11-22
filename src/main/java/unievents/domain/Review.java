package unievents.domain;

import java.time.LocalDateTime;

/**
 * A student's rating + comment for an attended event.
 * Association "Student writes Review; Event evaluates Review" (D4).
 */
public class Review {

    private Integer reviewId;           // null until persisted
    private final Student student;
    private final Event event;
    private final int rating;           // 1..5
    private final String comment;
    private final LocalDateTime postedAt;

    /** Domain constructor (created via Event.addReview). */
    Review(Student student, Event event, int rating, String comment) {
        this(student, event, rating, comment, LocalDateTime.now());
    }

    /** Full constructor; also used for rehydration by the persistence layer. */
    public Review(Student student, Event event, int rating, String comment, LocalDateTime postedAt) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        this.student = student;
        this.event = event;
        this.rating = rating;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.postedAt = postedAt == null ? LocalDateTime.now() : postedAt;
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public Student getStudent() {
        return student;
    }

    public Event getEvent() {
        return event;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    /** Persistence hook — assigns the generated key after INSERT. */
    public void assignId(int reviewId) {
        this.reviewId = reviewId;
    }
}
