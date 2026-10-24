package in.nitw.minigit.common;

import java.util.List;

/**
 * Shared shape for every paged list in the API contract:
 * {@code { "items": [...], "page": 0, "size": 20, "total": 57 }}.
 * Use {@link #clampPage} / {@link #clampSize} on incoming query params so a bad
 * {@code ?size=100000} can't pull the whole table.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static int clampPage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    public static int clampSize(Integer size) {
        if (size == null || size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    /** Row offset for LIMIT/OFFSET. long so huge page numbers can't overflow. */
    public static long offset(int page, int size) {
        return (long) page * size;
    }
}
