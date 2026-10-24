package in.nitw.minigit.readmodel;

import java.time.Instant;
import java.util.List;

/** Read-only DTOs for explore + dashboard. Shapes follow docs/api-contract.md. */
public final class ReadModels {

    private ReadModels() {}

    /** Contract "Repo". myRole is null on explore (guests have no role). */
    public record RepoSummary(long id, String owner, String name, String description,
                              String visibility, String defaultBranch, String myRole,
                              Instant createdAt, Instant updatedAt) {}

    /** repo = "owner/name" so the UI can link straight to /owner/name/pulls/number. */
    public record PullSummary(String repo, int number, String title, String author,
                              String source, String target, Instant createdAt) {}

    public record CommitSummary(String repo, String hash, String message, String author,
                                Instant createdAt) {}

    public record Dashboard(List<RepoSummary> repos, List<PullSummary> openPulls,
                            List<CommitSummary> recentCommits) {}
}
