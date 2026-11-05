package in.nitw.minigit.blame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlameEngineTest {

    private static BlameEngine.Source src(Map<String, List<String>> files, Map<String, List<String>> parents) {
        return new BlameEngine.Source() {
            public List<String> lines(String c) { return files.get(c); }
            public List<String> parents(String c) { return parents.getOrDefault(c, List.of()); }
        };
    }

    @Test
    void linearHistoryCreditsTheCommitThatAddedEachLine() {
        var s = src(Map.of("c1", List.of("a", "b"), "c2", List.of("a", "b", "c"), "c3", List.of("a", "X", "c")),
                Map.of("c2", List.of("c1"), "c3", List.of("c2")));
        assertArrayEquals(new String[]{"c1", "c3", "c2"}, BlameEngine.blame("c3", s, 50));
    }

    @Test
    void mergeCreditsEachSideAndConflictEditsToTheMerge() {
        // base: a,b  | left adds L | right adds R | merge keeps both and edits b -> B
        var s = src(Map.of("base", List.of("a", "b"), "left", List.of("a", "b", "L"), "right", List.of("R", "a", "b"),
                        "m", List.of("R", "a", "B", "L")),
                Map.of("left", List.of("base"), "right", List.of("base"), "m", List.of("left", "right")));
        assertArrayEquals(new String[]{"right", "base", "m", "left"}, BlameEngine.blame("m", s, 50));
    }

    @Test
    void fileCreatedInTheCommitBelongsToIt() {
        var s = src(Map.of("c1", List.of("x"), "c2", List.of("y")), Map.of("c2", List.of("c1")));
        assertArrayEquals(new String[]{"c2"}, BlameEngine.blame("c2", s, 50));
    }

    @Test
    void stepLimitIsEnforced() {
        var s = src(Map.of("c1", List.of("a"), "c2", List.of("a"), "c3", List.of("a")),
                Map.of("c3", List.of("c2"), "c2", List.of("c1")));
        assertThrows(BlameEngine.TooDeep.class, () -> BlameEngine.blame("c3", s, 2));
    }
}
