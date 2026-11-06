package in.nitw.minigit.readmodel;

import java.time.Instant;
import java.util.List;

/**
 * Read-only DTOs for the contribution heatmap (GET /activity) and the commit graph
 * (GET /repos/{owner}/{repo}/graph). Separate from ReadModels so the existing
 * dashboard/explore code is untouched.
 */
public final class ActivityModels {

    private ActivityModels() {}

    /**
     * One calendar day (UTC) with at least one contribution. Days with 0 are left out.
     * level is 0-4 (see {@link ContributionLevel}) and decides the heatmap shade.
     */
    public record ActivityDay(String date, int count, int level) {}

    /** repo = "owner/name". */
    public record ActivityRepo(String repo, int count) {}

    /** Where the contributions came from: commits, pull requests opened, and review (PR comments + approvals). */
    public record ActivityBreakdown(int commits, int pullRequests, int reviews) {}

    /** from/to are ISO dates (inclusive). years = selectable years, newest first. */
    public record Activity(String from, String to, int total, List<Integer> years,
                           List<ActivityDay> days, ActivityBreakdown breakdown,
                           List<ActivityRepo> repos, ContributionCalendar.Stats stats) {}

    /** Shape from docs/api-contract.md: GET /repos/{owner}/{repo}/graph. */
    public record GraphCommit(String hash, List<String> parents, String message, String author,
                              Instant createdAt) {}

    public record GraphBranch(String name, String head) {}

    public record GraphView(List<GraphCommit> commits, List<GraphBranch> branches) {}
}
