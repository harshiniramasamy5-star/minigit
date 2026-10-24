package in.nitw.minigit.storage.diff;

public record DiffLine(DiffType type, String text) {
    @Override
    public String toString() {
        String prefix = switch (type) {
            case EQUAL -> "  ";
            case INSERT -> "+ ";
            case DELETE -> "- ";
        };
        return prefix + text;
    }
}
