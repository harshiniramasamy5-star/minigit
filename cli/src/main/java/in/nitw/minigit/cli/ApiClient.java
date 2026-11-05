package in.nitw.minigit.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The one HTTP client every command uses (JDK HttpClient + Jackson, bearer token).
 * Paths are relative to /api/v1 and already URL-encoded; build path pieces with {@link #seg(String)}:
 *
 *   api.get("/repos/" + ApiClient.seg(owner) + "/" + ApiClient.seg(repo) + "/branches", null)
 *
 * Every failure becomes a {@link CliException} with a message fit to print:
 *   401 -> "Your login expired ... run `minigit login` again"   (also checked locally from expiresAt, no request sent)
 *   409 BRANCH_HEAD_MOVED -> "The branch moved ..."             (other 409s print the server's message)
 *   423 -> "Account locked after 5 failed logins ..."
 */
public final class ApiClient {

    private static final String API = "/api/v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String token;        // null for login/register
    private final Instant expiresAt;   // null = unknown

    public ApiClient(String baseUrl, String token, Instant expiresAt) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    /** For login and register: no token yet. */
    public static ApiClient anonymous(String baseUrl) {
        return new ApiClient(baseUrl, null, null);
    }

    /** Uses ~/.minigit/credentials. Throws a clear message if the user never logged in or the token has expired. */
    public static ApiClient fromCredentials() {
        Credentials c = Credentials.load()
                .orElseThrow(() -> new CliException("You are not logged in. Run `minigit login` first.", 2));
        ApiClient client = new ApiClient(c.server(), c.token(), c.expiresAt());
        client.checkNotExpired();
        return client;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** URL-encode one path segment (space becomes %20, not +). */
    public static String seg(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The server sends Instants as ISO text; tolerate epoch seconds too. */
    public static Instant instant(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isNumber()) {
            double d = n.asDouble();
            return Instant.ofEpochMilli((long) (d * 1000));
        }
        return Instant.parse(n.asText());
    }

    // ---------- calls ----------

    public JsonNode get(String path, Map<String, String> query) {
        return parse(exec("GET", path, query, null, null).body());
    }

    public byte[] getBytes(String path, Map<String, String> query) {
        return exec("GET", path, query, null, null).body();
    }

    /** @param body any Jackson-serialisable object (a Map is fine), or null */
    public JsonNode post(String path, Object body) {
        byte[] bytes = null;
        if (body != null) {
            try {
                bytes = JSON.writeValueAsBytes(body);
            } catch (IOException e) {
                throw new CliException("Can't build the request: " + e.getMessage());
            }
        }
        return parse(exec("POST", path, null, body == null ? null : "application/json", bytes).body());
    }

    public void delete(String path, Map<String, String> query) {
        exec("DELETE", path, query, null, null);
    }

    /**
     * PUT multipart/form-data: text {@code fields} plus one file part. Matches the backend's
     * PUT /repos/{owner}/{repo}/worktree/files, which takes parts `path` and `file`.
     */
    public JsonNode putMultipart(String path, Map<String, String> fields, String fileField, String fileName, byte[] content) {
        String boundary = "minigit-" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (Map.Entry<String, String> f : fields.entrySet()) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey()
                        + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            String safeName = fileName.replace("\"", "_").replace("\r", "_").replace("\n", "_");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                    + safeName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return parse(exec("PUT", path, null, "multipart/form-data; boundary=" + boundary, out.toByteArray()).body());
    }

    // ---------- internals ----------

    private void checkNotExpired() {
        if (token != null && expiresAt != null && !Instant.now().isBefore(expiresAt)) {
            throw new CliException("Your login has expired (logins last 30 minutes). Run `minigit login` again.", 2);
        }
    }

    private HttpResponse<byte[]> exec(String method, String path, Map<String, String> query,
                                      String contentType, byte[] body) {
        checkNotExpired();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + API + path + queryString(query)))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, */*");
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (contentType != null) b.header("Content-Type", contentType);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));

        HttpResponse<byte[]> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new CliException("The server at " + baseUrl + " took too long to answer. Try again.");
        } catch (IOException e) {
            throw new CliException("Can't reach the server at " + baseUrl + ". Is the backend running?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException("Interrupted.");
        }
        int status = res.statusCode();
        if (status >= 200 && status < 300) return res;
        throw mapError(status, res.body());
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (e.getValue() == null) continue;
            sb.append(sb.length() == 0 ? '?' : '&')
                    .append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) return JSON.nullNode();
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new CliException("The server sent a reply this client can't read.");
        }
    }

    /** Turns the backend's ApiError ({status, code, message}) into a message for the terminal. Package-visible for tests. */
    static CliException mapError(int status, byte[] body) {
        String code = null, message = null;
        try {
            JsonNode n = JSON.readTree(body);
            if (n != null && n.hasNonNull("code")) code = n.get("code").asText();
            if (n != null && n.hasNonNull("message")) message = n.get("message").asText();
        } catch (IOException | RuntimeException notJson) {
            // fall through to the generic messages
        }
        String m = message == null ? "" : message;
        return switch (status) {
            case 401 -> "INVALID_CREDENTIALS".equals(code)
                    ? new CliException("Wrong username or password.")
                    : new CliException("Your login expired or is no longer valid (logins last 30 minutes). "
                            + "Run `minigit login` again.", 2);
            case 423 -> new CliException("Account locked after 5 failed logins. " + m);
            case 409 -> "BRANCH_HEAD_MOVED".equals(code)
                    ? new CliException("The branch moved on the server since you started (BRANCH_HEAD_MOVED). "
                            + "Run `minigit pull`, then try again.")
                    : new CliException(m.isEmpty() ? "Conflict (HTTP 409)." : m + (code == null ? "" : " (" + code + ")"));
            case 403 -> new CliException("Not allowed: " + (m.isEmpty() ? "you don't have access to do that." : m));
            case 404 -> new CliException(m.isEmpty() ? "Not found." : m);
            case 400 -> new CliException("The server rejected the request: " + (m.isEmpty() ? "bad request." : m));
            default -> status >= 500
                    ? new CliException("The server had an internal error (HTTP " + status
                            + "). Try again; if it keeps failing, tell the team.")
                    : new CliException(m.isEmpty() ? "Request failed (HTTP " + status + ")." : m);
        };
    }
}
