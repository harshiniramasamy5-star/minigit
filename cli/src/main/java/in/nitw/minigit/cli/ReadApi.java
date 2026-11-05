package in.nitw.minigit.cli;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only calls the CLI needs: repo, branches, tree, blob, diff, commits. Mirrors the U4/U5 endpoints in docs/api-contract.md. */
public final class ReadApi {

    public record Branch(String name, String head, boolean isProtected, boolean isDefault) {}
    public record TreeEntry(String name, String type, long size) {}
    /** status: ADDED | DELETED | MODIFIED */
    public record Change(String path, String status, boolean binary) {}
    public record CommitInfo(String hash, List<String> parents, String author, String message, String createdAt) {}

    private final ApiClient api;

    public ReadApi(ApiClient api) {
        this.api = api;
    }

    private static String repoPath(String owner, String repo) {
        return "/repos/" + ApiClient.seg(owner) + "/" + ApiClient.seg(repo);
    }

    /** Repo = {id, owner, name, description, visibility, defaultBranch, myRole, ...} */
    public JsonNode repo(String owner, String repo) {
        return api.get(repoPath(owner, repo), null);
    }

    public List<Branch> branches(String owner, String repo) {
        List<Branch> out = new ArrayList<>();
        for (JsonNode b : api.get(repoPath(owner, repo) + "/branches", null)) {
            out.add(new Branch(b.path("name").asText(), b.path("head").asText(),
                    b.path("isProtected").asBoolean(), b.path("isDefault").asBoolean()));
        }
        return out;
    }

    /** One folder at a branch name or commit hash. path "" or null = the root. */
    public List<TreeEntry> tree(String owner, String repo, String ref, String path) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("ref", ref);
        if (path != null && !path.isEmpty()) q.put("path", path);
        List<TreeEntry> out = new ArrayList<>();
        for (JsonNode e : api.get(repoPath(owner, repo) + "/tree", q)) {
            out.add(new TreeEntry(e.path("name").asText(), e.path("type").asText(), e.path("size").asLong()));
        }
        return out;
    }

    /** Raw bytes of one file (text or binary). */
    public byte[] blob(String owner, String repo, String ref, String path) {
        return api.getBytes(repoPath(owner, repo) + "/blob", Map.of("ref", ref, "path", path));
    }

    /** Every file path (not folders) at a ref, found by walking /tree folder by folder. Sorted by folder walk order. */
    public List<String> listFiles(String owner, String repo, String ref) {
        List<String> files = new ArrayList<>();
        Deque<String> dirs = new ArrayDeque<>();
        dirs.push("");
        while (!dirs.isEmpty()) {
            String dir = dirs.pop();
            for (TreeEntry e : tree(owner, repo, ref, dir)) {
                String p = dir.isEmpty() ? e.name() : dir + "/" + e.name();
                if ("DIR".equals(e.type())) dirs.push(p);
                else files.add(p);
            }
        }
        files.sort(String::compareTo);
        return files;
    }

    /** What changed between two refs (from may be blank = empty project). Used by pull to fetch only changed files. */
    public List<Change> diff(String owner, String repo, String from, String to) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("from", from == null ? "" : from);
        q.put("to", to);
        List<Change> out = new ArrayList<>();
        for (JsonNode d : api.get(repoPath(owner, repo) + "/diff", q)) {
            out.add(new Change(d.path("path").asText(), d.path("status").asText(), d.path("binary").asBoolean()));
        }
        return out;
    }

    /** One page of commits, newest first. branch may be null (default branch). */
    public List<CommitInfo> commits(String owner, String repo, String branch, int page, int size) {
        Map<String, String> q = new LinkedHashMap<>();
        if (branch != null) q.put("branch", branch);
        q.put("page", Integer.toString(page));
        q.put("size", Integer.toString(size));
        List<CommitInfo> out = new ArrayList<>();
        for (JsonNode c : api.get(repoPath(owner, repo) + "/commits", q).path("items")) {
            List<String> parents = new ArrayList<>();
            c.path("parents").forEach(p -> parents.add(p.asText()));
            out.add(new CommitInfo(c.path("hash").asText(), parents, c.path("author").asText(),
                    c.path("message").asText(), c.path("createdAt").asText()));
        }
        return out;
    }
}
