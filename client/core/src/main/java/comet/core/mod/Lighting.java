package comet.core.mod;

import java.util.Collections;
import java.util.List;

public final class Lighting extends Mod {
    public static final Option FULLBRIGHT = new Option("fullbright", "Fullbright", true);
    public static final NumberOption MULTIPLIER = new NumberOption("multiplier", "Brightness multiplier", 2, 1, 10);

    @Override
    public String id() {
        return "lighting";
    }

    @Override
    public String name() {
        return "Lighting";
    }

    @Override
    public String description() {
        return "Fullbright or multiplied lightmap brightness.";
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public List<Option> options() {
        return Collections.singletonList(FULLBRIGHT);
    }

    @Override
    public List<NumberOption> numbers() {
        return Collections.singletonList(MULTIPLIER);
    }

    public void apply(int[] colors) {
        boolean fullbright = option(FULLBRIGHT);
        int multiplier = number(MULTIPLIER);
        for (int index = 0; index < colors.length; index++) {
            int color = colors[index];
            int red = Math.min(255, ((color >>> 16) & 255) * multiplier);
            int green = Math.min(255, ((color >>> 8) & 255) * multiplier);
            int blue = Math.min(255, (color & 255) * multiplier);
            colors[index] = (color & 0xFF000000) | (fullbright ? 0xFFFFFF : (red << 16) | (green << 8) | blue);
        }
    }
}
