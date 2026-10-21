package comet.core.mod;

public final class NumberOption {
    public final String id;
    public final String label;
    public final int defaultValue;
    public final int min;
    public final int max;
    public final String unit;
    private final String[] names;
    public final boolean keyBinding;
    private final int divisor;

    public NumberOption(String id, String label, int defaultValue, int min, int max) {
        this(id, label, defaultValue, min, max, "x");
    }

    public NumberOption(String id, String label, int defaultValue, int min, int max, String unit, String... names) {
        this(id, label, defaultValue, min, max, unit, 1, false, names);
    }

    private NumberOption(String id, String label, int defaultValue, int min, int max, String unit, int divisor, boolean keyBinding, String... names) {
        this.id = id;
        this.label = label;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.names = names.clone();
        this.divisor = divisor;
        this.keyBinding = keyBinding;
    }

    public static NumberOption keybind(String id, String label) {
        return new NumberOption(id, label, 0, -100, 255, "", 1, true);
    }

    public static NumberOption decimal(String id, String label, double defaultValue, double min, double max, int places) {
        if (places < 0 || places > 4 || !Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(defaultValue) || min > max) {
            throw new IllegalArgumentException("Invalid decimal option");
        }
        int divisor = (int) Math.pow(10, places);
        return new NumberOption(id, label, (int) Math.round(defaultValue * divisor), (int) Math.round(min * divisor),
                (int) Math.round(max * divisor), "", divisor, false);
    }

    public double value(int raw) {
        return clamp(raw) / (double) divisor;
    }

    public int step() {
        return Math.max(1, divisor / 10);
    }

    public String display(int value) {
        int bounded = clamp(value);
        if (keyBinding) return bounded == 0 ? "Unbound" : Integer.toString(bounded);
        if (divisor != 1) return java.math.BigDecimal.valueOf(bounded).divide(java.math.BigDecimal.valueOf(divisor)).stripTrailingZeros().toPlainString() + unit;
        int index = bounded - min;
        return index < names.length ? names[index] : bounded + unit;
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
