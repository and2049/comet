package comet.core.mod;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.ui.HudEditor;
import comet.core.ui.ModMenu;
import comet.core.ui.Screen;
import comet.core.ui.TestCanvas;
import comet.core.platform.RawMouse;
import comet.core.platform.BorderlessWindow;
import comet.core.render.Reprojection;
import comet.core.render.BlurVelocity;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class CoreTests {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path base = new File(System.getProperty("java.io.tmpdir"), "redsun").toPath();
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base, "comet-core-");
        try {
            cps(root.resolve("cps"));
            toggles(root.resolve("toggle"));
            settings(root.resolve("settings"));
            presets(root.resolve("presets"));
            menus(root.resolve("menu"));
            globalSettings(root.resolve("global"));
            rawMouse();
            borderless();
            displayMods(root.resolve("display"));
            motionBlur(root.resolve("blur"));
            System.out.println("Comet core: " + assertions + " assertions passed");
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Mod find(CometClient client, String id) {
        for (Mod mod : client.mods().all()) {
            if (mod.id().equals(id)) {
                return mod;
            }
        }
        throw new AssertionError("Missing mod " + id);
    }

    private static Option option(Mod mod, String id) {
        for (Option option : mod.options()) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        throw new AssertionError("Missing option " + id);
    }

    private static void cps(Path root) {
        AtomicLong clock = new AtomicLong(1000);
        CpsCounter cps = new CpsCounter(clock::get);
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()));
        registry.register(cps);
        TestCanvas canvas = new TestCanvas(1000, 600);
        int baseline = cps.width(canvas);
        registry.keyState(Keys.MOUSE_LEFT, true);
        registry.keyState(Keys.MOUSE_LEFT, true);
        cps.render(canvas);
        check(canvas.texts.contains("1"), "Repeated down events count as a single click");
        check(!canvas.texts.contains("2"), "Holding a mouse button does not add CPS");
        registry.keyState(Keys.MOUSE_LEFT, false);
        registry.keyState(Keys.MOUSE_RIGHT, true);
        clock.addAndGet(999);
        canvas.texts.clear();
        cps.render(canvas);
        check(canvas.texts.stream().filter("1"::equals).count() == 2, "Both mouse buttons use the rolling second");
        clock.incrementAndGet();
        canvas.texts.clear();
        cps.render(canvas);
        check(canvas.texts.stream().filter("0"::equals).count() == 2, "Clicks expire at exactly one second");
        check(cps.width(canvas) == baseline, "Single-digit CPS reserves two digits");
        CpsCounter.Layout two = CpsCounter.layout(canvas, 11, 99, "");
        CpsCounter.Layout three = CpsCounter.layout(canvas, 100, 1, "");
        check(two.width + 6 == baseline, "Two digits do not resize the counter");
        check(three.width - two.width == 2 * (three.slot - two.slot), "Three digits grow both slots equally");
        check(CpsCounter.layout(canvas, 1, 999, "").width == three.width, "Either side can trigger symmetric growth");
        ModSettings.Layout position = HudLayout.defaults(cps);
        position.x = 0.4F;
        position.scale = 1.5F;
        HudLayout.Placement before = HudLayout.resolve(cps, position, canvas);
        ModSettings.Layout edge = HudLayout.defaults(cps);
        edge.x = 0;
        HudLayout.Placement edgeBefore = HudLayout.resolve(cps, edge, canvas);
        for (int index = 0; index < 100; index++) {
            registry.keyState(Keys.MOUSE_LEFT, true);
            registry.keyState(Keys.MOUSE_LEFT, false);
        }
        HudLayout.Placement after = HudLayout.resolve(cps, position, canvas);
        check(before.x + before.width / 2 == after.x + after.width / 2, "CPS separator stays at its screen anchor when expanding");
        check(after.x < before.x && after.width > before.width, "The HUD expands outwards");
        HudLayout.Placement edgeAfter = HudLayout.resolve(cps, edge, canvas);
        check(edgeBefore.x + edgeBefore.width / 2 == edgeAfter.x + edgeAfter.width / 2, "Screen-edge clipping cannot shift the CPS separator");
        registry.toggle(cps);
        registry.toggle(cps);
        canvas.texts.clear();
        cps.render(canvas);
        check(canvas.texts.stream().filter("0"::equals).count() == 2, "Disabling clears recorded clicks");
    }

    private static void toggles(Path root) {
        Host host = new Host(root);
        CometClient client = new CometClient(host.bridge);
        host.world = true;
        client.tick();
        Mod toggle = find(client, "toggleSprint");
        check(client.holdsSprint(), "Auto sprint starts on world entry");
        client.keyState(host.sprintKey, true);
        client.keyState(host.sprintKey, true);
        check(!client.holdsSprint(), "Repeated key events cannot flip the toggle back");
        client.keyState(host.sprintKey, false);
        client.keyState(host.sprintKey, true);
        check(client.holdsSprint(), "A second physical press turns sprint on");
        client.releaseKeys();
        toggle.setOption(option(toggle, "toggleSneak"), true);
        host.sneakKey = Keys.MOUSE_RIGHT;
        client.keyState(host.sneakKey, true);
        client.keyState(host.sneakKey, false);
        check(client.holdsSneak(), "Sneak supports a rebound mouse button and stays on after release");
        client.openModMenu();
        check(!client.holdsSneak() && !client.holdsSprint(), "Menus suspend synthetic movement keys");
        client.keyState(host.sneakKey, true);
        host.bridge.closeScreen();
        check(client.holdsSneak(), "Clicks in menus do not change toggle state");
        toggle.setOption(option(toggle, "toggleSneak"), false);
        client.tick();
        toggle.setOption(option(toggle, "toggleSneak"), true);
        check(!client.holdsSneak(), "Disabling sneak clears its latch");
        host.world = false;
        client.tick();
        check(!client.holdsSprint() && !client.holdsSneak(), "Leaving a world clears movement toggles");
        toggle.setOption(option(toggle, "autoSprint"), false);
        host.world = true;
        client.tick();
        check(!client.holdsSprint(), "Auto sprint can be disabled");
        host.sprintKey = 33;
        client.keyState(29, true);
        check(!client.holdsSprint(), "Old bindings no longer toggle sprint");
        client.keyState(33, true);
        check(client.holdsSprint(), "Rebound sprint key toggles sprint");
        client.mods().toggle(toggle);
        client.mods().toggle(toggle);
        client.tick();
        check(!client.holdsSprint(), "Disabling a mod clears its movement latch");
    }

    private static void settings(Path root) throws Exception {
        Files.createDirectories(root.resolve("comet"));
        Files.write(root.resolve("comet/settings.json"), "{\"mods\":{\"fps\":false}}".getBytes(StandardCharsets.UTF_8));
        ModSettings settings = new ModSettings(root.toFile());
        check(Boolean.FALSE.equals(settings.enabled("fps")), "Existing settings are preserved");
        check(settings.option("oldAnimations", "blockHit") == null, "Older settings use option defaults");
        ModRegistry registry = new ModRegistry(settings);
        OldAnimations visuals = new OldAnimations();
        registry.register(visuals);
        visuals.setOption(option(visuals, "blockHit"), false);
        check(!visuals.swings(true) && visuals.swings(false), "Block and use animations have independent switches");
        ModSettings reloaded = new ModSettings(root.toFile());
        check(Boolean.FALSE.equals(reloaded.option("oldAnimations", "blockHit")), "Options survive reload");
        Host legacy = new Host(root.resolve("legacy"));
        legacy.version = "1.7.10";
        check(new CometClient(legacy.bridge).mods().all().stream().noneMatch(mod -> mod.id().equals("oldAnimations")), "1.7.10 uses its native animations");
    }

    private static void menus(Path root) throws Exception {
        Host host = new Host(root);
        CometClient client = new CometClient(host.bridge);
        host.world = true;
        host.shift = true;
        client.tick();
        check(host.screen instanceof ModMenu, "Right Shift opens the card menu");
        host.screen.key('\0', Keys.RIGHT_SHIFT);
        check(host.screen != null, "The opening key event does not immediately close the menu");
        host.shift = false;
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {512, 300}, {960, 540}}) {
            host.canvas = new TestCanvas(size[0], size[1]);
            host.screen.resize(size[0], size[1]);
            host.screen.draw(host.canvas, -1, -1);
            check(host.canvas.clipsBalanced(), "Card clipping is balanced at " + size[0]);
            check(host.canvas.drawnTextures.contains("comet:mod/fps") && host.canvas.drawnTextures.contains("comet:mod/cps"),
                    "Mod icons are uploaded even when the game reports placeholder texture dimensions");
            host.canvas.save("mods-" + size[0]);
        }
        host.canvas = new TestCanvas(512, 300);
        host.screen.resize(512, 300);
        host.screen.draw(host.canvas, -1, -1);
        check((host.canvas.pixel(140, 65) & 255) > (host.canvas.pixel(140, 64) & 255) + 16, "Top-row card border remains visible");
        check((host.canvas.pixel(105, 90) & 255) > (host.canvas.pixel(104, 90) & 255) + 16, "First-column outside border remains visible");
        check((host.canvas.pixel(488, 90) & 255) > (host.canvas.pixel(489, 90) & 255) + 16, "Last-column outside border remains visible");
        check((host.canvas.pixel(140, 282) & 255) > (host.canvas.pixel(140, 283) & 255) + 16, "Bottom-row card border remains visible");
        Mod fps = find(client, "fps");
        host.screen.mouseDown(130, 158, 0);
        check(!client.mods().isEnabled(fps), "Card footer toggles a mod");
        host.screen.mouseDown(250, 136, 0);
        host.screen.draw(host.canvas, -1, -1);
        host.canvas.save("cps-options");
        host.screen.key('\0', Keys.ESCAPE);
        check(host.screen instanceof ModMenu, "Escape dismisses options before closing the menu");
        host.screen.mouseDown(425, 48, 0);
        for (char character : "unmatched".toCharArray()) {
            host.screen.key(character, 0);
        }
        int enabled = client.mods().enabledCount();
        host.screen.draw(host.canvas, -1, -1);
        host.screen.mouseDown(130, 158, 0);
        check(client.mods().enabledCount() == enabled, "Filtered-out cards cannot be toggled");
        host.screen.key('\0', Keys.ESCAPE);
        check(host.screen == null && !host.blurred, "Closing the menu releases blur");
        client.openModMenu();
        host.screen.key('\0', Keys.RIGHT_SHIFT);
        check(host.screen == null, "Right Shift closes an already-open menu");
        client.openHudEditor();
        check(host.screen instanceof HudEditor, "HUD editor remains accessible");
        host.screen.draw(host.canvas, -1, -1);
        host.screen.mouseDown(256, 150, 0);
        check(host.screen instanceof ModMenu && host.blurred, "Switching from HUD editor preserves menu lifecycle");
        presetMenu(host, client);
    }

    private static void globalSettings(Path root) throws Exception {
        Host host = new Host(root);
        CometClient client = new CometClient(host.bridge);
        ModSettings settings = client.mods().settings();
        for (GlobalSetting setting : GlobalSetting.values()) {
            check(settings.global(setting) == setting.defaultValue, "Global setting defaults: " + setting.id);
        }
        settings.setGlobal(GlobalSetting.HUD_BACKGROUND, false);
        settings.setGlobal(GlobalSetting.TEXT_SHADOW, false);
        settings.createPreset("Other");
        settings.loadPreset("Default");
        settings.deletePreset("Other");
        check(!settings.global(GlobalSetting.HUD_BACKGROUND) && !settings.global(GlobalSetting.TEXT_SHADOW), "Presets preserve global preferences");
        check(!settings.presetModified(), "Global preferences do not dirty mod snapshots");
        ModSettings restored = new ModSettings(root.toFile());
        check(!restored.global(GlobalSetting.HUD_BACKGROUND) && !restored.global(GlobalSetting.TEXT_SHADOW), "Global preferences survive restart");
        for (HudMod mod : client.mods().hud()) {
            mod.preview(true);
            TestCanvas canvas = new TestCanvas(320, 240);
            int width = mod.width(canvas);
            mod.render(canvas);
            check(!canvas.roundedColors.contains(0x70000000) && !canvas.shadows.isEmpty() && !canvas.shadows.contains(true), "Global HUD appearance applies to " + mod.id());
            settings.setGlobal(GlobalSetting.HUD_BACKGROUND, true);
            settings.setGlobal(GlobalSetting.TEXT_SHADOW, true);
            canvas = new TestCanvas(320, 240);
            mod.render(canvas);
            check(canvas.roundedColors.contains(0x70000000) && !canvas.shadows.contains(false) && mod.width(canvas) == width, "Restoring HUD styling preserves layout for " + mod.id());
            settings.setGlobal(GlobalSetting.HUD_BACKGROUND, false);
            settings.setGlobal(GlobalSetting.TEXT_SHADOW, false);
        }
        client.openModMenu();
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {512, 300}, {960, 540}}) {
            host.canvas = new TestCanvas(size[0], size[1]);
            host.screen.resize(size[0], size[1]);
            int panelX = (size[0] - Math.min(600, size[0] - 20)) / 2;
            int panelY = (size[1] - Math.min(370, size[1] - 20)) / 2;
            host.screen.mouseDown(panelX + 170, panelY + 13, 0);
            host.screen.draw(host.canvas, -1, -1);
            check(host.canvas.clipsBalanced(), "Settings page clips correctly at " + size[0]);
            host.canvas.save("settings-" + size[0]);
        }
        host.canvas = new TestCanvas(512, 300);
        host.screen.resize(512, 300);
        host.screen.mouseDown(120, 76, 0);
        check(settings.global(GlobalSetting.HUD_BACKGROUND), "Settings page toggles global HUD background");
        host.screen.mouseDown(130, 49, 0);
        for (char character : "hotbar".toCharArray()) host.screen.key(character, 0);
        host.screen.mouseDown(120, 76, 0);
        check(settings.global(GlobalSetting.DISABLE_HOTBAR_SCROLLING), "Settings search routes clicks to the filtered setting");
        host.screen.mouseDown(120, 23, 0);
        host.screen.draw(host.canvas, -1, -1);
        check(host.canvas.drawnTextures.contains("comet:mod/fps"), "Mods tab restores the card grid");
        Path failureRoot = root.resolve("failure");
        ModSettings failure = new ModSettings(failureRoot.toFile());
        Files.createDirectories(failureRoot.resolve("comet/settings.json/occupied"));
        rejected(() -> failure.setGlobal(GlobalSetting.TEXT_SHADOW, false), "Global settings report persistence failures");
        check(failure.global(GlobalSetting.TEXT_SHADOW), "Failed writes retain the prior global setting");
    }

    private static void rawMouse() {
        final float[][] sample = {new float[] {50, -50}};
        RawMouse mouse = new RawMouse(() -> sample[0]);
        check(mouse.read(false, true) == null, "Disabled raw input uses vanilla movement");
        check(mouse.read(true, true)[0] == 0, "Enabling raw input discards stale deltas");
        sample[0] = new float[] {3, -4};
        int[] movement = mouse.read(true, true);
        check(movement[0] == 3 && movement[1] == -4, "Raw deltas preserve magnitude and direction");
        sample[0] = new float[] {0.5F, -0.5F};
        check(mouse.read(true, true)[0] == 0, "Fractional raw deltas accumulate");
        movement = mouse.read(true, true);
        check(movement[0] == 1 && movement[1] == -1, "Fractional accumulation avoids dropping slow input");
        sample[0] = new float[] {1000, 1000};
        check(mouse.read(true, false)[0] == 0 && mouse.read(true, true)[0] == 0, "Focus transitions discard background movement");
        mouse.reset();
        check(mouse.read(true, true)[0] == 0, "Cursor re-grab discards stale movement");
        sample[0] = null;
        check(mouse.read(true, true) == null && !mouse.status().isEmpty(), "Disconnected raw devices fall back to vanilla input");
        sample[0] = new float[] {3, 4};
        check(mouse.read(true, true)[0] == 0 && mouse.status().isEmpty(), "Reconnected devices resume without a camera jump");
    }

    private static void borderless() {
        final BorderlessWindow.State original = new BorderlessWindow.State(854, 480, 120, 80, null, true);
        final BorderlessWindow.State desktop = new BorderlessWindow.State(1920, 1080, 0, 0, "true", false);
        final BorderlessWindow.State[] current = {original};
        final boolean[] fail = {false};
        BorderlessWindow window = new BorderlessWindow(new BorderlessWindow.Backend() {
            public BorderlessWindow.State current() { return current[0]; }
            public BorderlessWindow.State desktop() { return desktop; }
            public void apply(BorderlessWindow.State state) {
                current[0] = state;
                if (fail[0]) {
                    fail[0] = false;
                    throw new IllegalStateException("Simulated display failure");
                }
            }
        });
        check(window.transition() && window.active() && current[0] == desktop, "Borderless enters desktop-sized undecorated mode");
        fail[0] = true;
        check(!window.transition() && window.active() && current[0] == desktop && !window.status().isEmpty(), "A failed exit rolls back and retains fullscreen state");
        check(window.transition() && !window.active() && current[0] == original && window.status().isEmpty(), "Leaving borderless restores position, dimensions and decoration");
        fail[0] = true;
        check(!window.transition() && !window.active() && current[0] == original, "A failed entry restores the original window");
        check(window.transition() && window.transition() && current[0] == original, "Fullscreen can be retried after display failure");
    }

    private static void rejected(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            check(true, message);
            return;
        }
        check(false, message);
    }

    private static void displayMods(Path root) throws Exception {
        Host host = new Host(root);
        host.world = true;
        CometClient client = new CometClient(host.bridge);
        Coordinates coordinates = (Coordinates) find(client, "coordinates");
        PingCounter ping = (PingCounter) find(client, "ping");
        Keystrokes keys = (Keystrokes) find(client, "keystrokes");
        Lighting lighting = (Lighting) find(client, "lighting");
        host.position = new double[] {-0.01, 63.99, -123.5};
        check(java.util.Arrays.equals(coordinates.labels(), new String[] {"X: -1", "Y: 63", "Z: -124"}), "Coordinates floor negative positions to their block");
        host.position = null;
        check(coordinates.labels()[0].equals("X: --"), "Missing player coordinates are explicit");
        host.ping = 183;
        check(ping.label().equals("183 ms"), "Ping shows server-reported latency");
        host.ping = -1;
        check(ping.label().equals("-- ms"), "Unknown ping does not invent zero latency");
        host.local = true;
        check(ping.label().equals("Local"), "Singleplayer is identified as local");
        host.physicalKey = 17;
        check(keys.pressed(comet.core.bridge.Control.FORWARD), "Keystrokes reads physical movement bindings");
        host.forwardKey = -98;
        check(!keys.pressed(comet.core.bridge.Control.FORWARD), "Rebinding releases the old keystroke indicator");
        host.physicalKey = -98;
        check(keys.pressed(comet.core.bridge.Control.FORWARD), "Keystrokes supports rebound mouse controls");
        host.forwardKey = 0;
        host.physicalKey = 0;
        check(!keys.pressed(comet.core.bridge.Control.FORWARD), "Unbound controls never light up");
        host.forwardKey = 17;
        host.physicalKey = 17;
        client.openModMenu();
        check(!keys.pressed(comet.core.bridge.Control.FORWARD), "GUI input does not light keystrokes");
        host.bridge.closeScreen();
        host.world = false;
        check(!keys.pressed(comet.core.bridge.Control.FORWARD), "World exit clears keystrokes without stale state");
        for (HudMod mod : new HudMod[] {keys, coordinates, ping}) {
            client.mods().toggle(mod);
            mod.preview(true);
            client.mods().settings().setGlobal(GlobalSetting.HUD_BACKGROUND, false);
            client.mods().settings().setGlobal(GlobalSetting.TEXT_SHADOW, false);
            TestCanvas canvas = new TestCanvas(320, 240);
            mod.render(canvas);
            check(!canvas.shadows.isEmpty() && !canvas.shadows.contains(true) && canvas.roundedColors.isEmpty(), "Global appearance applies to " + mod.id());
            client.mods().settings().setGlobal(GlobalSetting.HUD_BACKGROUND, true);
            client.mods().settings().setGlobal(GlobalSetting.TEXT_SHADOW, true);
            canvas = new TestCanvas(320, 240);
            mod.render(canvas);
            check(canvas.roundedColors.contains(0x70000000) && !canvas.shadows.contains(false), "Background and shadows restore for " + mod.id());
            canvas.save(mod.id());
        }
        check(keys.height(host.canvas) == 88, "Keystrokes includes mouse and jump rows");
        keys.setOption(Keystrokes.MOUSE, false);
        keys.setOption(Keystrokes.JUMP, false);
        check(keys.height(host.canvas) == 46, "Keystrokes optional rows resize the editor bounds");
        int[] original = {0xFF204080, 0x80301005, 0xFF000000};
        int[] colors = original.clone();
        client.lightmap(colors);
        check(java.util.Arrays.equals(colors, original), "Disabled Lighting leaves the lightmap untouched");
        client.mods().toggle(lighting);
        client.lightmap(colors);
        check(colors[0] == 0xFFFFFFFF && colors[1] == 0x80FFFFFF, "Fullbright saturates RGB while preserving alpha");
        lighting.setOption(Lighting.FULLBRIGHT, false);
        colors = original.clone();
        client.lightmap(colors);
        check(colors[0] == 0xFF4080FF && colors[1] == 0x8060200A && colors[2] == 0xFF000000, "Multiplier brightens channels independently and clamps without overflow");
        lighting.setNumber(Lighting.MULTIPLIER, 1);
        colors = original.clone();
        client.lightmap(colors);
        check(java.util.Arrays.equals(colors, original), "1x preserves vanilla lightmap colors exactly");
        lighting.setNumber(Lighting.MULTIPLIER, 999);
        check(lighting.number(Lighting.MULTIPLIER) == 10, "Multiplier is bounded");
        ModSettings settings = client.mods().settings();
        settings.savePreset();
        lighting.setNumber(Lighting.MULTIPLIER, 3);
        check(settings.presetModified(), "Numeric changes dirty the preset");
        settings.createPreset("Dimmer");
        lighting.setNumber(Lighting.MULTIPLIER, 4);
        settings.loadPreset("Default");
        check(lighting.number(Lighting.MULTIPLIER) == 10, "Numeric preset snapshots are deeply isolated");
        ModSettings restarted = new ModSettings(root.toFile());
        restarted.loadPreset("Dimmer");
        check(restarted.number("lighting", "multiplier") == 3, "Numeric settings and snapshots survive restart");
        Path failureRoot = root.resolve("failure");
        ModSettings failure = new ModSettings(failureRoot.toFile());
        Files.createDirectories(failureRoot.resolve("comet/settings.json/occupied"));
        rejected(() -> failure.setNumber("lighting", "multiplier", 4), "Numeric persistence failures are reported");
        check(failure.number("lighting", "multiplier") == null, "Failed numeric writes retain working settings");
        host.version = "1.7.10";
        check(find(new CometClient(host.bridge), "lighting") != null, "Lighting is registered on legacy PvP too");
        host.world = true;
        client.openModMenu();
        host.screen.mouseDown(430, 49, 0);
        for (char c : "lighting".toCharArray()) host.screen.key(c, 0);
        host.screen.mouseDown(145, 128, 0);
        host.screen.draw(host.canvas, -1, -1);
        host.canvas.save("lighting-options");
        int previous = lighting.number(Lighting.MULTIPLIER);
        host.screen.mouseDown(311, 174, 0);
        check(lighting.number(Lighting.MULTIPLIER) == previous - 1, "Multiplier minus button changes the numeric setting");
        host.screen.mouseDown(365, 174, 0);
        check(lighting.number(Lighting.MULTIPLIER) == previous, "Multiplier plus button changes the numeric setting");
    }

    private static void motionBlur(Path root) throws Exception {
        Host host = new Host(root);
        CometClient client = new CometClient(host.bridge);
        MotionBlur blur = (MotionBlur) find(client, "motionBlur");
        check(!client.mods().isEnabled(blur) && blur.category() == Category.VISUAL, "Motion blur starts disabled as a visual mod");
        host.version = "1.7.10";
        check(find(new CometClient(host.bridge), "motionBlur") != null, "Motion blur is registered on legacy PvP too");
        check(blur.number(MotionBlur.ALGORITHM) == MotionBlur.HYBRID && blur.number(MotionBlur.STRENGTH) == 5, "Hybrid at medium strength is the default");
        check(MotionBlur.ALGORITHM.display(3).equals("Hybrid") && Lighting.MULTIPLIER.display(2).equals("2x")
                && MotionBlur.STRENGTH.display(5).equals("5"), "Named algorithms and numeric units display correctly");
        NumberOption futureAlgorithms = new NumberOption("algorithm", "Algorithm", 3, 3, 4, "", "Hybrid", "Future");
        check(futureAlgorithms.display(4).equals("Future") && futureAlgorithms.clamp(5) == 4,
                "Named selectors retain navigation and bounds for future algorithms");
        blur.setNumber(MotionBlur.ALGORITHM, 9);
        check(blur.number(MotionBlur.ALGORITHM) == MotionBlur.HYBRID, "Algorithm is bounded to the implemented set");
        blur.setNumber(MotionBlur.STRENGTH, 0);
        check(blur.number(MotionBlur.STRENGTH) == 1, "Strength never drops to an invisible zero");
        for (int oldAlgorithm = 1; oldAlgorithm <= 2; oldAlgorithm++) {
            client.mods().settings().setNumber(blur.id(), MotionBlur.ALGORITHM.id, oldAlgorithm);
            check(blur.number(MotionBlur.ALGORITHM) == MotionBlur.HYBRID, "Saved rejected algorithm resolves to Hybrid: " + oldAlgorithm);
            MotionBlur restored = (MotionBlur) find(new CometClient(host.bridge), "motionBlur");
            check(restored.number(MotionBlur.ALGORITHM) == MotionBlur.HYBRID, "Rejected selection resolves after restart: " + oldAlgorithm);
        }
        blur.setNumber(MotionBlur.ALGORITHM, MotionBlur.HYBRID - 1);
        check(blur.number(MotionBlur.ALGORITHM) == MotionBlur.HYBRID, "Selector cannot navigate to a rejected algorithm");
        blur.setNumber(MotionBlur.STRENGTH, 9);
        check(blur.trailStrength() == 5 && blur.shutterMs() == MotionBlur.trailMs(9), "Hybrid keeps its accepted shutter and half-strength trail");
        check(MotionBlur.weight(5, 0) == 1F && MotionBlur.weight(5, 1e9) == 0F, "Fresh history is kept whole and stale history vanishes");
        for (int strength = 1; strength <= 10; strength++) {
            float twoShort = MotionBlur.weight(strength, 4) * MotionBlur.weight(strength, 4);
            check(Math.abs(twoShort - MotionBlur.weight(strength, 8)) < 1e-5, "Trail decay is frame-rate independent at strength " + strength);
            check(strength == 1 || MotionBlur.weight(strength, 16) > MotionBlur.weight(strength - 1, 16), "Higher strength keeps more history at " + strength);
        }
        check(Math.abs(Math.pow(MotionBlur.weight(5, 1000.0 / 300), 5) - MotionBlur.weight(5, 1000.0 / 60)) < 1e-6,
                "Every-frame history has equal decay at 60 and 300 fps");
        check(MotionBlur.weight(1, 16.7) < 0.3F, "Minimum strength is a subtle trail at 60 fps");
        float[] projection = perspective(70, 16F / 9F);
        float[] still = Reprojection.matrix(projection, rotationY(0.4F), projection, rotationY(0.4F));
        for (float[] point : new float[][] {{0, 0}, {0.7F, -0.3F}, {-1, 1}}) {
            float[] mapped = Reprojection.project(still, point[0], point[1]);
            check(Math.abs(mapped[0] - point[0]) < 1e-5 && Math.abs(mapped[1] - point[1]) < 1e-5, "A still camera reprojects every pixel onto itself");
        }
        float yaw = 0.05F;
        float[] turned = Reprojection.matrix(projection, rotationY(0), projection, rotationY(yaw));
        float[] centre = Reprojection.project(turned, 0, 0);
        check(Math.abs(centre[0] - projection[0] * Math.tan(yaw)) < 1e-5 && Math.abs(centre[1]) < 1e-6, "Turning right maps the centre pixel to its previous position on the right");
        float[] moved = rotationY(yaw);
        moved[12] = 3F;
        moved[13] = -1.5F;
        moved[14] = 40F;
        float[] translated = Reprojection.project(Reprojection.matrix(projection, rotationY(0), projection, moved), 0, 0);
        check(Math.abs(translated[0] - centre[0]) < 1e-6 && Math.abs(translated[1] - centre[1]) < 1e-6, "Camera translation does not affect the rotation-only reprojection");
        float[] zoomed = Reprojection.project(Reprojection.matrix(perspective(30, 16F / 9F), rotationY(0), projection, rotationY(0)), 0.5F, 0.5F);
        check(zoomed[0] > 0.5F && zoomed[1] > 0.5F, "A field-of-view change produces a radial zoom blur");
        BlurVelocity velocity = new BlurVelocity();
        float[] shift = BlurVelocity.identity();
        shift[8] = 0.02F;
        float[] shortFrame = velocity.update(shift, 4, 40);
        velocity.reset();
        shift[8] = 0.08F;
        float[] longFrame = velocity.update(shift, 16, 40);
        check(Math.abs(shortFrame[8] - longFrame[8]) < 1e-6, "Constant camera speed yields equal shutter displacement across frame rates");
        shift[8] = 0.16F;
        float[] spike = velocity.update(shift, 16, 40);
        check(spike[8] > longFrame[8] && spike[8] < 0.4F, "Camera velocity filters isolated timing spikes");
        for (int i = 0; i < 20; i++) spike = velocity.update(BlurVelocity.identity(), 16, 40);
        check(Math.abs(spike[8]) < 1e-6, "Blur settles promptly when camera movement stops");
        float[] stale = velocity.update(shift, 150, 40);
        check(stale[8] == 0F, "A stalled frame resets velocity instead of smearing a camera cut");
        shift[8] = Float.NaN;
        check(velocity.update(shift, 16, 40)[8] == 0F, "Non-finite camera transforms cannot poison subsequent frames");
        shift[8] = 0.08F;
        check(Math.abs(velocity.update(shift, 16, 40)[8] - 0.2F) < 1e-6, "Velocity recovers immediately after invalid history");
    }

    private static float[] perspective(float fovDegrees, float aspect) {
        float f = (float) (1 / Math.tan(Math.toRadians(fovDegrees) / 2));
        float near = 0.05F;
        float far = 512F;
        float[] matrix = new float[16];
        matrix[0] = f / aspect;
        matrix[5] = f;
        matrix[10] = (far + near) / (near - far);
        matrix[11] = -1F;
        matrix[14] = 2 * far * near / (near - far);
        return matrix;
    }

    private static float[] rotationY(float angle) {
        float[] matrix = new float[16];
        matrix[0] = (float) Math.cos(angle);
        matrix[2] = (float) -Math.sin(angle);
        matrix[5] = 1F;
        matrix[8] = (float) Math.sin(angle);
        matrix[10] = (float) Math.cos(angle);
        matrix[15] = 1F;
        return matrix;
    }

    private static void presets(Path root) throws Exception {
        ModSettings settings = new ModSettings(root.toFile());
        check(settings.presetNames().size() == 1 && settings.activePreset().equals("Default"), "Existing configuration becomes Default");
        check(!settings.presetModified(), "A migrated configuration starts clean");
        settings.setEnabled("fps", false);
        settings.setOption("oldAnimations", "blockHit", false);
        ModSettings.Layout layout = new ModSettings.Layout();
        layout.x = 0.3F;
        layout.y = 0.4F;
        layout.scale = 2;
        layout.locked = true;
        settings.setLayout("cps", layout);
        check(settings.presetModified(), "Changes are distinguished from saved presets");
        settings.createPreset("  Duels  ");
        check(settings.activePreset().equals("Duels") && !settings.presetModified(), "Creation snapshots and selects the current configuration");
        layout.x = 0.9F;
        settings.layout("cps").scale = 3;
        settings.setOption("oldAnimations", "blockHit", true);
        settings.loadPreset("Default");
        check(settings.enabled("fps") == null && settings.layout("cps") == null, "Loading restores implicit defaults too");
        settings.loadPreset("Duels");
        check(Boolean.FALSE.equals(settings.enabled("fps")), "Preset restores mod toggles");
        check(Boolean.FALSE.equals(settings.option("oldAnimations", "blockHit")), "Version-specific options survive switching");
        check(settings.layout("cps").x == 0.3F && settings.layout("cps").scale == 2 && settings.layout("cps").locked,
                "Preset snapshots are isolated from live HUD mutations");
        settings.layout("cps").x = 0.6F;
        settings.savePreset();
        settings.layout("cps").x = 0.8F;
        settings.loadPreset("Duels");
        check(settings.layout("cps").x == 0.6F, "Save changes updates only the selected snapshot");
        settings.renamePreset("Duels", "PvP");
        ModSettings restored = new ModSettings(root.toFile());
        check(restored.activePreset().equals("PvP") && restored.presetNames().get(1).equals("PvP"), "Names, order and selection survive restart");
        check(!restored.presetModified() && restored.layout("cps").x == 0.6F, "Snapshots and layouts survive restart");
        restored.setEnabled("cps", false);
        ModSettings dirty = new ModSettings(root.toFile());
        check(dirty.presetModified() && Boolean.FALSE.equals(dirty.enabled("cps")), "Unsaved working changes survive restart separately from the snapshot");
        dirty.loadPreset("PvP");
        check(dirty.enabled("cps") == null, "Reloading the selected preset discards working changes");
        rejected(() -> restored.createPreset("pvp"), "Names are case-insensitively unique");
        rejected(() -> restored.createPreset(" "), "Empty names are rejected");
        rejected(() -> restored.createPreset("bad\nname"), "Control characters are rejected");
        rejected(() -> restored.createPreset("abcdefghijklmnopqrstuvwxyz"), "Overlong names are rejected");
        rejected(() -> restored.renamePreset("PvP", "Default"), "Renaming cannot overwrite another preset");
        rejected(() -> restored.loadPreset("Missing"), "Missing presets are rejected");
        restored.deletePreset("PvP");
        check(restored.activePreset().equals("Default") && restored.layout("cps") == null, "Deleting the active preset loads the first remaining preset");
        rejected(() -> restored.deletePreset("Default"), "The last preset cannot be deleted");
        Host host = new Host(root.resolve("registry"));
        host.world = true;
        CometClient client = new CometClient(host.bridge);
        client.tick();
        Mod toggle = find(client, "toggleSprint");
        toggle.setOption(option(toggle, "autoSprint"), false);
        toggle.setOption(option(toggle, "toggleSneak"), true);
        client.mods().settings().createPreset("Manual");
        client.keyState(host.sneakKey, true);
        check(client.holdsSneak(), "Test begins with a latched sneak");
        client.mods().loadPreset("Manual");
        client.tick();
        check(!client.holdsSneak() && !client.holdsSprint(), "Loading a preset clears transient movement state");
        Path failureRoot = root.resolve("failure");
        ModSettings failure = new ModSettings(failureRoot.toFile());
        Files.createDirectories(failureRoot.resolve("comet/settings.json/occupied"));
        rejected(() -> failure.createPreset("Cannot write"), "Persistence failures are reported");
        check(failure.presetNames().size() == 1 && failure.activePreset().equals("Default"), "Failed writes do not change preset state");
    }

    private static void presetMenu(Host host, CometClient client) throws Exception {
        host.screen.draw(host.canvas, -1, -1);
        host.screen.mouseDown(45, 240, 0);
        for (char character : "Duels".toCharArray()) host.screen.key(character, 0);
        host.screen.draw(host.canvas, -1, -1);
        host.canvas.save("preset-create");
        host.screen.key('\0', Keys.ENTER);
        check(client.mods().settings().activePreset().equals("Duels"), "The sidebar creates a named preset");
        host.screen.mouseDown(35, 86, 0);
        check(client.mods().settings().activePreset().equals("Duels"), "A preset row loads its snapshot");
        Mod cps = find(client, "cps");
        client.mods().toggle(cps);
        host.screen.mouseDown(45, 218, 0);
        check(!client.mods().settings().presetModified(), "Save changes persists through the sidebar");
        host.screen.mouseDown(35, 66, 0);
        check(client.mods().settings().activePreset().equals("Default") && client.mods().isEnabled(cps), "Switching rows applies another mod configuration");
        host.screen.mouseDown(82, 86, 0);
        for (int index = 0; index < 5; index++) host.screen.key('\0', Keys.BACKSPACE);
        for (char character : "Practice".toCharArray()) host.screen.key(character, 0);
        host.screen.key('\0', Keys.ENTER);
        check(client.mods().settings().presetNames().contains("Practice"), "The pencil control renames a preset");
        host.screen.mouseDown(82, 86, 0);
        host.screen.mouseDown(240, 197, 0);
        check(!client.mods().settings().presetNames().contains("Practice"), "The edit dialog deletes a preset");
        for (int index = 0; index < 15; index++) client.mods().settings().createPreset("Preset " + index);
        host.screen.scroll(40, 90, -30);
        host.screen.draw(host.canvas, -1, -1);
        check(host.canvas.clipsBalanced(), "Long preset lists are clipped independently of the cards");
        host.canvas.save("presets-scrolled");
    }

    private static final class Host {
        final GameHost bridge;
        TestCanvas canvas = new TestCanvas(512, 300);
        boolean world;
        boolean shift;
        boolean blurred;
        boolean sprinting;
        int sprintKey = 29;
        int sneakKey = 42;
        String version = "1.8.9";
        int physicalKey = Integer.MIN_VALUE;
        int forwardKey = 17;
        double[] position;
        int ping = -1;
        boolean local;
        Screen screen;

        Host(Path root) {
            bridge = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "gameDirectory": return root.toFile();
                    case "version": return version;
                    case "inWorld": return world;
                    case "screenOpen": return screen != null;
                    case "keyDown": return physicalKey == (Integer) args[0] || shift && (Integer) args[0] == Keys.RIGHT_SHIFT;
                    case "controlKey": return new int[] {forwardKey, 30, 31, 32, 57, -100, -99}[((comet.core.bridge.Control) args[0]).ordinal()];
                    case "keyName":
                        switch ((Integer) args[0]) {
                            case 17: return "W";
                            case 30: return "A";
                            case 31: return "S";
                            case 32: return "D";
                            case 57: return "SPACE";
                            case -100: return "LMB";
                            case -99: return "RMB";
                            default: return "--";
                        }
                    case "position": return position;
                    case "ping": return ping;
                    case "singleplayer": return local;
                    case "sprintKey": return sprintKey;
                    case "sneakKey": return sneakKey;
                    case "sprinting": return sprinting;
                    case "setSprinting": sprinting = (Boolean) args[0]; return null;
                    case "fps": return 144;
                    case "canvas": return canvas;
                    case "blur": blurred = (Boolean) args[0]; return null;
                    case "openScreen":
                        if (screen != null) screen.closed();
                        screen = (Screen) args[0];
                        screen.resize(canvas.width(), canvas.height());
                        screen.opened();
                        return null;
                    case "closeScreen":
                        if (screen != null) screen.closed();
                        screen = null;
                        return null;
                    default: throw new AssertionError("Unexpected host call " + method.getName());
                }
            });
        }
    }
}
