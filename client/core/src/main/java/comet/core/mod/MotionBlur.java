package comet.core.mod;

import java.util.Arrays;
import java.util.List;

public final class MotionBlur extends Mod {
    public static final int HYBRID = 3;
    public static final int DECAYING_MAX = 4;
    public static final int FRAME_BLEND = 5;
    public static final int CAPPED_BLEND = 6;
    public static final NumberOption ALGORITHM = new NumberOption("algorithm", "Algorithm", HYBRID, HYBRID, CAPPED_BLEND, "",
            "Hybrid", "Decaying Max", "Frame Blend", "Capped Blend");
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
        return "Frame-history trails or hybrid camera motion blur.";
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

    public boolean hybrid() {
        return number(ALGORITHM) == HYBRID;
    }

    public float frameFactor() {
        int strength = number(STRENGTH);
        switch (number(ALGORITHM)) {
            case DECAYING_MAX: return 0.7F + (strength / 100F) * 3F - 0.01F;
            case FRAME_BLEND: return 1F - Math.min(strength / 10F, 0.99F);
            default: return strength / 10F;
        }
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
