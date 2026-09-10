package comet.core.mod;

public final class NumberOption {
    public final String id;
    public final String label;
    public final int defaultValue;
    public final int min;
    public final int max;
    public final String unit;
    private final String[] names;

    public NumberOption(String id, String label, int defaultValue, int min, int max) {
        this(id, label, defaultValue, min, max, "x");
    }

    public NumberOption(String id, String label, int defaultValue, int min, int max, String unit, String... names) {
        this.id = id;
        this.label = label;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.names = names.clone();
    }

    public String display(int value) {
        int bounded = clamp(value);
        int index = bounded - min;
        return index < names.length ? names[index] : bounded + unit;
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
