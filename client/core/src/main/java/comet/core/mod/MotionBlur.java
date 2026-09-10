package comet.core.mod;

import java.util.Arrays;
import java.util.List;

public final class MotionBlur extends Mod {
    public static final int HYBRID = 3;
    public static final NumberOption ALGORITHM = new NumberOption("algorithm", "Algorithm", HYBRID, HYBRID, HYBRID, "", "Hybrid");
    public static final NumberOption STRENGTH = new NumberOption("strength", "Strength", 5, 1, 10, "");
    public static final int TAPS = 32;
    public static final float MAX_BLUR = 0.1F;

    @Override
    public String id() {
        return "motionBlur";
    }

    @Override
    public String name() {
        return "Motion Blur";
    }

    @Override
    public String description() {
        return "Camera motion blur with a short frame-history trail.";
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public List<NumberOption> numbers() {
        return Arrays.asList(ALGORITHM, STRENGTH);
    }

    public int trailStrength() {
        return (number(STRENGTH) + 1) / 2;
    }

    public float shutterMs() {
        return trailMs(number(STRENGTH));
    }

    public static float trailMs(int strength) {
        return 8F * strength + 4F;
    }

    public static float weight(int strength, double ageMs) {
        return (float) Math.exp(-Math.max(0, ageMs) / trailMs(strength));
    }
}
