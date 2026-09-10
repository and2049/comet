package comet.core.mod;

import java.util.Arrays;
import java.util.List;

public final class OldAnimations extends Mod {
    private static final Option BLOCK_HIT = new Option("blockHit", "Block-hitting swing", true);
    private static final Option USE_SWING = new Option("useSwing", "Swing while eating, drinking or drawing", true);

    @Override
    public String id() {
        return "oldAnimations";
    }

    @Override
    public String name() {
        return "1.7 Visuals";
    }

    @Override
    public String description() {
        return "Brings the 1.7 first-person animations to 1.8.";
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String glyph() {
        return "1.7";
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(BLOCK_HIT, USE_SWING);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public boolean swings(boolean blocking) {
        return option(blocking ? BLOCK_HIT : USE_SWING);
    }
}
