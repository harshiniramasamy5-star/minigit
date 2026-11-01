package in.nitw.minigit.history;

import in.nitw.minigit.commit.CommitService;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.repo.RepoGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** U3 (create a commit) and U4 (list and open commits) for {owner}/{repo}. */
@RestController
@RequestMapping("/api/v1/repos/{owner}/{repo}/commits")
public class CommitController {

    public record CommitRequest(String message, String newBranch) {}

    private final CommitService commits;
    private final RepoHistory history;
    private final RepoGate gate;

    public CommitController(CommitService commits, RepoHistory history, RepoGate gate) {
        this.commits = commits;
        this.history = history;
        this.gate = gate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommitService.CommitView create(@PathVariable String owner, @PathVariable String repo,
                                           @RequestBody CommitRequest req) {
        RepoGate.Ctx c = gate.write(owner, repo);
        return commits.commit(c.id(), c.userId(), gate.username(c.userId()), c.repo().defaultBranch(),
                req.message(), req.newBranch());
    }

    @GetMapping
    public PageResponse<CommitService.CommitView> list(@PathVariable String owner, @PathVariable String repo,
                                                       @RequestParam(required = false) String branch,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return history.log(c.id(), branch, c.repo().defaultBranch(),
                PageResponse.clampPage(page), PageResponse.clampSize(size));
    }

    @GetMapping("/{hash}")
    public RepoHistory.CommitDetail detail(@PathVariable String owner, @PathVariable String repo,
                                           @PathVariable String hash) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return history.detail(c.id(), hash);
    }
}
