package in.nitw.minigit.merge;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * One file both sides changed differently since the merge base.
 * Content is kept as raw bytes, so picking ours/theirs/base never corrupts binary files.
 * A null side means the file doesn't exist on that side.
 */
public record FileConflict(String path, Kind kind, byte[] base, byte[] ours, byte[] theirs) {

    /** FR-29 conflict types. */
    public enum Kind {
        BOTH_MODIFIED,      // in base, changed differently on both sides
        BOTH_ADDED,         // not in base, added with different content on both sides
        MODIFIED_DELETED,   // we changed it, they deleted it
        DELETED_MODIFIED    // we deleted it, they changed it
    }

    public static Kind classify(byte[] base, byte[] ours, byte[] theirs) {
        if (base == null) return Kind.BOTH_ADDED;
        if (theirs == null) return Kind.MODIFIED_DELETED;
        if (ours == null) return Kind.DELETED_MODIFIED;
        return Kind.BOTH_MODIFIED;
    }

    /** Binary if any side has a NUL byte or isn't valid UTF-8. Binary files can't be custom-edited. */
    public boolean binary() {
        return isBinary(base) || isBinary(ours) || isBinary(theirs);
    }

    public String baseText() { return text(base); }
    public String oursText() { return text(ours); }
    public String theirsText() { return text(theirs); }

    private String text(byte[] b) {
        return b == null || binary() ? null : new String(b, StandardCharsets.UTF_8);
    }

    static boolean isBinary(byte[] b) {
        if (b == null) return false;
        for (byte x : b) if (x == 0) return true;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b));
            return false;
        } catch (CharacterCodingException e) {
            return true;
        }
    }
}
