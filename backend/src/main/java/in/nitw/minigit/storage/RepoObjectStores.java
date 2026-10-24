package in.nitw.minigit.storage;

import in.nitw.minigit.storage.diff.DiffService;
import in.nitw.minigit.storage.diff.MyersDiff;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The one place other modules get the storage engine for a repository.
 * Follows the layout documented in config/StorageConfig:
 *
 *   <root>/<repoId>/objects/ab/cdef...      blobs, trees, commits (ObjectStore)
 *   <root>/<repoId>/worktrees/<userId>/      per-user working copy (U3)
 *
 * Usage from a service:
 *   String tree = stores.treeBuilder(repoId).build(files);
 *   String hash = stores.objects(repoId).write(new Commit(tree, parents, author, now, msg));
 *   List<LogEntry> log = stores.history(repoId).log(headHash);
 */
@Component
public class RepoObjectStores {

    private final Path root;

    /** storageRoot is the Path bean from StorageConfig (already absolute and created). */
    public RepoObjectStores(Path storageRoot) {
        this.root = storageRoot;
    }

    public Path repoDir(long repoId) {
        if (repoId <= 0) {
            throw new IllegalArgumentException("Invalid repository id: " + repoId);
        }
        return root.resolve(Long.toString(repoId));
    }

    public ObjectStore objects(long repoId) {
        return new ObjectStore(repoDir(repoId).resolve("objects"));
    }

    public Path worktree(long repoId, long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user id: " + userId);
        }
        return repoDir(repoId).resolve("worktrees").resolve(Long.toString(userId));
    }

    public TreeBuilder treeBuilder(long repoId) {
        return new TreeBuilder(objects(repoId));
    }

    public HistoryService history(long repoId) {
        return new HistoryService(objects(repoId));
    }

    public DiffService diff(long repoId) {
        return new DiffService(history(repoId), new MyersDiff());
    }
}
