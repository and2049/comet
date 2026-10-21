package comet.core.mod;

import comet.core.bridge.LightingBackend;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Lighting extends Mod {
    public static final int LIGHTMAP = 0;
    public static final int GAMMA = 1;
    public static final float FULLBRIGHT_GAMMA = 100F;
    public static final Option FULLBRIGHT = new Option("fullbright", "Fullbright", true);
    public static final NumberOption MULTIPLIER = new NumberOption("multiplier", "Brightness multiplier", 2, 1, 10);
    public static final NumberOption BOOST = NumberOption.decimal("brightnessBoost", "Gamma boost", 1.0, 1.0, 10.0, 2);
    public static final NumberOption METHOD = new NumberOption("method", "Brightness method", LIGHTMAP, LIGHTMAP, GAMMA, "", "Lightmap", "Gamma");
    public static final NumberOption TOGGLE = NumberOption.keybind("fullBrightToggle", "Fullbright toggle");
    private final LightingBackend backend;
    private boolean overridden;
    private boolean suspended;
    private int method = -1;
    private String status = "";

    public Lighting() {
        this(LightingBackend.NONE);
    }

    public Lighting(LightingBackend backend) {
        this.backend = backend == null ? LightingBackend.NONE : backend;
    }

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
        return Arrays.asList(BOOST, MULTIPLIER, METHOD, TOGGLE);
    }

    public LightingBackend backend() {
        return backend;
    }

    public String status() {
        return status;
    }

    public boolean gammaMethod() {
        return number(METHOD) == GAMMA;
    }

    public float gammaRequest() {
        return option(FULLBRIGHT) ? FULLBRIGHT_GAMMA : (float) decimal(BOOST);
    }

    public void apply(int[] colors) {
        if (gammaMethod()) {
            return;
        }
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

    @Override
    public void tick() {
        sync();
    }

    @Override
    public void keyState(int key, boolean pressed) {
        if (!pressed || key == 0 || key != number(TOGGLE)) {
            return;
        }
        setOption(FULLBRIGHT, !option(FULLBRIGHT));
        sync();
    }

    @Override
    public void reset() {
        release();
        suspended = false;
        status = "";
    }

    private void sync() {
        int selected = number(METHOD);
        if (selected != method) {
            method = selected;
            suspended = false;
        }
        if (selected != GAMMA) {
            release();
            report("");
            return;
        }
        if (!backend.available()) {
            release();
            report("Gamma brightness needs a version bridge.");
            return;
        }
        if (suspended) {
            release();
            return;
        }
        if (backend.consumeUserGammaEdit()) {
            release();
            try {
                disable();
            } catch (IllegalStateException failure) {
                System.err.println("[Comet] Lighting disable could not be saved: " + failure.getMessage());
            }
            suspended = true;
            report("Gamma brightness disabled: the vanilla gamma setting changed.");
            return;
        }
        if (backend.shadersActive()) {
            release();
            report("Gamma brightness paused: a shader pack is active.");
            return;
        }
        backend.setGammaOverride(gammaRequest());
        overridden = true;
        report("");
    }

    private void report(String message) {
        if (message.equals(status)) {
            return;
        }
        status = message;
        if (!message.isEmpty()) {
            System.err.println("[Comet] " + message);
        }
    }

    private void release() {
        if (overridden) {
            backend.removeGammaOverride();
            overridden = false;
        }
    }
}
