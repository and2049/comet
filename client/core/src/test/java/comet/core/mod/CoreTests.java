package comet.core.mod;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.ui.HudEditor;
import comet.core.ui.ModMenu;
import comet.core.ui.Screen;
import comet.core.ui.TestCanvas;
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
        check(new CometClient(legacy.bridge).mods().all().size() == 3, "1.7.10 uses its native animations");
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

    private static void rejected(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            check(true, message);
            return;
        }
        check(false, message);
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
        Screen screen;

        Host(Path root) {
            bridge = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "gameDirectory": return root.toFile();
                    case "version": return version;
                    case "inWorld": return world;
                    case "screenOpen": return screen != null;
                    case "keyDown": return shift && (Integer) args[0] == Keys.RIGHT_SHIFT;
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
