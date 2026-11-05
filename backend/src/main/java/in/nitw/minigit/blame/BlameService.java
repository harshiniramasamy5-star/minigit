package in.nitw.minigit.blame;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.HistoryService;
import in.nitw.minigit.storage.RepoObjectStores;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Git blame: who last wrote each line of a text file, and in which commit. Needs Read access (checked by the controller). */
@Service
public class BlameService {

    /** Proposed limits V-10 / V-11 / V-12 for the SRS; the team confirms them. */
    public static final int MAX_LINES = 2000;
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_STEPS = 500;

    public record BlameLine(int line, String text, String commit, String author, Instant time, String message) {}

    public record BlameResult(String ref, String commit, String path, List<BlameLine> lines) {}

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final RepoObjectStores stores;
    private final BranchStore branches;

    public BlameService(RepoObjectStores stores, BranchStore branches) {
        this.stores = stores;
        this.branches = branches;
    }

    public BlameResult blame(long repoId, String ref, String rawPath) {
        String path = cleanPath(rawPath);
        HistoryService history = stores.history(repoId);
        String start = resolve(repoId, history, ref);

        Map<String, List<String>> lineCache = new HashMap<>();
        Map<String, Commit> commitCache = new HashMap<>();

        byte[] startBytes = history.readFile(start, path);
        if (startBytes == null) throw ApiException.notFound("File " + path);
        List<String> startLines = decode(startBytes, true);
        lineCache.put(start, startLines);

        BlameEngine.Source src = new BlameEngine.Source() {
            @Override public List<String> lines(String commit) {
                if (lineCache.containsKey(commit)) return lineCache.get(commit);
                byte[] b = history.readFile(commit, path);
                List<String> l = b == null ? null : decode(b, false);   // binary/huge in an old commit = treated as absent
                lineCache.put(commit, l);
                return l;
            }
            @Override public List<String> parents(String commit) {
                return commit(history, commitCache, commit).getParentHashes();
            }
        };

        String[] owners;
        try {
            owners = BlameEngine.blame(start, src, MAX_STEPS);
        } catch (BlameEngine.TooDeep e) {
            throw ApiException.badRequest("BLAME_TOO_LARGE", e.getMessage());
        }

        List<BlameLine> out = new ArrayList<>(startLines.size());
        for (int i = 0; i < startLines.size(); i++) {
            Commit c = commit(history, commitCache, owners[i]);
            out.add(new BlameLine(i + 1, startLines.get(i), owners[i], c.getAuthor(), c.getTime(), firstLine(c.getMessage())));
        }
        return new BlameResult(ref, start, path, out);
    }

    private static Commit commit(HistoryService h, Map<String, Commit> cache, String hash) {
        return cache.computeIfAbsent(hash, h::getCommit);
    }

    private String resolve(long repoId, HistoryService history, String ref) {
        if (ref == null || ref.isBlank()) throw ApiException.badRequest("VALIDATION_FAILED", "ref is required");
        String hash = branches.find(repoId, ref).map(BranchStore.Branch::headCommitHash).orElse(null);
        if (hash == null && HASH.matcher(ref).matches()) hash = ref;
        if (hash == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch or commit " + ref);
        try {
            history.getCommit(hash);
        } catch (RuntimeException missing) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No commit " + hash);
        }
        return hash;
    }

    private static String cleanPath(String raw) {
        if (raw == null) throw ApiException.badRequest("VALIDATION_FAILED", "path is required");
        String p = raw.strip();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty() || p.contains("//") || p.endsWith("/")
                || List.of(p.split("/")).stream().anyMatch(s -> s.equals("..") || s.equals("."))) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Invalid file path");
        }
        return p;
    }

    /** @param strict true for the file being blamed (throw), false for ancestors (return null = treat as absent) */
    private static List<String> decode(byte[] bytes, boolean strict) {
        if (bytes.length > MAX_BYTES) {
            return reject(strict, "BLAME_TOO_LARGE", "File is larger than " + (MAX_BYTES / 1024) + " KB");
        }
        for (byte b : bytes) {
            if (b == 0) return reject(strict, "BLAME_BINARY", "Blame only works on text files");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return reject(strict, "BLAME_BINARY", "Blame only works on UTF-8 text files");
        }
        if (text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (text.endsWith("\n")) lines.remove(lines.size() - 1);
        lines.replaceAll(l -> l.endsWith("\r") ? l.substring(0, l.length() - 1) : l);
        if (lines.size() > MAX_LINES) {
            return reject(strict, "BLAME_TOO_LARGE", "File has more than " + MAX_LINES + " lines");
        }
        return lines;
    }

    private static List<String> reject(boolean strict, String code, String msg) {
        if (strict) throw ApiException.badRequest(code, msg);
        return null;
    }

    private static String firstLine(String m) {
        int i = m.indexOf('\n');
        return (i < 0 ? m : m.substring(0, i)).strip();
    }
}
