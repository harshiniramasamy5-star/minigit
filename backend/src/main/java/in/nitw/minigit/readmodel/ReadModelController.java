package in.nitw.minigit.readmodel;

import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.readmodel.ReadModels.Dashboard;
import in.nitw.minigit.readmodel.ReadModels.RepoSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /explore (guests allowed) and GET /dashboard (logged in). Owner: Harshini. */
@RestController
@RequestMapping("/api/v1")
public class ReadModelController {

    private final RepoReadQueries queries;
    private final CurrentUser currentUser;

    public ReadModelController(RepoReadQueries queries, CurrentUser currentUser) {
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @GetMapping("/explore")
    public PageResponse<RepoSummary> explore(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return queries.explore(q, PageResponse.clampPage(page), PageResponse.clampSize(size));
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestHeader(value = "X-Dev-User", required = false) String devUser) {
        return queries.dashboard(currentUser.requireId(devUser));
    }
}
