package in.nitw.minigit.merge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A three-way merge waiting for conflicts to be resolved. Nothing is committed and no
 * branch moves until {@link MergeService#finish} succeeds; abort just drops the session.
 */
public final class MergeSession {

    private final String id;
    private final long repoId;
    private final long startedBy;
    private final String sourceBranch, targetBranch;
    private final String oursHash, theirsHash, baseHash;
    private final Instant startedAt;
    private final Map<String, byte[]> merged = new TreeMap<>();
    private final Map<String, FileConflict> conflicts = new TreeMap<>();

    public MergeSession(String id, long repoId, long startedBy, String sourceBranch, String targetBranch,
                        String oursHash, String theirsHash, String baseHash,
                        Map<String, byte[]> clean, Map<String, FileConflict> conflicts) {
        this(id, repoId, startedBy, sourceBranch, targetBranch, oursHash, theirsHash, baseHash,
                clean, conflicts, Instant.now());
    }

    public MergeSession(String id, long repoId, long startedBy, String sourceBranch, String targetBranch,
                        String oursHash, String theirsHash, String baseHash,
                        Map<String, byte[]> clean, Map<String, FileConflict> conflicts, Instant startedAt) {
        this.startedAt = startedAt;
        this.id = id;
        this.repoId = repoId;
        this.startedBy = startedBy;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.oursHash = oursHash;
        this.theirsHash = theirsHash;
        this.baseHash = baseHash;
        clean.forEach((k, v) -> merged.put(k, v.clone()));
        this.conflicts.putAll(conflicts);
    }

    public String id() { return id; }
    public long repoId() { return repoId; }
    public long startedBy() { return startedBy; }
    public String sourceBranch() { return sourceBranch; }
    public String targetBranch() { return targetBranch; }
    public String oursHash() { return oursHash; }
    public String theirsHash() { return theirsHash; }
    public String baseHash() { return baseHash; }
    public Instant startedAt() { return startedAt; }

    public synchronized Map<String, FileConflict> conflicts() {
        return Collections.unmodifiableMap(new TreeMap<>(conflicts));
    }

    public synchronized Set<String> unresolved() {
        return Collections.unmodifiableSet(new TreeSet<>(conflicts.keySet()));
    }

    /**
     * choice: ours | theirs | base | custom | delete. ours/theirs/base copy the original bytes;
     * custom takes UTF-8 text and is refused for binary files. Picking a side that doesn't have
     * the file (e.g. "theirs" when they deleted it) deletes it.
     */
    public synchronized void resolve(String path, String choice, String customContent) {
        FileConflict c = conflicts.get(path);
        if (c == null) throw new IllegalArgumentException("No unresolved conflict for " + path);
        if (choice == null) throw new IllegalArgumentException("choice is required");
        byte[] value = switch (choice.toLowerCase(Locale.ROOT)) {
            case "ours" -> c.ours();
            case "theirs" -> c.theirs();
            case "base" -> c.base();
            case "delete" -> null;
            case "custom" -> {
                if (c.binary()) throw new IllegalArgumentException("Binary file: pick ours, theirs, base or delete");
                if (customContent == null) throw new IllegalArgumentException("content is required for custom");
                yield customContent.getBytes(StandardCharsets.UTF_8);
            }
            default -> throw new IllegalArgumentException("choice must be ours, theirs, base, custom or delete");
        };
        if (value == null) merged.remove(path);
        else merged.put(path, value.clone());
        conflicts.remove(path);
    }

    /**
     * Re-applies a stored resolution when a session is loaded back from the database.
     * value == null means the file is deleted. Unknown paths are ignored.
     */
    synchronized void resolveRaw(String path, byte[] value) {
        if (conflicts.remove(path) == null) return;
        if (value == null) merged.remove(path);
        else merged.put(path, value.clone());
    }

    /** What the merge commit will contain for this path right now (null = deleted or not set). */
    synchronized byte[] resolvedValue(String path) {
        byte[] v = merged.get(path);
        return v == null ? null : v.clone();
    }

    /** Final file set for the merge commit. Throws if anything is still unresolved. */
    public synchronized Map<String, byte[]> finalFiles() {
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Resolve all conflicts first: " + conflicts.keySet());
        }
        Map<String, byte[]> copy = new TreeMap<>();
        merged.forEach((k, v) -> copy.put(k, v.clone()));
        return copy;
    }
}
