package comet.core.anim;

public final class HurtShake {
    private static final float DIVISOR = 1.15F;

    private HurtShake() {
    }

    public static float remap(float angle) {
        float scaled = (float) Math.sqrt(Math.sqrt(angle / Math.PI));
        scaled /= DIVISOR;
        return (float) (scaled * scaled * scaled * scaled * Math.PI);
    }
}
