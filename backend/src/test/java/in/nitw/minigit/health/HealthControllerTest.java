package in.nitw.minigit.health;

import in.nitw.minigit.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private static AppProperties props(boolean openDev) {
        return new AppProperties(
                new AppProperties.Storage("./minigit-data/repos"),
                new AppProperties.Security(openDev),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("x".repeat(32), 30));
    }

    private static JdbcTemplate dbUp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        return jdbc;
    }

    @Test
    void reportsDevAuthWhileOpenDevIsOn() {
        Map<String, Object> body = new HealthController(dbUp(), props(true)).health();
        assertEquals("UP", body.get("status"));
        assertEquals("UP", body.get("db"));
        assertEquals(true, body.get("devAuth"));
    }

    @Test
    void hidesDevAuthOnceRealAuthIsOn() {
        assertEquals(false, new HealthController(dbUp(), props(false)).health().get("devAuth"));
    }
}
