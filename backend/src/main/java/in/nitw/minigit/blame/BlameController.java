package in.nitw.minigit.blame;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.access.RepoAccess.Level;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/repos/{repoId}/blame?ref=main&path=src/Main.java  (ref = branch name or full commit hash) */
@RestController
@RequestMapping("/api/v1/repos/{repoId}/blame")
public class BlameController {

    private final BlameService blame;
    private final RepoAccess access;

    public BlameController(BlameService blame, RepoAccess access) {
        this.blame = blame;
        this.access = access;
    }

    @GetMapping
    public BlameService.BlameResult blame(@PathVariable long repoId, @RequestParam String ref, @RequestParam String path) {
        access.require(repoId, access.currentUserId(), Level.READ);
        return blame.blame(repoId, ref, path);
    }
}
