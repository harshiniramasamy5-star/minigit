package com.minigit.storage.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MyersDiffTest {

    private final DiffAlgorithm diff = new MyersDiff();

    @Test
    void identicalTextsAreAllEqual() {
        List<DiffLine> result = diff.diff("a\nb\nc", "a\nb\nc");
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(l -> l.type() == DiffType.EQUAL));
    }

    @Test
    void bothEmptyGivesEmptyDiff() {
        assertTrue(diff.diff("", "").isEmpty());
    }

    @Test
    void allInsertedWhenOldIsEmpty() {
        List<DiffLine> result = diff.diff("", "a\nb");
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> l.type() == DiffType.INSERT));
    }

    @Test
    void allDeletedWhenNewIsEmpty() {
        List<DiffLine> result = diff.diff("a\nb", "");
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> l.type() == DiffType.DELETE));
    }

    @Test
    void classicMyersExample() {
        List<DiffLine> result = diff.diff(
                List.of("A", "B", "C", "A", "B", "B", "A"),
                List.of("C", "B", "A", "B", "A", "C"));

        long deletes = result.stream().filter(l -> l.type() == DiffType.DELETE).count();
        long inserts = result.stream().filter(l -> l.type() == DiffType.INSERT).count();
        assertEquals(5, deletes + inserts);   // shortest edit script has D = 5
    }

    @Test
    void rebuildingFromDiffGivesNewText() {
        List<String> oldL = List.of("one", "two", "three", "four");
        List<String> newL = List.of("one", "2", "three", "four", "five");

        List<DiffLine> result = diff.diff(oldL, newL);

        List<String> rebuiltNew = result.stream()
                .filter(l -> l.type() != DiffType.DELETE).map(DiffLine::text).toList();
        List<String> rebuiltOld = result.stream()
                .filter(l -> l.type() != DiffType.INSERT).map(DiffLine::text).toList();

        assertEquals(newL, rebuiltNew);
        assertEquals(oldL, rebuiltOld);
    }
}