package in.nitw.minigit.commit;

import in.nitw.minigit.repo.RepoGate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** U3: the caller's own working copy of {owner}/{repo}. Needs Write access (it is per-user and editable). */
@RestController
@RequestMapping("/api/v1/repos/{owner}/{repo}/worktree")
public class WorktreeController {

    public record CheckoutRequest(String branch) {}

    private final WorktreeService worktree;
    private final RepoGate gate;

    public WorktreeController(WorktreeService worktree, RepoGate gate) {
        this.worktree = worktree;
        this.gate = gate;
    }

    @GetMapping
    public WorktreeService.State state(@PathVariable String owner, @PathVariable String repo) {
        RepoGate.Ctx c = gate.write(owner, repo);
        return worktree.state(c.id(), c.userId(), c.repo().defaultBranch());
    }

    @PostMapping("/checkout")
    public WorktreeService.State checkout(@PathVariable String owner, @PathVariable String repo,
                                          @RequestBody CheckoutRequest req) {
        RepoGate.Ctx c = gate.write(owner, repo);
        worktree.checkout(c.id(), c.userId(), c.repo().defaultBranch(), req.branch());
        return worktree.state(c.id(), c.userId(), c.repo().defaultBranch());
    }

    @PutMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@PathVariable String owner, @PathVariable String repo,
                    @RequestParam("path") String path, @RequestParam("file") MultipartFile file) {
        RepoGate.Ctx c = gate.write(owner, repo);
        try {
            worktree.putFile(c.id(), c.userId(), c.repo().defaultBranch(), path, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The current content of one working-copy file (for the editor). */
    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@PathVariable String owner, @PathVariable String repo,
                                       @RequestParam String path) {
        RepoGate.Ctx c = gate.write(owner, repo);
        byte[] data = worktree.readFile(c.id(), c.userId(), c.repo().defaultBranch(), path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(data);
    }

    @DeleteMapping("/files")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String owner, @PathVariable String repo, @RequestParam String path) {
        RepoGate.Ctx c = gate.write(owner, repo);
        worktree.deleteFile(c.id(), c.userId(), c.repo().defaultBranch(), path);
    }

    @PostMapping("/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable String owner, @PathVariable String repo) {
        RepoGate.Ctx c = gate.write(owner, repo);
        worktree.discard(c.id(), c.userId(), c.repo().defaultBranch());
    }
}
