package in.nitw.minigit.readmodel;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable value object: every day of a period with how many contributions happened on it.
 *
 * <p>Encapsulates the "how busy was this person" rules (levels, streaks, busiest day) so
 * {@link ActivityReadQueries} only has to run SQL. Pure Java, no Spring, so it is unit tested
 * without a database.
 */
public final class ContributionCalendar {

    /** Summary numbers shown under the heatmap. {@code busiestDay} is null when there is no activity. */
    public record Stats(int activeDays, int longestStreak, int currentStreak,
                        String busiestDay, int busiestCount) {}

    private final LocalDate from;
    private final LocalDate to;
    private final Map<LocalDate, Integer> countsByDay;
    private final int busiest;

    /**
     * @param from   first day of the period (inclusive)
     * @param to     last day of the period (inclusive)
     * @param counts days that had at least one contribution; days outside from..to are ignored
     */
    public ContributionCalendar(LocalDate from, LocalDate to, Map<LocalDate, Integer> counts) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        this.from = from;
        this.to = to;
        TreeMap<LocalDate, Integer> copy = new TreeMap<>();
        counts.forEach((day, n) -> {
            if (n != null && n > 0 && !day.isBefore(from) && !day.isAfter(to)) copy.put(day, n);
        });
        this.countsByDay = Collections.unmodifiableMap(copy);
        this.busiest = copy.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public int countOn(LocalDate day) {
        return countsByDay.getOrDefault(day, 0);
    }

    public ContributionLevel levelOn(LocalDate day) {
        return ContributionLevel.of(countOn(day), busiest);
    }

    /**
     * @param today the reference "now". The current streak is the run of active days ending on
     *              {@code min(today, to)}; it is still alive if that day is empty but the day
     *              before was active (you have until midnight to keep it going).
     */
    public Stats stats(LocalDate today) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : countsByDay.keySet()) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }

        LocalDate end = today.isBefore(to) ? today : to;
        if (end.isBefore(from)) end = from;
        LocalDate cursor = countOn(end) > 0 ? end : end.minusDays(1);
        int current = 0;
        while (!cursor.isBefore(from) && countOn(cursor) > 0) {
            current++;
            cursor = cursor.minusDays(1);
        }

        LocalDate busiestDay = null;
        for (Map.Entry<LocalDate, Integer> e : countsByDay.entrySet()) {
            if (e.getValue() == busiest) { busiestDay = e.getKey(); break; }   // earliest wins a tie
        }
        return new Stats(countsByDay.size(), longest, current,
                busiestDay == null ? null : busiestDay.toString(), busiest);
    }
}
