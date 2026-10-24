package in.nitw.minigit.repo;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.readmodel.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * U2. Repos are addressed as {owner}/{repo} (username + name), per the API contract.
 * Branch/merge endpoints from U5-U7 use /repos/{repoId}/..., the repo JSON carries the id for them.
 */
@RestController
@RequestMapping("/api/v1/repos")
public class RepoController {

    public record CreateRequest(String name, String description, String visibility) {}
    public record UpdateRequest(String name, String description, String visibility) {}

    private final RepoService repos;
    private final RepoAccess access;
    private final CurrentUser currentUser;
    private final HttpServletRequest request;

    public RepoController(RepoService repos, RepoAccess access, CurrentUser currentUser, HttpServletRequest request) {
        this.repos = repos;
        this.access = access;
        this.currentUser = currentUser;
        this.request = request;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepoService.RepoView create(@RequestBody CreateRequest req) {
        long userId = access.currentUserId();
        return repos.create(userId, access.username(userId), req.name(), req.description(), req.visibility());
    }

    @GetMapping("/mine")
    public PageResponse<RepoService.RepoView> mine(@RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return repos.mine(access.currentUserId(), PageResponse.clampPage(page), PageResponse.clampSize(size));
    }

    @GetMapping("/{owner}/{repo}")
    public RepoService.RepoView get(@PathVariable String owner, @PathVariable String repo) {
        Long userId = currentUser.findId(request.getHeader("X-Dev-User")).orElse(null);
        return repos.view(repos.visible(owner, repo, userId), userId);
    }

    @PatchMapping("/{owner}/{repo}")
    public RepoService.RepoView update(@PathVariable String owner, @PathVariable String repo,
                                       @RequestBody UpdateRequest req) {
        long userId = access.currentUserId();
        return repos.update(repos.asOwner(owner, repo, userId), userId, req.name(), req.description(), req.visibility());
    }

    @DeleteMapping("/{owner}/{repo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String owner, @PathVariable String repo) {
        long userId = access.currentUserId();
        repos.delete(repos.asOwner(owner, repo, userId), userId);
    }
}
