package in.nitw.minigit.storage;

import in.nitw.minigit.common.PathValidator;
import in.nitw.minigit.common.PathValidator.InvalidPathException;
import in.nitw.minigit.storage.diff.DiffService;
import in.nitw.minigit.storage.diff.MyersDiff;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The one place other modules get the storage engine for a repository.
 * Follows the layout documented in config/StorageConfig:
 *
 *   <root>/<repoId>/objects/ab/cdef...      blobs, trees, commits (ObjectStore)
 *   <root>/<repoId>/worktrees/<userId>/      per-user working copy (U3)
 *
 * Usage from a service:
 *   String tree = stores.buildTree(repoId, files);                  // paths from the client
 *   StoredCommit c = stores.writeCommit(repoId, tree, parents, author, msg);
 *   commitRow.setHash(c.hash()); commitRow.setCreatedAt(c.createdAt());   // same Instant as the object
 *   List<LogEntry> log = stores.history(repoId).log(headHash);
 *
 * Services should use buildTree / writeCommit rather than treeBuilder(...).build and
 * objects(...).write(new Commit(...)) directly, so path rules and commit times stay consistent.
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

    /**
     * Builds a tree from client-supplied paths. Every path goes through {@link PathValidator#normalise}
     * first, so "a//b" and "./a" are accepted as "a/b" and "a" (TreeBuilder itself stays strict).
     * Bad paths, two paths that normalise to the same file, and file/folder clashes ("a" and "a/b")
     * all throw InvalidPathException, which GlobalExceptionHandler maps to 400 INVALID_PATH.
     */
    public String buildTree(long repoId, Map<String, byte[]> files) {
        Map<String, byte[]> clean = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            String path = PathValidator.normalise(f.getKey());
            if (clean.put(path, f.getValue()) != null) {
                throw new InvalidPathException("Two files map to the same path: " + path);
            }
        }
        try {
            return treeBuilder(repoId).build(clean);
        } catch (InvalidPathException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InvalidPathException(e.getMessage());   // e.g. "Path conflict: a/b"
        }
    }

    /** Writes a commit stamped with the current time. See {@link #writeCommit(long, String, List, String, String, Instant)}. */
    public StoredCommit writeCommit(long repoId, String treeHash, List<String> parents,
                                    String author, String message) {
        return writeCommit(repoId, treeHash, parents, author, message, Instant.now());
    }

    /**
     * Writes a commit object and returns its hash together with the time stored inside it
     * (truncated to milliseconds, same as DATETIME(3)). Write {@link StoredCommit#createdAt()}
     * to commits.created_at; never rely on the column's CURRENT_TIMESTAMP default.
     */
    public StoredCommit writeCommit(long repoId, String treeHash, List<String> parents,
                                    String author, String message, Instant time) {
        Commit commit = new Commit(treeHash, parents, author, time, message);
        String hash = objects(repoId).write(commit);
        return new StoredCommit(hash, commit);
    }

    public HistoryService history(long repoId) {
        return new HistoryService(objects(repoId));
    }

    public DiffService diff(long repoId) {
        return new DiffService(history(repoId), new MyersDiff());
    }
}
