package in.nitw.minigit.storage;

import java.time.Instant;

/**
 * A commit that has been written to the object store.
 * Use {@link #createdAt()} for commits.created_at so the database row and the
 * commit object carry exactly the same time (both millisecond precision).
 */
public record StoredCommit(String hash, Commit commit) {

    public Instant createdAt() {
        return commit.getTime();
    }
}
