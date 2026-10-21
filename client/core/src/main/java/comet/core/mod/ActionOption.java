package comet.core.mod;

public final class ActionOption {
    public final String id;
    public final String label;
    private final Runnable action;

    public ActionOption(String id, String label, Runnable action) {
        this.id = id;
        this.label = label;
        this.action = action;
    }

    public void run() {
        action.run();
    }
}
