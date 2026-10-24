package in.nitw.minigit.common;

import java.time.Instant;
import java.util.List;

/**
 * The one error shape every endpoint returns. The React client reads {@code message} for toasts
 * and {@code code} for logic (e.g. BRANCH_HEAD_MOVED -> offer "discard" or "commit to new branch").
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of(), Instant.now());
    }
}
