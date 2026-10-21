package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.MovementBackend;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

public final class ToggleSprint extends HudMod {
    public static final int PRESS = 0;
    public static final int TAP = 1;
    private static final int PADDING = 3;
    private static final int ICON = 9;
    private static final int ICON_GAP = 2;
    private static final long NO_PRESS = Long.MIN_VALUE;
    private static final long SNEAK_TAP = 200L;
    private static final long SPRINT_TAP = 400L;
    private static final float FLY_SPEED = 0.05F;
    private static final double BOOST_MOTION = 0.15D;

    public static final Option TOGGLE_SPRINT = new Option("toggleSprint", "Toggle sprint", true);
    public static final Option AUTO_SPRINT = new Option("autoSprint", "Sprint when joining a world", true);
    public static final Option TOGGLE_SNEAK = new Option("toggleSneak", "Toggle sneak", false);
    public static final Option SPRINT_OVERRIDE = new Option("sprintKeybindOverride", "Override sprint key", false);
    public static final Option SNEAK_OVERRIDE = new Option("sneakKeybindOverride", "Override sneak key", false);
    public static final Option CONTAINER_SNEAK = new Option("toggleSneakContainer", "Sneak in containers", false);
    public static final Option FLY_BOOST = new Option("flyBoost", "Creative fly boost", false);
    public static final Option DOUBLE_TAP = new Option("doubleTap", "Double-tap sprint", true);
    public static final Option SHOW_TEXT = new Option("showText", "Show status text", true);
    public static final Option SHOW_RIDING = new Option("showRidingText", "Riding status", true);
    public static final Option SHOW_DESCENDING = new Option("descendingText", "Descending status", true);
    public static final Option ICON_MODE = new Option("iconMode", "Status icon", false);
    public static final NumberOption FLY_BOOST_AMOUNT = new NumberOption("flyBoostAmount", "Fly boost", 4, 2, 8);
    public static final NumberOption TOGGLE_MODE = new NumberOption("toggleMode", "Toggle mode", PRESS, PRESS, TAP, "",
            "Press", "Tap");
    public static final NumberOption KEYBIND_SPRINT = NumberOption.keybind("keybindSprint", "Sprint toggle key");
    public static final NumberOption KEYBIND_SNEAK = NumberOption.keybind("keybindSneak", "Sneak toggle key");
    public static final TextOption SPRINTING_TEXT = new TextOption("sprintingText", "Sprint text", "Sprinting", 24);
    public static final TextOption SNEAKING_TEXT = new TextOption("sneakingText", "Sneak text", "Sneaking", 24);
    public static final TextOption FLYING_TEXT = new TextOption("flyingText", "Flying text", "Flying", 24);

    private final GameHost host;
    private final LongSupplier clock;
    private boolean sprintToggled;
    private boolean sneakToggled;
    private long sprintPressedAt = NO_PRESS;
    private int sprintPressedKey;
    private int sprintPressedBinding;
    private long sneakPressedAt = NO_PRESS;
    private int sneakPressedKey;
    private int sneakPressedBinding;
    private boolean wasInWorld;
    private boolean hypixel;
    private Float savedFlySpeed;

    public ToggleSprint(GameHost host) {
        this(host, () -> System.nanoTime() / 1000000L);
    }

