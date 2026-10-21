package comet.core.anim;

public final class HealthBar {
    private HealthBar() {
    }

    public static int threshold(int displayedHealth, boolean animation) {
        return animation ? 0 : displayedHealth;
    }
}
