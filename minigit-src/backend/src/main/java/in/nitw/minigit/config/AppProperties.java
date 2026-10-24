package in.nitw.minigit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the "minigit.*" block in application.yml. Inject it instead of using @Value. */
@ConfigurationProperties(prefix = "minigit")
public record AppProperties(Storage storage, Security security, Cors cors, Jwt jwt) {

    public record Storage(String root) {}

    public record Security(boolean openDev) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, int expiryMinutes) {}
}