    ToggleSprint(GameHost host, LongSupplier clock) {
        this.host = host;
        this.clock = clock;
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
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public float defaultY() {
        return 0.92F;
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(TOGGLE_SPRINT, AUTO_SPRINT, TOGGLE_SNEAK, SPRINT_OVERRIDE, SNEAK_OVERRIDE,
                CONTAINER_SNEAK, FLY_BOOST, DOUBLE_TAP, SHOW_TEXT, SHOW_RIDING, SHOW_DESCENDING, ICON_MODE);
    }

    @Override
    public List<NumberOption> numbers() {
        return Arrays.asList(TOGGLE_MODE, FLY_BOOST_AMOUNT, KEYBIND_SPRINT, KEYBIND_SNEAK);
    }

    @Override
    public List<TextOption> texts() {
        return Arrays.asList(SPRINTING_TEXT, SNEAKING_TEXT, FLYING_TEXT);
    }

    public int mode() {
        return number(TOGGLE_MODE);
    }

    public int sprintToggleKey() {
        return option(SPRINT_OVERRIDE) ? number(KEYBIND_SPRINT) : host.sprintKey();
    }

    public int sneakToggleKey() {
        return option(SNEAK_OVERRIDE) ? number(KEYBIND_SNEAK) : host.sneakKey();
    }

    public boolean holdsSprint() {
        return host.inWorld() && !host.screenOpen() && option(TOGGLE_SPRINT) && (sprintToggled || sprintHeld());
    }

    public boolean holdsSneak() {
        if (!host.inWorld() || !option(TOGGLE_SNEAK) || (!sneakToggled && !sneakHeld())) {
            return false;
        }
        return !host.screenOpen() || containerSneak();
    }

    public void releaseKeys() {
        clearPresses();
    }

    public boolean allowsSprint(boolean sprinting) {
        if (!sprinting || option(DOUBLE_TAP)) {
            return true;
        }
        return holdsSprint() || host.keyDown(host.sprintKey());
    }

    public void movement() {
        MovementBackend movement = backend();
        if (!boosting(movement)) {
            restoreFlySpeed();
            return;
        }
        if (savedFlySpeed == null) {
            savedFlySpeed = movement.flySpeed();
        }
        int amount = number(FLY_BOOST_AMOUNT);
        movement.setFlySpeed(FLY_SPEED * amount);
        if (movement.sneakInputHeld()) {
            movement.addMotionY(-BOOST_MOTION * amount);
        }
        if (movement.jumpInputHeld()) {
            movement.addMotionY(BOOST_MOTION * amount);
        }
    }

    @Override
    public void reset() {
        sprintToggled = false;
        sneakToggled = false;
        clearPresses();
        wasInWorld = false;
        hypixel = false;
        restoreFlySpeed();
        host.setSprinting(false);
    }

    @Override
    public void tick() {
        boolean inWorld = host.inWorld();
        if (inWorld && !wasInWorld) {
            sprintToggled = option(TOGGLE_SPRINT) && option(AUTO_SPRINT);
        } else if (!inWorld) {
            sprintToggled = false;
            sneakToggled = false;
        }
        wasInWorld = inWorld;
        if (!option(TOGGLE_SPRINT)) {
            sprintToggled = false;
        }
        if (!option(TOGGLE_SNEAK)) {
            sneakToggled = false;
        }
        if (!inWorld || host.screenOpen()) {
            clearPresses();
        }
        syncBindings();
        MovementBackend movement = backend();
        hypixel = isHypixel(movement);
        if (!boosting(movement)) {
            restoreFlySpeed();
        }
    }

    @Override
    public void keyState(int key, boolean pressed) {
        if (!host.inWorld() || host.screenOpen()) {
            clearPresses();
            return;
        }
        syncBindings();
        int sprintKey = sprintToggleKey();
        int sneakKey = sneakToggleKey();
        if (key == sprintKey) {
            if (pressed) {
                sprintPressedAt = clock.getAsLong();
                sprintPressedKey = key;
                sprintPressedBinding = sprintKey;
                if (mode() == PRESS && option(TOGGLE_SPRINT)) {
                    toggleSprint();
                }
            } else if (mode() == PRESS) {
                if (!sprintToggled) {
                    host.setSprinting(false);
                }
            } else {
                releaseTap(sprintPressedAt, SPRINT_TAP, false);
                clearSprintPress();
            }
        } else if (key == sneakKey) {
            if (pressed) {
                sneakPressedAt = clock.getAsLong();
                sneakPressedKey = key;
                sneakPressedBinding = sneakKey;
                if (mode() == PRESS && option(TOGGLE_SNEAK)) {
                    sneakToggled = !sneakToggled;
                }
            } else if (mode() == TAP) {
                releaseTap(sneakPressedAt, SNEAK_TAP, true);
                clearSneakPress();
            }
        }
    }

    private void releaseTap(long pressedAt, long limit, boolean sneak) {
        if (pressedAt == NO_PRESS || backend().flying()) {
            return;
        }
        long elapsed = clock.getAsLong() - pressedAt;
        if (elapsed < 0 || elapsed > limit) {
            return;
        }
        if (sneak) {
            if (option(TOGGLE_SNEAK)) {
                sneakToggled = !sneakToggled;
            }
        } else if (option(TOGGLE_SPRINT)) {
            toggleSprint();
        }
    }

    private void toggleSprint() {
        sprintToggled = !sprintToggled;
        if (!sprintToggled) {
            host.setSprinting(false);
        }
    }

    private void clearPresses() {
        clearSprintPress();
        clearSneakPress();
    }

    private void clearSprintPress() {
        sprintPressedAt = NO_PRESS;
        sprintPressedKey = 0;
        sprintPressedBinding = 0;
    }

    private void clearSneakPress() {
        sneakPressedAt = NO_PRESS;
        sneakPressedKey = 0;
        sneakPressedBinding = 0;
    }

    private void syncBindings() {
        if (sprintPressedKey != 0 && sprintToggleKey() != sprintPressedBinding) {
            clearSprintPress();
        }
        if (sneakPressedKey != 0 && sneakToggleKey() != sneakPressedBinding) {
            clearSneakPress();
        }
    }

    private boolean sprintHeld() {
        return mode() == TAP && sprintPressedKey != 0 && sprintPressedKey == sprintToggleKey()
                && host.keyDown(sprintPressedKey);
    }

    private boolean sneakHeld() {
        return mode() == TAP && sneakPressedKey != 0 && sneakPressedKey == sneakToggleKey()
                && host.keyDown(sneakPressedKey);
    }

    private boolean containerSneak() {
        return option(CONTAINER_SNEAK) && !hypixel && backend().containerOpen();
    }

    private static boolean isHypixel(MovementBackend movement) {
        String address = movement.serverAddress();
        return address != null && address.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    private boolean boosting(MovementBackend movement) {
        return option(FLY_BOOST) && host.inWorld() && !host.screenOpen()
                && (movement.creative() || movement.spectator()) && movement.flying() && movement.sprintKeyHeld();
    }

    private void restoreFlySpeed() {
        if (savedFlySpeed == null) {
            return;
        }
        float original = savedFlySpeed;
        savedFlySpeed = null;
        backend().setFlySpeed(original);
    }

    private MovementBackend backend() {
        MovementBackend movement = host.movement();
        return movement == null ? MovementBackend.NONE : movement;
    }

    private boolean sprintKeyHeld() {
        int key = sprintToggleKey();
        return key != 0 && host.keyDown(key);
    }

    private String label() {
        if (!option(SHOW_TEXT)) {
            return "";
        }
        MovementBackend movement = backend();
        List<String> parts = new ArrayList<String>();
        if (movement.flying()) {
            parts.add(text(FLYING_TEXT));
        } else if (movement.riding()) {
            if (option(SHOW_RIDING)) {
                parts.add("Riding");
            }
        } else if (option(SHOW_DESCENDING) && movement.descending()) {
            parts.add("Descending");
        }
        if (sneakToggled && holdsSneak()) {
            parts.add(text(SNEAKING_TEXT) + " (Toggled)");
        } else if (movement.sneaking() || sneakHeld()) {
            parts.add(text(SNEAKING_TEXT) + " (Key Held)");
        }
        if (sprintToggled) {
            parts.add(text(SPRINTING_TEXT) + " (Toggled)");
        } else if (host.sprinting() || sprintKeyHeld()) {
            parts.add(text(SPRINTING_TEXT) + " (Key Held)");
        }
        if (parts.isEmpty()) {
            return preview ? "[" + text(SPRINTING_TEXT) + " (Toggled)]" : "";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(parts.get(index));
        }
        return builder.append(']').toString();
    }

    @Override
    public int width(Canvas canvas) {
        String label = label();
        if (label.isEmpty()) {
            return 0;
        }
        int text = canvas.textWidth(label);
        return (option(ICON_MODE) ? Math.max(text, ICON) : text) + PADDING * 2;
    }

    @Override
    public int height(Canvas canvas) {
        if (width(canvas) == 0) {
            return 0;
        }
        return (option(ICON_MODE) ? ICON + ICON_GAP : 0) + canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        int width = width(canvas);
        if (width == 0) {
            return;
        }
        background(canvas, width, height(canvas));
        int y = PADDING;
        if (option(ICON_MODE)) {
            icon(canvas, PADDING + (width - PADDING * 2 - ICON) / 2, PADDING, backend().sneaking());
            y += ICON + ICON_GAP;
        }
        text(canvas, label(), PADDING, y);
    }

    private void icon(Canvas canvas, int x, int y, boolean sneaking) {
        int color = 0xFFFFFFFF;
        if (sneaking) {
            int[][] arms = {{0, 0}, {2, 2}, {4, 4}, {6, 2}, {8, 0}};
            for (int[] point : arms) {
                canvas.fill(x + point[0], y + point[1], 2, 2, color);
            }
            return;
        }
        canvas.fill(x, y, ICON, 2, color);
        canvas.fill(x, y + 4, 6, 2, color);
        canvas.fill(x, y + 8, 3, 2, color);
    }
}
