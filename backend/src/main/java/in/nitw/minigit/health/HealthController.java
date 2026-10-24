package in.nitw.minigit.health;

import java.util.Map;

import in.nitw.minigit.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke test for the whole stack: React -> Spring -> MySQL.
 *
 * devAuth = true while minigit.security.open-dev is on (before U1 auth is merged).
 * The React login page uses it to show the temporary "continue as" dev login,
 * and hides it automatically once open-dev is switched off.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public HealthController(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of(
                "status", "UP",
                "db", one != null && one == 1 ? "UP" : "DOWN",
                "devAuth", props.security().openDev());
    }
}
