package in.nitw.minigit.blame;

import in.nitw.minigit.storage.diff.DiffLine;
import in.nitw.minigit.storage.diff.MyersDiff;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Line attribution. Starting at a commit, every line is pushed back to the oldest commit it came from:
 * a line equal (by Myers diff) to a line in a parent goes to that parent's history, parents tried in order
 * (first parent wins); a line found in no parent belongs to the commit itself. For a merge commit that means
 * lines from the second parent are credited to that side and only conflict-resolution edits to the merge.
 * Pure logic, no storage, so it can be unit tested.
 */
public final class BlameEngine {

    public interface Source {
        /** The file's lines at that commit, or null if the file isn't there (or can't be read as text). */
        List<String> lines(String commit);
        List<String> parents(String commit);
    }

    public static class TooDeep extends RuntimeException {
        public TooDeep(String m) { super(m); }
    }

    private record State(String commit, List<String> lines, int[] origin) {}

    private BlameEngine() {}

    /** @return for each line of the file at {@code start}, the hash of the commit that introduced it; null if absent. */
    public static String[] blame(String start, Source src, int maxSteps) {
        List<String> startLines = src.lines(start);
        if (startLines == null) return null;
        String[] out = new String[startLines.size()];
        int[] identity = new int[out.length];
        for (int i = 0; i < identity.length; i++) identity[i] = i;

        MyersDiff myers = new MyersDiff();
        Deque<State> work = new ArrayDeque<>();
        work.push(new State(start, startLines, identity));
        int steps = 0;

        while (!work.isEmpty()) {
            if (++steps > maxSteps) {
                throw new TooDeep("History is too long to blame (more than " + maxSteps + " steps)");
            }
            State s = work.pop();
            boolean[] claimed = new boolean[s.lines().size()];

            for (String parent : src.parents(s.commit())) {
                List<String> pl = src.lines(parent);
                if (pl == null) continue;
                int[] porigin = new int[pl.size()];
                Arrays.fill(porigin, -1);
                boolean any = false;

                if (pl.equals(s.lines())) {                       // unchanged file: no diff needed
                    for (int i = 0; i < claimed.length; i++) {
                        if (s.origin()[i] >= 0 && !claimed[i]) {
                            claimed[i] = true;
                            porigin[i] = s.origin()[i];
                            any = true;
                        }
                    }
                } else {
                    int ai = 0, bi = 0;
                    for (DiffLine d : myers.diff(pl, s.lines())) {
                        switch (d.type()) {
                            case EQUAL -> {
                                if (s.origin()[bi] >= 0 && !claimed[bi]) {
                                    claimed[bi] = true;
                                    porigin[ai] = s.origin()[bi];
                                    any = true;
                                }
                                ai++;
                                bi++;
                            }
                            case INSERT -> bi++;
                            case DELETE -> ai++;
                        }
                    }
                }
                if (any) work.push(new State(parent, pl, porigin));
            }
            for (int i = 0; i < claimed.length; i++) {
                if (s.origin()[i] >= 0 && !claimed[i]) out[s.origin()[i]] = s.commit();
            }
        }
        return out;
    }
}
