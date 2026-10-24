package in.nitw.minigit.storage.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface DiffAlgorithm {

    List<DiffLine> diff(List<String> oldLines, List<String> newLines);

    default List<DiffLine> diff(String oldText, String newText) {
        return diff(splitLines(oldText), splitLines(newText));
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
        // "a\nb\n" splits into [a, b, ""] so drop the trailing empty element
        if (lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}