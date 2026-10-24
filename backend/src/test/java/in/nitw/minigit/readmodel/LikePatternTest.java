package in.nitw.minigit.readmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LikePatternTest {

    @Test void blankMeansNoFilter() {
        assertNull(LikePattern.contains(null));
        assertNull(LikePattern.contains(""));
        assertNull(LikePattern.contains("   "));
    }

    @Test void plainTextIsWrappedAndTrimmed() {
        assertEquals("%minigit%", LikePattern.contains("  minigit "));
    }

    @Test void wildcardsAreEscaped() {
        assertEquals("%!%%", LikePattern.contains("%"));
        assertEquals("%a!_b%", LikePattern.contains("a_b"));
        assertEquals("%100!%!!%", LikePattern.contains("100%!"));
    }

    @Test void longQueriesAreCut() {
        String q = "x".repeat(500);
        assertEquals(LikePattern.MAX_QUERY_LENGTH + 2, LikePattern.contains(q).length());
    }
}
