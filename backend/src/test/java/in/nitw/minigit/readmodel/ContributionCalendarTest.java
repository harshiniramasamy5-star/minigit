package in.nitw.minigit.readmodel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContributionCalendarTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Test
    void levelsAreRelativeToTheBusiestDay() {
        assertEquals(ContributionLevel.NONE, ContributionLevel.of(0, 8));
        assertEquals(ContributionLevel.LOW, ContributionLevel.of(1, 8));
        assertEquals(ContributionLevel.MEDIUM, ContributionLevel.of(4, 8));
        assertEquals(ContributionLevel.HIGH, ContributionLevel.of(6, 8));
        assertEquals(ContributionLevel.MAX, ContributionLevel.of(8, 8));
        assertEquals(ContributionLevel.NONE, ContributionLevel.of(3, 0));
    }

    @Test
    void streaksAndBusiestDay() {
        ContributionCalendar cal = new ContributionCalendar(FROM, TO, Map.of(
                LocalDate.of(2026, 3, 1), 1,
                LocalDate.of(2026, 3, 2), 5,
                LocalDate.of(2026, 3, 3), 2,
                LocalDate.of(2026, 3, 10), 5));

        ContributionCalendar.Stats s = cal.stats(LocalDate.of(2026, 3, 11));
        assertEquals(4, s.activeDays());
        assertEquals(3, s.longestStreak());
        assertEquals(5, s.busiestCount());
        assertEquals("2026-03-02", s.busiestDay());               // earliest of the tie
        assertEquals(1, s.currentStreak());                       // Mar 10 active, Mar 11 empty but still alive
        assertEquals(0, cal.stats(LocalDate.of(2026, 3, 13)).currentStreak());
    }

    @Test
    void daysOutsideThePeriodAndZerosAreIgnored() {
        ContributionCalendar cal = new ContributionCalendar(FROM, TO, Map.of(
                LocalDate.of(2025, 12, 31), 9,
                LocalDate.of(2026, 5, 5), 0));
        assertEquals(0, cal.stats(TO).activeDays());
        assertNull(cal.stats(TO).busiestDay());
        assertEquals(ContributionLevel.NONE, cal.levelOn(LocalDate.of(2026, 5, 5)));
    }

    @Test
    void rejectsBackwardsPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new ContributionCalendar(TO, FROM, Map.of()));
    }
}
