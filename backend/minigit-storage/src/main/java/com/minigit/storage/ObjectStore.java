package com.minigit.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

@Service
public class ObjectStore {

    private final Path objectsDir;
    private final Path tmpDir;

    /** Used by Spring: the storage root comes from application.properties. */
    @Autowired
    public ObjectStore(@Value("${minigit.storage.root}") String rootPath) {
        this(Path.of(rootPath, "objects"));
    }

    /** Used by tests: pass any folder (for example a JUnit temp dir). */
    public ObjectStore(Path objectsDir) {
        this.objectsDir = objectsDir;
        this.tmpDir = objectsDir.resolve("tmp");
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Saves the object and returns its hash. If it already exists, nothing is written (dedup). */
    public String write(StorageObject obj) {
        byte[] bytes = obj.serialize();
        String hash = HashUtil.sha256Hex(bytes);
        Path target = pathFor(hash);

        if (Files.exists(target)) {
            return hash; // already stored
        }

        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(tmpDir, "obj", ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write object " + hash, e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
        return hash;
    }

    /** Reads an object back and turns it into a Blob, Tree or Commit. */
    public StorageObject read(String hash) {
        byte[] raw = readRaw(hash);

        int nul = -1;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) { nul = i; break; }
        }
        if (nul < 0) {
            throw new IllegalStateException("Corrupt object (no header): " + hash);
        }

        String[] header = new String(raw, 0, nul, StandardCharsets.UTF_8).split(" ");
        if (header.length != 2) {
            throw new IllegalStateException("Corrupt object header: " + hash);
        }
        String type = header[0];
        int size = Integer.parseInt(header[1]);

        byte[] body = Arrays.copyOfRange(raw, nul + 1, raw.length);
        if (body.length != size) {
            throw new IllegalStateException("Corrupt object (size mismatch): " + hash);
        }

        return switch (type) {
            case "blob" -> new Blob(body);
            case "tree" -> Tree.fromBody(body);
            case "commit" -> Commit.fromBody(body);
            default -> throw new IllegalStateException("Unknown object type: " + type);
        };
    }

    public boolean exists(String hash) {
        return Files.exists(pathFor(hash));
    }

    /** Returns the raw stored bytes (header + body). */
    public byte[] readRaw(String hash) {
        try {
            return Files.readAllBytes(pathFor(hash));
        } catch (IOException e) {
            throw new UncheckedIOException("Object not found: " + hash, e);
        }
    }

    /** True if the stored file still matches its hash (detects corruption or tampering). */
    public boolean verify(String hash) {
        try {
            byte[] bytes = Files.readAllBytes(pathFor(hash));
            return HashUtil.sha256Hex(bytes).equals(hash);
        } catch (IOException e) {
            return false;
        }
    }

    private Path pathFor(String hash) {
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        return objectsDir.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
    }
}