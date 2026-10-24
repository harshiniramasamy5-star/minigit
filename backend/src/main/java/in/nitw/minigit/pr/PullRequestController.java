package in.nitw.minigit.pr;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.repo.RepoGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** U8 for {owner}/{repo}. Anyone who can read the repo can read its pull requests (guests too on public repos). */
@RestController
@RequestMapping("/api/v1/repos/{owner}/{repo}/pulls")
public class PullRequestController {

    public record CreateRequest(String title, String description, String source, String target) {}
    public record CommentRequest(String body) {}
    public record MergeRequest(String message) {}

    private final PullRequestService pulls;
    private final RepoGate gate;

    public PullRequestController(PullRequestService pulls, RepoGate gate) {
        this.pulls = pulls;
        this.gate = gate;
    }

    @GetMapping
    public PageResponse<PullRequestService.PullView> list(@PathVariable String owner, @PathVariable String repo,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return pulls.list(c.id(), PullRules.statusFilter(status), PageResponse.clampPage(page), PageResponse.clampSize(size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PullRequestService.PullView create(@PathVariable String owner, @PathVariable String repo,
                                              @RequestBody CreateRequest req) {
        RepoGate.Ctx c = gate.write(owner, repo);
        return pulls.create(c.id(), c.userId(), req.title(), req.description(), req.source(), req.target());
    }

    @GetMapping("/{number}")
    public PullRequestService.PullDetail get(@PathVariable String owner, @PathVariable String repo,
                                             @PathVariable int number) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return pulls.detail(c.id(), c.repo().defaultBranch(), number);
    }

    @PostMapping("/{number}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public PullRequestService.CommentView comment(@PathVariable String owner, @PathVariable String repo,
                                                  @PathVariable int number, @RequestBody CommentRequest req) {
        RepoGate.Ctx c = gate.member(owner, repo);
        return pulls.comment(c.id(), c.userId(), gate.username(c.userId()), number, req.body());
    }

    @PostMapping("/{number}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable String owner, @PathVariable String repo, @PathVariable int number) {
        RepoGate.Ctx c = gate.write(owner, repo);
        pulls.approve(c.id(), c.userId(), gate.username(c.userId()), number);
    }

    @PostMapping("/{number}/merge")
    public PullRequestService.MergeOutcome merge(@PathVariable String owner, @PathVariable String repo,
                                                 @PathVariable int number,
                                                 @RequestBody(required = false) MergeRequest req) {
        RepoGate.Ctx c = gate.write(owner, repo);
        return pulls.merge(c.id(), c.userId(), gate.username(c.userId()), number, req == null ? null : req.message());
    }

    /** Write access, or the author of the pull request. */
    @PostMapping("/{number}/close")
    public PullRequestService.PullView close(@PathVariable String owner, @PathVariable String repo,
                                             @PathVariable int number) {
        RepoGate.Ctx c = gate.member(owner, repo);
        boolean writer = "WRITE".equals(c.role()) || "OWNER".equals(c.role());
        if (!writer && pulls.authorIdOf(c.id(), number) != c.userId()) {
            throw ApiException.forbidden("Only the author or someone with Write access can close this pull request");
        }
        return pulls.close(c.id(), number);
    }
}
