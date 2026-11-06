package in.nitw.minigit.readmodel;

/**
 * Shade of one square in the contribution heatmap, like GitHub's "Less ... More" legend.
 *
 * <p>An enum that carries behaviour: {@link #of(int, int)} decides the level from a day's count
 * relative to the busiest day in the period, so a quiet user still sees a readable graph.
 */
public enum ContributionLevel {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    MAX(4);

    private final int intensity;

    ContributionLevel(int intensity) {
        this.intensity = intensity;
    }

    /** 0 to 4. The frontend maps this straight to a colour. */
    public int intensity() {
        return intensity;
    }

    /**
     * @param count   contributions on the day (0 means none)
     * @param busiest the largest daily count in the period (the 100% mark)
     */
    public static ContributionLevel of(int count, int busiest) {
        if (count <= 0 || busiest <= 0) return NONE;
        double ratio = Math.min(1.0, (double) count / busiest);
        if (ratio <= 0.25) return LOW;
        if (ratio <= 0.50) return MEDIUM;
        if (ratio <= 0.75) return HIGH;
        return MAX;
    }
}
