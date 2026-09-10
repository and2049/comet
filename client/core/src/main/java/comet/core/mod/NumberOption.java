package comet.core.mod;

public final class NumberOption {
    public final String id;
    public final String label;
    public final int defaultValue;
    public final int min;
    public final int max;

    public NumberOption(String id, String label, int defaultValue, int min, int max) {
        this.id = id;
        this.label = label;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
