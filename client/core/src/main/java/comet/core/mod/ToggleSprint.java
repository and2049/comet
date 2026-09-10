package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import java.util.Arrays;
import java.util.List;

public final class ToggleSprint extends HudMod {
    private static final int PADDING = 3;
    private static final Option AUTO_SPRINT = new Option("autoSprint", "Sprint when joining a world", true);
    private static final Option TOGGLE_SNEAK = new Option("toggleSneak", "Toggle sneak", false);
    private static final Option SHOW_TEXT = new Option("showText", "Show status text", true);
    private final GameHost host;
    private boolean sprintToggled;
    private boolean sneakToggled;
    private boolean wasInWorld;

    public ToggleSprint(GameHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "toggleSprint";
    }

    @Override
    public String name() {
        return "Toggle Sprint / Sneak";
    }

    @Override
    public String description() {
        return "Press sprint or sneak once to keep it held.";
    }

    @Override
    public Category category() {
        return Category.MECHANIC;
    }

    @Override
    public String glyph() {
        return "TS";
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(AUTO_SPRINT, TOGGLE_SNEAK, SHOW_TEXT);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public float defaultY() {
        return 0.92F;
    }

    public boolean holdsSprint() {
        return host.inWorld() && !host.screenOpen() && sprintToggled;
    }

    public boolean holdsSneak() {
        return host.inWorld() && !host.screenOpen() && sneakToggled && option(TOGGLE_SNEAK);
    }

    @Override
    public void reset() {
        sprintToggled = false;
        sneakToggled = false;
        wasInWorld = false;
        host.setSprinting(false);
    }

    @Override
    public void tick() {
        boolean inWorld = host.inWorld();
        if (inWorld && !wasInWorld) {
            sprintToggled = option(AUTO_SPRINT);
        } else if (!inWorld) {
            sprintToggled = false;
            sneakToggled = false;
        }
        wasInWorld = inWorld;
        if (!option(TOGGLE_SNEAK)) {
            sneakToggled = false;
        }
    }

    @Override
    public void keyState(int key, boolean pressed) {
        if (!host.inWorld() || host.screenOpen()) {
            return;
        }
        if (key == host.sprintKey()) {
            if (pressed) {
                sprintToggled = !sprintToggled;
                if (!sprintToggled) {
                    host.setSprinting(false);
                }
            } else if (!sprintToggled) {
                host.setSprinting(false);
            }
        } else if (key == host.sneakKey() && pressed && option(TOGGLE_SNEAK)) {
            sneakToggled = !sneakToggled;
        }
    }

    private String label() {
        if (holdsSneak()) {
            return "[Sneaking (Toggled)]";
        }
        if (sprintToggled) {
            return "[Sprinting (Toggled)]";
        }
        if (host.sprinting()) {
            return "[Sprinting (Key Held)]";
        }
        return preview ? "[Sprinting (Toggled)]" : "";
    }

    @Override
    public int width(Canvas canvas) {
        String label = label();
        return label.isEmpty() || !option(SHOW_TEXT) ? 0 : canvas.textWidth(label) + PADDING * 2;
    }

    @Override
    public int height(Canvas canvas) {
        return width(canvas) == 0 ? 0 : canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        int width = width(canvas);
        if (width == 0) {
            return;
        }
        canvas.roundedFill(0, 0, width, height(canvas), 3, 0x70000000);
        canvas.text(label(), PADDING, PADDING, 0xFFFFFFFF, true);
    }
}
