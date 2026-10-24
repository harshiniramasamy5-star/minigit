package in.nitw.minigit.history;

import in.nitw.minigit.commit.CommitService;
import in.nitw.minigit.repo.RepoGate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** U4: browse files at a branch or commit, diff, restore, and the graph data. Reads work for guests on public repos. */
@RestController
@RequestMapping("/api/v1/repos/{owner}/{repo}")
public class RepoFilesController {

    public record RestoreRequest(String branch, String commit, String path) {}

    private final RepoHistory history;
    private final RepoGate gate;

    public RepoFilesController(RepoHistory history, RepoGate gate) {
        this.history = history;
        this.gate = gate;
    }

    @GetMapping("/branches")
    public List<RepoHistory.BranchInfo> branches(@PathVariable String owner, @PathVariable String repo) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return history.branchList(c.id(), c.repo().defaultBranch());
    }

    @GetMapping("/tree")
    public List<TreeListing.Entry> tree(@PathVariable String owner, @PathVariable String repo,
                                        @RequestParam(required = false) String ref,
                                        @RequestParam(required = false) String path) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return history.tree(c.id(), ref, path, c.repo().defaultBranch());
    }

    @GetMapping("/blob")
    public ResponseEntity<byte[]> blob(@PathVariable String owner, @PathVariable String repo,
                                       @RequestParam(required = false) String ref, @RequestParam String path) {
        RepoGate.Ctx c = gate.read(owner, repo);
        byte[] data = history.blob(c.id(), ref, path, c.repo().defaultBranch());
        MediaType type = TextCheck.isText(data)
                ? new MediaType("text", "plain", StandardCharsets.UTF_8) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).body(data);
    }

    @GetMapping("/diff")
    public List<RepoHistory.FileDiffView> diff(@PathVariable String owner, @PathVariable String repo,
                                               @RequestParam(required = false) String from,
                                               @RequestParam String to) {
        RepoGate.Ctx c = gate.read(owner, repo);
        return history.diff(c.id(), from, to, c.repo().defaultBranch());
    }

    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.CREATED)
    public CommitService.CommitView restore(@PathVariable String owner, @PathVariable String repo,
                                            @RequestBody RestoreRequest req) {
        RepoGate.Ctx c = gate.write(owner, repo);
        return history.restore(c.id(), c.userId(), gate.username(c.userId()), c.repo().defaultBranch(),
                req.branch(), req.commit(), req.path());
    }

    @GetMapping("/graph")
    public RepoHistory.Graph graph(@PathVariable String owner, @PathVariable String repo,
                                   @RequestParam(required = false) Integer limit) {
        RepoGate.Ctx c = gate.read(owner, repo);
        int n = (limit == null || limit <= 0) ? 200 : Math.min(limit, 1000);
        return history.graph(c.id(), n);
    }
}
