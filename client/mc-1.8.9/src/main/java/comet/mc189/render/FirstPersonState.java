package comet.mc189.render;

public final class FirstPersonState {
    private static boolean suppressModelTransform;

    private FirstPersonState() {
    }

    public static boolean push() {
        boolean previous = suppressModelTransform;
        suppressModelTransform = true;
        return previous;
    }

    public static void restore(boolean previous) {
        suppressModelTransform = previous;
    }

    public static boolean suppressed() {
        return suppressModelTransform;
    }
}
