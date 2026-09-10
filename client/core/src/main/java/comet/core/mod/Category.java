package comet.core.mod;

public enum Category {
    HUD("HUD"),
    MECHANIC("MECHANIC"),
    VISUAL("VISUAL");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
