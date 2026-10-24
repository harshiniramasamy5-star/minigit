package in.nitw.minigit.health;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smoke test for the whole stack: React -> Spring -> MySQL. */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP", "db", one != null && one == 1 ? "UP" : "DOWN");
    }
}
