package in.nitw.minigit.readmodel;

import in.nitw.minigit.readmodel.ActivityModels.Activity;
import in.nitw.minigit.readmodel.ActivityModels.GraphView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /activity: the logged-in user's contribution heatmap data.
 * GET /repos/{owner}/{repo}/graph: commits + parents + branch heads for the commit graph UI.
 * Both are read-only and add no tables.
 */
@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    private final ActivityReadQueries queries;
    private final CurrentUser currentUser;

    public ActivityController(ActivityReadQueries queries, CurrentUser currentUser) {
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @GetMapping("/activity")
    public Activity activity(@RequestHeader(value = "X-Dev-User", required = false) String devUser,
                             @RequestParam(required = false) Integer year) {
        return queries.activity(currentUser.requireId(devUser), year);
    }

    @GetMapping("/repos/{owner}/{repo}/graph")
    public GraphView graph(@PathVariable String owner, @PathVariable String repo,
                           @RequestParam(required = false) Integer limit,
                           @RequestHeader(value = "X-Dev-User", required = false) String devUser) {
        return queries.graph(owner, repo, currentUser.findId(devUser), limit);
    }
}
