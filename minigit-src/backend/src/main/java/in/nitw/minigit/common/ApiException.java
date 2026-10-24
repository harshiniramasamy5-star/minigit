package in.nitw.minigit.common;

import org.springframework.http.HttpStatus;

/**
 * Throw this from any service. GlobalExceptionHandler turns it into an ApiError.
 * Use the static helpers so codes stay consistent across modules.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 409 for the "branch head moved since you checked out" case (U3) and other state clashes. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
