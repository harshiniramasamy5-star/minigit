package in.nitw.minigit.readmodel;

/**
 * Turns user search text into a safe SQL LIKE pattern.
 * '!' is the escape char (used with {@code ESCAPE '!'} in SQL) so behaviour doesn't depend on
 * MySQL's NO_BACKSLASH_ESCAPES mode. Without this, searching "%" or "_" would match everything.
 */
public final class LikePattern {

    static final int MAX_QUERY_LENGTH = 100;

    private LikePattern() {}

    /** @return {@code %escaped%}, or null when the query is blank (= no filter). */
    public static String contains(String query) {
        if (query == null) return null;
        String q = query.strip();
        if (q.isEmpty()) return null;
        if (q.length() > MAX_QUERY_LENGTH) q = q.substring(0, MAX_QUERY_LENGTH);
        StringBuilder sb = new StringBuilder(q.length() + 4).append('%');
        for (char ch : q.toCharArray()) {
            if (ch == '!' || ch == '%' || ch == '_') sb.append('!');
            sb.append(ch);
        }
        return sb.append('%').toString();
    }
}
