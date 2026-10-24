package in.nitw.minigit.storage.pr;

import java.time.Instant;

public record PullRequestComment(String author, String text, Instant createdAt) {

    public PullRequestComment {
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Comment author is required");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Comment text is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Comment time is required");
        }
    }
}