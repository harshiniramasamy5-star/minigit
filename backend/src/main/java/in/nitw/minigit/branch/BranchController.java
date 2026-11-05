package in.nitw.minigit.branch;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.access.RepoAccess.Level;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import in.nitw.minigit.common.ApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Branch names can contain "/" (feat/login), so they go in the body or a query param,
 * never in the URL path.
 */
@RestController
@RequestMapping("/api/v1/repos/{repoId}/branches")
public class BranchController {

    public record CreateRequest(String name, String from) {}
    public record NameRequest(String name) {}
    public record ProtectionRequest(String name, boolean requireApproval) {}

    private final BranchService branches;
    private final RepoAccess access;
    private final BranchProtection protection;

    public BranchController(BranchService branches, RepoAccess access, BranchProtection protection) {
        this.branches = branches;
        this.access = access;
        this.protection = protection;
    }

    @GetMapping
    public List<BranchService.BranchView> list(@PathVariable long repoId) {
        access.require(repoId, access.currentUserId(), Level.READ);
        return branches.list(repoId);
    }

    @PostMapping
    public BranchService.BranchView create(@PathVariable long repoId, @RequestBody CreateRequest req) {
        writer(repoId);
        return branches.create(repoId, req.name(), req.from());
    }

    @DeleteMapping
    public Map<String, String> delete(@PathVariable long repoId, @RequestParam String name) {
        long user = writer(repoId);
        branches.delete(repoId, user, name);
        return Map.of("deleted", name);
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(@PathVariable long repoId, @RequestBody NameRequest req) {
        long user = writer(repoId);
        return Map.of("branch", req.name(), "headCommitHash", branches.checkout(repoId, user, req.name()));
    }

    @PostMapping("/undo")
    public Map<String, String> undo(@PathVariable long repoId, @RequestBody NameRequest req) {
        writer(repoId);
        if (protection.isRequired(repoId, req.name())) {
            throw BranchProtection.direct(req.name(), "undo changes on");
        }
        return Map.of("branch", req.name(), "undone", branches.undo(repoId, req.name()));
    }

    @GetMapping("/undo")
    public List<String> undoHistory(@PathVariable long repoId, @RequestParam String name) {
        access.require(repoId, access.currentUserId(), Level.READ);
        return branches.undoHistory(repoId, name);
    }

    /** Names of the branches that require an approved pull request. */
    @GetMapping("/protection")
    public Set<String> protectedBranches(@PathVariable long repoId) {
        access.require(repoId, access.currentUserId(), Level.READ);
        return protection.requiredNames(repoId);
    }

    /** Owner only: turn the approval rule on or off for one branch. */
    @PutMapping("/protection")
    public Map<String, Object> setProtection(@PathVariable long repoId, @RequestBody ProtectionRequest req) {
        access.require(repoId, access.currentUserId(), Level.OWNER);
        if (!protection.set(repoId, req.name(), req.requireApproval())) {
            throw ApiException.notFound("Branch " + req.name());
        }
        return Map.of("branch", req.name(), "requireApproval", req.requireApproval());
    }

    private long writer(long repoId) {
        long user = access.currentUserId();
        access.require(repoId, user, Level.WRITE);
        return user;
    }
}
