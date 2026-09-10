package comet.core.mod;

public final class Option {
    public final String id;
    public final String label;
    public final boolean defaultValue;

    public Option(String id, String label, boolean defaultValue) {
        this.id = id;
        this.label = label;
        this.defaultValue = defaultValue;
    }
}
