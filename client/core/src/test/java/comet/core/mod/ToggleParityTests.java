package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.Control;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.bridge.MovementBackend;
import comet.core.bridge.VanillaScreen;
import comet.core.ui.Screen;
import comet.core.ui.TestCanvas;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ToggleParityTests {
    private static int assertions;

    public static void run(Path root) throws Exception {
        Files.createDirectories(root);
        legacy(root.resolve("legacy"));
        tap(root.resolve("tap"));
        holds(root.resolve("holds"));
        binds(root.resolve("binds"));
        containers(root.resolve("containers"));
        flight(root.resolve("flight"));
        status(root.resolve("status"));
        persistence(root.resolve("persistence"));
        System.out.println("Toggle sprint parity: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasOption(Mod mod, String id) {
        for (Option option : mod.options()) {
            if (option.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void legacy(Path root) {
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host);
        registry.register(toggle);
        check(toggle.id().equals("toggleSprint") && toggle.enabledByDefault(), "The mod keeps its identity and default state");
        check(hasOption(toggle, "autoSprint") && hasOption(toggle, "toggleSneak") && hasOption(toggle, "showText"),
                "Existing option keys are retained");
        check(toggle.option(ToggleSprint.AUTO_SPRINT) && !toggle.option(ToggleSprint.TOGGLE_SNEAK)
                && toggle.option(ToggleSprint.SHOW_TEXT), "Auto sprint and status text default on and toggle sneak stays opt-in");
        check(toggle.mode() == ToggleSprint.PRESS, "Down-edge toggling remains the default mode");
        host.world = true;
        registry.tick();
        check(toggle.holdsSprint(), "Auto sprint starts on world entry");
        registry.keyState(host.sprintKey, true);
        registry.keyState(host.sprintKey, true);
        check(!toggle.holdsSprint(), "Repeated key events cannot flip the toggle back");
        registry.keyState(host.sprintKey, false);
        registry.keyState(host.sprintKey, true);
        check(toggle.holdsSprint(), "A second physical press turns sprint on");
        registry.releaseKeys();
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        host.sneakKey = Keys.MOUSE_RIGHT;
        registry.keyState(host.sneakKey, true);
        registry.keyState(host.sneakKey, false);
        check(toggle.holdsSneak(), "Sneak supports a rebound mouse button and stays on after release");
        host.screen = true;
        check(!toggle.holdsSneak() && !toggle.holdsSprint(), "Menus suspend synthetic movement keys");
        registry.keyState(host.sneakKey, true);
        host.screen = false;
        check(toggle.holdsSneak(), "Clicks in menus do not change toggle state");
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, false);
        registry.tick();
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        check(!toggle.holdsSneak(), "Disabling sneak clears its latch");
        host.world = false;
        registry.reset();
        check(!toggle.holdsSprint() && !toggle.holdsSneak(), "Leaving a world clears movement toggles");
        toggle.setOption(ToggleSprint.AUTO_SPRINT, false);
        host.world = true;
        registry.tick();
        check(!toggle.holdsSprint(), "Auto sprint can be disabled");
        host.sprintKey = 33;
        registry.keyState(29, true);
        check(!toggle.holdsSprint(), "Old bindings no longer toggle sprint");
        registry.keyState(33, true);
        check(toggle.holdsSprint(), "A rebound sprint key toggles sprint");
        registry.toggle(toggle);
        registry.toggle(toggle);
        registry.tick();
        check(!toggle.holdsSprint(), "Disabling a mod clears its movement latch");
        toggle.setOption(ToggleSprint.TOGGLE_SPRINT, false);
        registry.keyState(33, false);
        registry.keyState(33, true);
        check(!toggle.holdsSprint(), "The sprint switch stops sprint toggling");
        registry.keyState(33, false);
        toggle.setOption(ToggleSprint.TOGGLE_SPRINT, true);
        registry.keyState(33, true);
        check(toggle.holdsSprint(), "Sprint toggling resumes when the switch returns");
    }

    private static void tap(Path root) {
        final long[] clock = {10000L};
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host, () -> clock[0]);
        registry.register(toggle);
        toggle.setNumber(ToggleSprint.TOGGLE_MODE, ToggleSprint.TAP);
        toggle.setOption(ToggleSprint.AUTO_SPRINT, false);
        host.world = true;
        registry.tick();
        registry.keyState(host.sprintKey, true);
        check(!toggle.holdsSprint(), "A press alone does not toggle in tap mode");
        clock[0] += 400;
        registry.keyState(host.sprintKey, false);
        check(toggle.holdsSprint(), "A 400 ms sprint tap toggles");
        clock[0] += 1000;
        registry.keyState(host.sprintKey, true);
        clock[0] += 401;
        registry.keyState(host.sprintKey, false);
        check(toggle.holdsSprint(), "A 401 ms sprint hold stays an ordinary hold");
        clock[0] += 1000;
        registry.keyState(host.sprintKey, true);
        clock[0] += 399;
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "A 399 ms sprint tap toggles back off");
        clock[0] += 1000;
        registry.keyState(host.sprintKey, true);
        clock[0] += 3000;
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "A long sprint hold never toggles");
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        clock[0] += 1000;
        registry.keyState(host.sneakKey, true);
        clock[0] += 199;
        registry.keyState(host.sneakKey, false);
        check(toggle.holdsSneak(), "A 199 ms sneak tap toggles");
        clock[0] += 1000;
        registry.keyState(host.sneakKey, true);
        clock[0] += 201;
        registry.keyState(host.sneakKey, false);
        check(toggle.holdsSneak(), "A 201 ms sneak hold stays an ordinary hold");
        clock[0] += 1000;
        registry.keyState(host.sneakKey, true);
        clock[0] += 200;
        registry.keyState(host.sneakKey, false);
        check(!toggle.holdsSneak(), "A 200 ms sneak tap toggles back off");
        host.movement.flying = true;
        clock[0] += 1000;
        registry.keyState(host.sneakKey, true);
        clock[0] += 50;
        registry.keyState(host.sneakKey, false);
        check(!toggle.holdsSneak(), "Flight suppresses sneak taps");
        registry.keyState(host.sprintKey, true);
        clock[0] += 50;
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "Flight suppresses sprint taps");
        host.movement.flying = false;
        clock[0] = 50000L;
        registry.keyState(host.sprintKey, true);
        clock[0] = 10L;
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "A wrapped clock cannot produce a tap");
        clock[0] = 60000L;
        registry.keyState(host.sprintKey, true);
        host.screen = true;
        registry.tick();
        host.screen = false;
        clock[0] += 50;
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "A press interrupted by a menu cannot toggle");
        host.sprintKey = 29;
        clock[0] += 1000;
        registry.keyState(29, true);
        host.sprintKey = 33;
        clock[0] += 50;
        registry.keyState(29, false);
        check(!toggle.holdsSprint(), "Rebinding between press and release cannot toggle");
        clock[0] += 1000;
        registry.keyState(33, true);
        registry.keyState(33, true);
        clock[0] += 100;
        registry.keyState(33, false);
        check(toggle.holdsSprint(), "A repeated press still yields one tap");
    }

    private static void holds(Path root) {
        final long[] clock = {1000L};
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host, () -> clock[0]);
        registry.register(toggle);
        toggle.setOption(ToggleSprint.AUTO_SPRINT, false);
        toggle.setNumber(ToggleSprint.TOGGLE_MODE, ToggleSprint.TAP);
        toggle.setOption(ToggleSprint.SPRINT_OVERRIDE, true);
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, 33);
        host.world = true;
        registry.tick();
        host.physicalKey = 33;
        registry.keyState(33, true);
        check(toggle.holdsSprint(), "A held override binding sprints while pressed");
        clock[0] += 900;
        registry.keyState(33, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSprint(), "A long override press neither latches nor keeps sprinting");
        host.physicalKey = 33;
        registry.keyState(33, true);
        clock[0] += 120;
        registry.keyState(33, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(toggle.holdsSprint(), "A short override tap latches sprint");
        registry.releaseKeys();
        check(toggle.holdsSprint(), "Releasing keys keeps the latch");
        host.physicalKey = 33;
        registry.keyState(33, true);
        clock[0] += 120;
        registry.keyState(33, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSprint(), "A short override tap unlatches sprint");
        host.physicalKey = 33;
        registry.keyState(33, true);
        clock[0] += 50;
        toggle.releaseKeys();
        check(!toggle.holdsSprint(), "Focus loss immediately clears the pending synthetic hold");
        registry.releaseKeys();
        registry.keyState(33, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSprint(), "A tap interrupted by focus loss cannot latch");
        host.physicalKey = 33;
        registry.keyState(33, true);
        check(toggle.holdsSprint(), "The synthetic hold follows the pressed binding");
        host.screen = true;
        registry.tick();
        check(!toggle.holdsSprint(), "A menu drops the held override binding");
        host.screen = false;
        registry.keyState(33, false);
        check(!toggle.holdsSprint(), "A press interrupted by a menu cannot latch");
        host.physicalKey = 33;
        registry.keyState(33, true);
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, 34);
        registry.tick();
        check(!toggle.holdsSprint(), "Rebinding mid-press drops the held override binding");
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, 33);
        clock[0] += 50;
        registry.keyState(33, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSprint(), "Rebinding away and back cannot reuse the old press");
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        toggle.setOption(ToggleSprint.SNEAK_OVERRIDE, true);
        toggle.setNumber(ToggleSprint.KEYBIND_SNEAK, 34);
        host.physicalKey = 34;
        registry.keyState(34, true);
        check(toggle.holdsSneak(), "A held sneak override binding sneaks while pressed");
        TestCanvas held = new TestCanvas(512, 300);
        toggle.render(held);
        check(held.texts.contains("[Sneaking (Key Held)]"), "An override hold is not labelled as a latch");
        clock[0] += 900;
        registry.keyState(34, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSneak(), "A long sneak override press does not latch");
        toggle.setNumber(ToggleSprint.TOGGLE_MODE, ToggleSprint.PRESS);
        host.physicalKey = 34;
        registry.keyState(34, true);
        check(toggle.holdsSneak(), "Down-edge mode still latches the override binding");
        registry.keyState(34, false);
        host.physicalKey = Integer.MIN_VALUE;
        registry.releaseKeys();
        check(toggle.holdsSneak(), "Releasing keys keeps the sneak latch");
        host.physicalKey = 34;
        registry.keyState(34, true);
        registry.keyState(34, false);
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.holdsSneak(), "A second down-edge press unlatches sneak");
    }

    private static void binds(Path root) {
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host);
        registry.register(toggle);
        toggle.setOption(ToggleSprint.AUTO_SPRINT, false);
        host.world = true;
        registry.tick();
        toggle.setOption(ToggleSprint.SPRINT_OVERRIDE, true);
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, Keys.MOUSE_RIGHT);
        check(toggle.sprintToggleKey() == Keys.MOUSE_RIGHT, "Sprint key overrides accept mouse buttons");
        registry.keyState(host.sprintKey, true);
        check(!toggle.holdsSprint(), "The vanilla sprint key stops toggling when overridden");
        registry.keyState(host.sprintKey, false);
        registry.keyState(Keys.MOUSE_RIGHT, true);
        check(toggle.holdsSprint(), "The override key toggles sprint");
        registry.keyState(Keys.MOUSE_RIGHT, false);
        registry.keyState(Keys.MOUSE_RIGHT, true);
        check(!toggle.holdsSprint(), "The override key toggles sprint back off");
        registry.keyState(Keys.MOUSE_RIGHT, false);
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, 0);
        registry.keyState(Keys.MOUSE_RIGHT, true);
        check(!toggle.holdsSprint(), "An unbound override disables the sprint toggle key");
        registry.keyState(Keys.MOUSE_RIGHT, false);
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        toggle.setOption(ToggleSprint.SNEAK_OVERRIDE, true);
        toggle.setNumber(ToggleSprint.KEYBIND_SNEAK, 33);
        check(toggle.sneakToggleKey() == 33, "The sneak override replaces the vanilla binding");
        registry.keyState(host.sneakKey, true);
        check(!toggle.holdsSneak(), "The vanilla sneak key stops toggling when overridden");
        registry.keyState(host.sneakKey, false);
        registry.keyState(33, true);
        check(toggle.holdsSneak(), "The override sneak key toggles sneak");
        registry.keyState(33, false);
        check(toggle.allowsSprint(true), "Double-tap activation is allowed by default");
        toggle.setOption(ToggleSprint.DOUBLE_TAP, false);
        check(toggle.allowsSprint(false), "Releasing sprint is never suppressed");
        check(!toggle.allowsSprint(true), "Without double-tap an unbacked activation is suppressed");
        toggle.setOption(ToggleSprint.SPRINT_OVERRIDE, false);
        registry.keyState(host.sprintKey, true);
        check(toggle.holdsSprint() && toggle.allowsSprint(true), "A latched sprint backs the vanilla activation");
        registry.keyState(host.sprintKey, false);
        registry.keyState(host.sprintKey, true);
        registry.keyState(host.sprintKey, false);
        check(!toggle.holdsSprint(), "The latch is released before the physical check");
        host.physicalKey = host.sprintKey;
        check(toggle.allowsSprint(true), "A physically held sprint key backs the activation");
        host.physicalKey = Integer.MIN_VALUE;
        check(!toggle.allowsSprint(true), "An unbacked activation stays suppressed");
        toggle.setOption(ToggleSprint.DOUBLE_TAP, true);
        check(toggle.allowsSprint(true), "Double-tap restores the vanilla activation");
    }

    private static void containers(Path root) {
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host);
        registry.register(toggle);
        host.world = true;
        registry.tick();
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        registry.keyState(host.sneakKey, true);
        registry.keyState(host.sneakKey, false);
        check(toggle.holdsSneak(), "Sneak starts latched");
        host.screen = true;
        host.movement.container = true;
        check(!toggle.holdsSneak(), "Container sneak is opt-in");
        toggle.setOption(ToggleSprint.CONTAINER_SNEAK, true);
        check(toggle.holdsSneak(), "Container sneak keeps the latch while a container is open");
        check(!toggle.holdsSprint(), "Sprint stays suspended in containers");
        host.movement.address = "mc.hypixel.net";
        registry.tick();
        check(!toggle.holdsSneak(), "Hypixel forces container sneak off");
        host.movement.address = "play.example.com";
        registry.tick();
        check(toggle.holdsSneak(), "Other servers keep container sneak");
        host.movement.container = false;
        check(!toggle.holdsSneak(), "Non-container screens still suspend sneak");
        host.movement.container = true;
        registry.keyState(host.sneakKey, true);
        registry.keyState(host.sneakKey, false);
        check(toggle.holdsSneak(), "Keys in containers cannot change the latch");
        host.screen = false;
        host.movement.address = null;
        registry.tick();
        check(toggle.holdsSneak(), "The latch survives leaving the container");
    }

    private static void flight(Path root) {
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host);
        registry.register(toggle);
        host.world = true;
        registry.tick();
        Movement movement = host.movement;
        check(!toggle.option(ToggleSprint.FLY_BOOST), "Creative fly boost is opt-in");
        movement.creative = true;
        movement.flying = true;
        movement.sprintKeyHeld = true;
        movement.flySpeed = 0.11F;
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Disabled fly boost leaves creative flight untouched");
        movement.creative = false;
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Survival flight state never mutates fly speed");
        toggle.setOption(ToggleSprint.FLY_BOOST, true);
        movement.creative = true;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Creative flight boosts to the configured amount");
        movement.flySpeed = 0.9F;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "The boost re-applies without recapturing the original speed");
        movement.sneakInput = true;
        movement.motionY = 0;
        toggle.movement();
        check(movement.motionY == -0.6D, "Sneak input descends while boosted");
        movement.sneakInput = false;
        movement.jumpInput = true;
        movement.motionY = 0;
        toggle.movement();
        check(movement.motionY == 0.6D, "Jump input ascends while boosted");
        movement.sneakInput = true;
        movement.motionY = 0;
        toggle.movement();
        check(movement.motionY == 0D, "Sneak and jump cancel numerically");
        movement.sneakInput = false;
        movement.jumpInput = false;
        movement.sprintKeyHeld = false;
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Releasing the sprint key restores the original fly speed");
        movement.sprintKeyHeld = true;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Holding the sprint key boosts again");
        movement.flying = false;
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Landing restores the original fly speed");
        movement.flying = true;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Taking off boosts again");
        toggle.setOption(ToggleSprint.FLY_BOOST, false);
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Disabling fly boost mid-flight restores the original speed");
        toggle.setOption(ToggleSprint.FLY_BOOST, true);
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Re-enabling fly boost mid-flight boosts again");
        host.screen = true;
        toggle.movement();
        check(movement.flySpeed == 0.11F, "Opening a menu releases the boost");
        host.screen = false;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Closing the menu boosts again");
        movement.creative = false;
        movement.spectator = true;
        movement.sprintKeyHeld = false;
        toggle.movement();
        movement.flySpeed = 0.07F;
        movement.sprintKeyHeld = true;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "Spectator flight boosts");
        movement.spectator = false;
        toggle.movement();
        check(movement.flySpeed == 0.07F, "Leaving spectator restores the original speed");
        movement.flying = false;
        movement.sprintKeyHeld = false;
        toggle.setNumber(ToggleSprint.FLY_BOOST_AMOUNT, 99);
        check(toggle.number(ToggleSprint.FLY_BOOST_AMOUNT) == 8, "The fly boost amount is bounded");
        toggle.setNumber(ToggleSprint.FLY_BOOST_AMOUNT, 1);
        check(toggle.number(ToggleSprint.FLY_BOOST_AMOUNT) == 2, "The fly boost amount never falls below two");
        movement.creative = true;
        movement.flying = true;
        movement.sprintKeyHeld = true;
        movement.flySpeed = 0.05F;
        toggle.movement();
        check(movement.flySpeed == 0.1F, "The bounded amount is used");
        registry.reset();
        check(movement.flySpeed == 0.05F, "Resetting the mod restores the original fly speed");
        toggle.setNumber(ToggleSprint.FLY_BOOST_AMOUNT, 4);
        movement.flySpeed = 0.05F;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "The boost reapplies after a reset");
        registry.loadPreset("Default");
        check(movement.flySpeed == 0.05F, "Loading a preset restores the original fly speed");
        check(!toggle.option(ToggleSprint.FLY_BOOST), "A preset load restores the opt-in fly boost default");
        toggle.setOption(ToggleSprint.FLY_BOOST, true);
        movement.flySpeed = 0.05F;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "The boost reapplies after re-enabling it");
        host.world = false;
        registry.reset();
        check(movement.flySpeed == 0.05F, "Leaving a world restores the original fly speed");
        host.world = true;
        registry.tick();
        movement.flySpeed = 0.05F;
        toggle.movement();
        check(movement.flySpeed == 0.2F, "The boost reapplies after re-entering a world");
        host.screen = true;
        registry.tick();
        check(movement.flySpeed == 0.05F, "Opening a menu restores the original fly speed");
        host.screen = false;
        movement.sprintKeyHeld = false;
        movement.flying = false;
        movement.creative = false;
    }

    private static void status(Path root) {
        Host host = new Host();
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        ToggleSprint toggle = new ToggleSprint(host);
        registry.register(toggle);
        toggle.setOption(ToggleSprint.AUTO_SPRINT, false);
        host.world = true;
        registry.tick();
        TestCanvas canvas = new TestCanvas(320, 240);
        toggle.preview(true);
        toggle.render(canvas);
        check(canvas.texts.contains("[Sprinting (Toggled)]"), "Preview falls back to the sprint label");
        toggle.preview(false);
        check(toggle.width(canvas) == 0, "An idle status is hidden");
        registry.keyState(host.sprintKey, true);
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Sprinting (Toggled)]"), "A latched sprint is labelled as toggled");
        toggle.setText(ToggleSprint.SPRINTING_TEXT, "Zooming");
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Zooming (Toggled)]"), "Custom sprint text is used");
        registry.keyState(host.sprintKey, false);
        registry.keyState(host.sprintKey, true);
        host.sprinting = true;
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Zooming (Key Held)]"), "A physically held sprint is labelled");
        host.sprinting = false;
        toggle.setOption(ToggleSprint.TOGGLE_SNEAK, true);
        registry.keyState(host.sneakKey, true);
        registry.keyState(host.sneakKey, false);
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Sneaking (Toggled)]"), "A latched sneak is labelled as toggled");
        host.movement.flying = true;
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Flying, Sneaking (Toggled)]"), "Flight and sneak combine in one status");
        host.movement.flying = false;
        registry.keyState(host.sneakKey, true);
        registry.keyState(host.sneakKey, false);
        host.movement.sneaking = true;
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Sneaking (Key Held)]"), "A physically held sneak is labelled");
        host.movement.sneaking = false;
        host.movement.riding = true;
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Riding]"), "Riding is reported");
        toggle.setOption(ToggleSprint.SHOW_RIDING, false);
        check(toggle.width(canvas) == 0, "The riding status can be turned off");
        toggle.setOption(ToggleSprint.SHOW_RIDING, true);
        host.movement.riding = false;
        host.movement.descending = true;
        canvas.texts.clear();
        toggle.render(canvas);
        check(canvas.texts.contains("[Descending]"), "Descending is reported");
        toggle.setOption(ToggleSprint.SHOW_DESCENDING, false);
        check(toggle.width(canvas) == 0, "The descending status can be turned off");
        toggle.setOption(ToggleSprint.SHOW_DESCENDING, true);
        host.movement.descending = false;
        toggle.setOption(ToggleSprint.SHOW_TEXT, false);
        check(toggle.width(canvas) == 0 && toggle.height(canvas) == 0, "The status text switch hides the HUD");
        toggle.setOption(ToggleSprint.SHOW_TEXT, true);
        toggle.preview(true);
        int plain = toggle.height(canvas);
        toggle.setOption(ToggleSprint.ICON_MODE, true);
        check(toggle.height(canvas) == plain + 11, "Icon mode adds an icon row above the text");
        toggle.render(canvas);
        int iconX = 3 + (toggle.width(canvas) - 6 - 9) / 2;
        check((canvas.pixel(iconX + 4, 3) & 255) == 255, "The sprint icon draws a motion bar");
        host.movement.sneaking = true;
        toggle.render(canvas);
        check((canvas.pixel(iconX + 4, 3) & 255) != 255, "The sneak icon replaces the motion bars");
        host.movement.sneaking = false;
        toggle.setOption(ToggleSprint.ICON_MODE, false);
        toggle.preview(false);
    }

    private static void persistence(Path root) {
        ModSettings settings = new ModSettings(root.toFile());
        Host host = new Host();
        ToggleSprint toggle = new ToggleSprint(host);
        new ModRegistry(settings).register(toggle);
        toggle.setNumber(ToggleSprint.KEYBIND_SPRINT, Keys.MOUSE_RIGHT);
        toggle.setNumber(ToggleSprint.KEYBIND_SNEAK, 33);
        toggle.setNumber(ToggleSprint.TOGGLE_MODE, ToggleSprint.TAP);
        toggle.setNumber(ToggleSprint.FLY_BOOST_AMOUNT, 7);
        toggle.setText(ToggleSprint.FLYING_TEXT, "Soaring");
        toggle.setOption(ToggleSprint.TOGGLE_SPRINT, false);
        settings.createPreset("Keys");
        ModSettings reloaded = new ModSettings(root.toFile());
        ToggleSprint other = new ToggleSprint(host);
        new ModRegistry(reloaded).register(other);
        check(other.number(ToggleSprint.KEYBIND_SPRINT) == Keys.MOUSE_RIGHT, "Mouse key overrides survive restart");
        check(other.number(ToggleSprint.KEYBIND_SNEAK) == 33, "Keyboard overrides survive restart");
        check(other.mode() == ToggleSprint.TAP, "The toggle mode choice persists");
        check(other.number(ToggleSprint.FLY_BOOST_AMOUNT) == 7, "The fly boost amount persists");
        check(other.text(ToggleSprint.FLYING_TEXT).equals("Soaring"), "Custom status text persists");
        check(!other.option(ToggleSprint.TOGGLE_SPRINT), "The sprint switch persists");
        check(other.texts().size() == 3 && other.texts().contains(ToggleSprint.SPRINTING_TEXT),
                "Text options are exposed to the menu");
        other.setNumber(ToggleSprint.TOGGLE_MODE, ToggleSprint.PRESS);
        other.setText(ToggleSprint.FLYING_TEXT, "Wrong");
        reloaded.loadPreset("Keys");
        check(other.mode() == ToggleSprint.TAP, "Presets isolate the toggle mode");
        check(other.number(ToggleSprint.KEYBIND_SPRINT) == Keys.MOUSE_RIGHT, "Presets isolate key overrides");
        check(other.text(ToggleSprint.FLYING_TEXT).equals("Soaring"), "Presets isolate custom status text");
    }

    private static final class Host implements GameHost {
        final Movement movement = new Movement();
        TestCanvas canvas = new TestCanvas(512, 300);
        boolean world;
        boolean screen;
        boolean sprinting;
        int sprintKey = 29;
        int sneakKey = 42;
        int physicalKey = Integer.MIN_VALUE;

        @Override
        public MovementBackend movement() {
            return movement;
        }

        @Override
        public boolean inWorld() {
            return world;
        }

        @Override
        public boolean screenOpen() {
            return screen;
        }

        @Override
        public void openScreen(Screen screen) {
            this.screen = true;
        }

        @Override
        public void closeScreen() {
            screen = false;
        }

        @Override
        public boolean keyDown(int key) {
            return key != 0 && key == physicalKey;
        }

        @Override
        public int controlKey(Control control) {
            return 0;
        }

        @Override
        public String keyName(int key) {
            return key == 0 ? "--" : Integer.toString(key);
        }

        @Override
        public double[] position() {
            return null;
        }

        @Override
        public int ping() {
            return -1;
        }

        @Override
        public int fps() {
            return 144;
        }

        @Override
        public File gameDirectory() {
            return new File(".");
        }

        @Override
        public void blur(boolean enabled) {
        }

        @Override
        public Canvas canvas() {
            return canvas;
        }

        @Override
        public String version() {
            return "1.8.9";
        }

        @Override
        public void open(VanillaScreen kind) {
        }

        @Override
        public void quitGame() {
        }

        @Override
        public boolean singleplayer() {
            return true;
        }

        @Override
        public void leaveWorld() {
        }

        @Override
        public int sprintKey() {
            return sprintKey;
        }

        @Override
        public int sneakKey() {
            return sneakKey;
        }

        @Override
        public boolean sprinting() {
            return sprinting;
        }

        @Override
        public void setSprinting(boolean value) {
            sprinting = value;
        }
    }

    private static final class Movement implements MovementBackend {
        boolean creative;
        boolean spectator;
        boolean flying;
        boolean riding;
        boolean descending;
        boolean sneaking;
        boolean sprintKeyHeld;
        boolean sneakInput;
        boolean jumpInput;
        boolean container;
        String address;
        float flySpeed = 0.05F;
        double motionY;

        @Override
        public boolean creative() {
            return creative;
        }

        @Override
        public boolean spectator() {
            return spectator;
        }

        @Override
        public boolean flying() {
            return flying;
        }

        @Override
        public boolean riding() {
            return riding;
        }

        @Override
        public boolean descending() {
            return descending;
        }

        @Override
        public boolean sneaking() {
            return sneaking;
        }

        @Override
        public boolean sprintKeyHeld() {
            return sprintKeyHeld;
        }

        @Override
        public boolean sneakInputHeld() {
            return sneakInput;
        }

        @Override
        public boolean jumpInputHeld() {
            return jumpInput;
        }

        @Override
        public float flySpeed() {
            return flySpeed;
        }

        @Override
        public void setFlySpeed(float speed) {
            flySpeed = speed;
        }

        @Override
        public void addMotionY(double amount) {
            motionY += amount;
        }

        @Override
        public boolean containerOpen() {
            return container;
        }

        @Override
        public String serverAddress() {
            return address;
        }
    }
}
