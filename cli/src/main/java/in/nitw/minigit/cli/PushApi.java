package in.nitw.minigit.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Push = replay the net changes on the server-side working copy, then commit.
 * Routes: docs/api-contract.md (U3 worktree + commits, U5 branches). All HTTP goes through ApiClient.
 */
public final class PushApi {

    /**
     * @param newBranch when not null, create it from the commit we cloned (cfg.base) and push there
     * @return hash of the commit the server created
     */
    public String push(RepoConfig cfg, String message, List<LocalChanges.Change> changes, String newBranch) {
        ApiClient api = ApiClient.fromCredentials();
        if (!api.baseUrl().replaceAll("/+$", "").equalsIgnoreCase(cfg.server.replaceAll("/+$", ""))) {
            throw new CliException("You are logged in to " + api.baseUrl() + " but this repository was cloned from "
                    + cfg.server + ". Run `minigit login --server " + cfg.server + "`.");
        }
        String repo = "/repos/" + ApiClient.seg(cfg.owner) + "/" + ApiClient.seg(cfg.repo);
        String worktree = repo + "/worktree";

        String target = cfg.branch;
        if (newBranch == null) {
            // Checked here on purpose: worktree/checkout would quietly load the newer head and hide the conflict.
            List<ReadApi.CommitInfo> latest = new ReadApi(api).commits(cfg.owner, cfg.repo, cfg.branch, 0, 1);
            if (!latest.isEmpty() && !latest.get(0).hash().equals(cfg.base)) {
                throw new CliException("The branch moved on the server since you cloned. "
                        + "Push to a new branch instead: minigit push --new-branch <name>");
            }
        } else {
            // Branch creation lives at /repos/{repoId}/branches (numeric id); its "from" takes a commit hash.
            String repoId = new ReadApi(api).repo(cfg.owner, cfg.repo).path("id").asText();
            Map<String, String> body = new LinkedHashMap<>();
            body.put("name", newBranch);
            body.put("from", cfg.base);
            api.post("/repos/" + ApiClient.seg(repoId) + "/branches", body);
            target = newBranch;
        }

        api.post(worktree + "/checkout", Map.of("branch", target));
        api.post(worktree + "/discard", null);
        for (LocalChanges.Change c : changes) {
            if (c.status().equals("DELETED")) {
                api.delete(worktree + "/files", Map.of("path", c.path()));
            } else {
                String name = c.path().substring(c.path().lastIndexOf('/') + 1);
                api.putMultipart(worktree + "/files", Map.of("path", c.path()), "file", name, c.content());
            }
        }
        String hash = api.post(repo + "/commits", Map.of("message", message)).path("hash").asText(null);
        if (hash == null) throw new CliException("The server created a commit but did not return its hash.");
        return hash;
    }
}
