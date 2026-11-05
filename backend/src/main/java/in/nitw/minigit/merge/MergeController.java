package in.nitw.minigit.merge;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.access.RepoAccess.Level;
import in.nitw.minigit.branch.BranchProtection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/repos/{repoId}/merges")
public class MergeController {

    public record MergeRequest(String source, String target, String expectedTargetHead, String message) {}
    public record ResolveRequest(String path, String choice, String content) {}
    public record FinishRequest(String message) {}

    private final MergeService merges;
    private final RepoAccess access;
    private final BranchProtection protection;

    public MergeController(MergeService merges, RepoAccess access, BranchProtection protection) {
        this.merges = merges;
        this.access = access;
        this.protection = protection;
    }

    @PostMapping
    public MergeService.MergeResult merge(@PathVariable long repoId, @RequestBody MergeRequest req) {
        long user = writer(repoId);
        if (protection.isRequired(repoId, req.target())) {
            throw BranchProtection.direct(req.target(), "merge into");
        }
        return merges.merge(repoId, user, access.username(user), req.source(), req.target(),
                req.expectedTargetHead(), req.message());
    }

    @GetMapping("/{sessionId}")
    public MergeService.SessionView session(@PathVariable long repoId, @PathVariable String sessionId) {
        writer(repoId);
        return merges.session(repoId, sessionId);
    }

    @PostMapping("/{sessionId}/resolve")
    public MergeService.SessionView resolve(@PathVariable long repoId, @PathVariable String sessionId,
                                            @RequestBody ResolveRequest req) {
        writer(repoId);
        return merges.resolve(repoId, sessionId, req.path(), req.choice(), req.content());
    }

    @PostMapping("/{sessionId}/finish")
    public Map<String, String> finish(@PathVariable long repoId, @PathVariable String sessionId,
                                      @RequestBody(required = false) FinishRequest req) {
        long user = writer(repoId);
        String hash = merges.finish(repoId, user, access.username(user), sessionId,
                req == null ? null : req.message());
        return Map.of("status", "MERGED", "commitHash", hash);
    }

    @PostMapping("/{sessionId}/abort")
    public Map<String, String> abort(@PathVariable long repoId, @PathVariable String sessionId) {
        writer(repoId);
        merges.abort(repoId, sessionId);
        return Map.of("status", "ABORTED");
    }

    private long writer(long repoId) {
        long user = access.currentUserId();
        access.require(repoId, user, Level.WRITE);
        return user;
    }
}
