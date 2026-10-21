package comet.core.mod;

import comet.core.bridge.Control;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.ui.ModOptions;
import comet.core.ui.TestCanvas;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class KeystrokesParityTests {
    private static int assertions;

    public static void run(Path root) throws Exception {
        Files.createDirectories(root);
        geometry(new Fixture(root.resolve("geometry")));
        elements(new Fixture(root.resolve("elements")));
        labels(new Fixture(root.resolve("labels")));
        state(new Fixture(root.resolve("state")));
        cps(new Fixture(root.resolve("cps")));
        customs(new Fixture(root.resolve("customs")));
        malformed(new Fixture(root.resolve("malformed")));
        gui(new Fixture(root.resolve("gui")));
        System.out.println("Keystrokes parity: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void geometry(Fixture f) {
        TestCanvas canvas = new TestCanvas(512, 300);
        check(f.keys.width(canvas) == 70 && f.keys.height(canvas) == 88, "Default grouped keystrokes keep the existing cells");
        f.keys.setOption(Keystrokes.JUMP, false);
        check(f.keys.height(canvas) == 72, "A disabled jump row keeps its spacer");
        f.keys.setOption(Keystrokes.MOVEMENT, false);
        check(f.keys.height(canvas) == 26, "The mouse row keeps both residual spacers");
        f.keys.setOption(Keystrokes.MOVEMENT, true);
        f.keys.setOption(Keystrokes.JUMP, true);
        f.keys.setOption(Keystrokes.MOUSE, false);
        check(f.keys.height(canvas) == 64, "Movement and jump pack above a disabled mouse row");
        f.keys.setOption(Keystrokes.MOUSE, true);
        check(f.keys.height(canvas) == 88 && f.keys.width(canvas) == 70, "Re-enabling rows restores the grouped bounds");
        f.keys.setOption(Keystrokes.JUMP, false);
        f.keys.setOption(Keystrokes.MOUSE, false);
        f.keys.setNumber(Keystrokes.BOX_SIZE, 32);
        check(f.keys.width(canvas) == 100 && f.keys.height(canvas) == 66, "Box size scales the movement grid");
        f.keys.setNumber(Keystrokes.BOX_SIZE, 10);
        check(f.keys.width(canvas) == 34 && f.keys.height(canvas) == 22, "The minimum box size keeps whole-pixel cells");
        f.keys.setNumber(Keystrokes.BOX_SIZE, 22);
        f.keys.setOption(Keystrokes.JUMP, true);
        f.keys.setOption(Keystrokes.MOUSE, true);
    }

    private static void elements(Fixture f) {
        TestCanvas canvas = new TestCanvas(512, 300);
        List<HudMod> grouped = f.keys.elements();
        check(grouped.size() == 1 && grouped.get(0) == f.keys, "Grouped mode draws the parent as a single element");
        f.keys.setNumber(Keystrokes.MODE, 1);
        check(f.keys.width(canvas) == 0 && f.keys.height(canvas) == 0, "Individual mode leaves the group empty");
        List<HudMod> individual = f.keys.elements();
        check(individual.size() == 7, "Individual mode exposes one element per stock key");
        check(unique(ids(individual)) == 7, "Individual element ids are unique");
        check(visible(individual) == 7, "Every enabled row is visible");
        f.keys.setOption(Keystrokes.MOUSE, false);
        check(visible(f.keys.elements()) == 5, "Disabling the mouse row hides its two elements");
        f.keys.setOption(Keystrokes.MOUSE, true);
        HudMod forward = find(f.keys, "keystrokes.forward");
        HudMod jump = find(f.keys, "keystrokes.jump");
        HudMod attack = find(f.keys, "keystrokes.attack");
        check(forward.width(canvas) == 22 && forward.height(canvas) == 22, "Movement cells stay square");
        check(jump.width(canvas) == 70 && jump.height(canvas) == 16, "The jump cell keeps the grouped jump size");
        check(attack.width(canvas) == 34 && attack.height(canvas) == 22, "Mouse cells keep their grouped size");
        check(forward.defaultX() != jump.defaultX() || forward.defaultY() != jump.defaultY(), "Individual elements start at distinct positions");
        f.keys.setNumber(Keystrokes.BOX_SIZE, 32);
        check(forward.width(canvas) == 32 && jump.height(canvas) == 24, "Individual cells follow the box size");
        f.keys.setNumber(Keystrokes.BOX_SIZE, 22);
        f.keys.setNumber(Keystrokes.MODE, 0);
    }

    private static void labels(Fixture f) {
        TestCanvas canvas = render(f.keys);
        check(canvas.texts.containsAll(Arrays.asList("W", "A", "S", "D", "SPACE", "LMB", "RMB")), "Default labels use the current vanilla bindings");
        f.host.controls[Control.FORWARD.ordinal()] = 33;
        canvas = render(f.keys);
        check(canvas.texts.contains("UP") && !canvas.texts.contains("W"), "Movement labels follow rebinding");
        f.keys.setOption(Keystrokes.ARROWS, true);
        canvas = render(f.keys);
        check(canvas.texts.contains("\u2191") && !canvas.texts.contains("UP"), "Arrow labels replace movement key names");
        check(canvas.texts.contains("SPACE"), "Arrow labels leave the jump key alone");
        f.keys.setOption(Keystrokes.ARROWS, false);
        f.keys.setNumber(Keystrokes.BOX_SIZE, 10);
        canvas = render(f.keys);
        check(canvas.texts.contains("L") && !canvas.texts.contains("LMB"), "Small cells truncate stock labels");
        f.keys.setNumber(Keystrokes.BOX_SIZE, 22);
        f.keys.setOption(Keystrokes.JUMP_BAR, true);
        canvas = render(f.keys);
        check(!canvas.texts.contains("SPACE"), "The jump bar replaces the jump label");
        f.keys.setOption(Keystrokes.JUMP_BAR, false);
        f.host.controls[Control.ATTACK.ordinal()] = 0;
        canvas = render(f.keys);
        check(canvas.texts.contains("--"), "Unbound controls show no key name");
        f.host.controls[Control.ATTACK.ordinal()] = Keys.MOUSE_LEFT;
        f.host.controls[Control.FORWARD.ordinal()] = 17;
    }

    private static void state(Fixture f) {
        f.host.physicalKey = 17;
        check(f.keys.pressed(Control.FORWARD), "A held physical key lights its cell");
        check(pressedIndicator(f.keys), "The pressed cell draws the held indicator");
        f.host.screen = true;
        check(!f.keys.pressed(Control.FORWARD), "GUI input does not light keystrokes");
        f.host.screen = false;
        f.host.world = false;
        check(!f.keys.pressed(Control.FORWARD), "World exit clears keystrokes without stale state");
        f.host.world = true;
        f.keys.preview(true);
        check(!f.keys.pressed(Control.FORWARD), "The HUD editor preview suppresses held state");
        check(!pressedIndicator(f.keys), "Preview mode draws no pressed indicator");
        f.keys.setNumber(Keystrokes.MODE, 1);
        HudMod forward = find(f.keys, "keystrokes.forward");
        check(!pressedIndicator(forward), "Individual cells suppress held state in preview");
        f.keys.preview(false);
        check(pressedIndicator(forward), "Individual cells follow held state outside preview");
        f.keys.setNumber(Keystrokes.MODE, 0);
        f.host.physicalKey = Integer.MIN_VALUE;
        check(!pressedIndicator(f.keys), "Released keys clear the indicator");
        f.keys.preview(true);
        f.settings.setGlobal(GlobalSetting.HUD_BACKGROUND, false);
        f.settings.setGlobal(GlobalSetting.TEXT_SHADOW, false);
        TestCanvas plain = render(f.keys);
        check(plain.roundedColors.isEmpty() && !plain.shadows.isEmpty() && !plain.shadows.contains(true),
                "Global appearance off removes every keystroke surface");
        f.settings.setGlobal(GlobalSetting.HUD_BACKGROUND, true);
        f.settings.setGlobal(GlobalSetting.TEXT_SHADOW, true);
        TestCanvas styled = render(f.keys);
        check(styled.roundedColors.contains(0x70000000) && !styled.shadows.contains(false),
                "Global appearance restores keystroke surfaces");
        f.keys.preview(false);
    }

    private static void cps(Fixture f) {
        f.stats.record(Keys.MOUSE_LEFT, true, false);
        f.stats.record(Keys.MOUSE_RIGHT, true, false);
        check(f.stats.left(false) == 1 && f.stats.right() == 1, "The shared counter records physical mouse buttons");
        TestCanvas canvas = render(f.keys);
        check(count(canvas.texts, "1") == 0, "CPS subtitles stay off by default");
        f.keys.setOption(Keystrokes.LEFT_CPS, true);
        f.keys.setOption(Keystrokes.RIGHT_CPS, true);
        f.host.controls[Control.ATTACK.ordinal()] = 33;
        f.host.controls[Control.USE.ordinal()] = 48;
        canvas = render(f.keys);
        check(count(canvas.texts, "1") == 2, "Both mouse cells show their physical CPS subtitle");
        check(canvas.texts.contains("UP") && canvas.texts.contains("B"), "Mouse labels still follow their rebound bindings");
        f.host.controls[Control.ATTACK.ordinal()] = Keys.MOUSE_LEFT;
        f.host.controls[Control.USE.ordinal()] = Keys.MOUSE_RIGHT;
        f.keys.setOption(Keystrokes.LEFT_CPS, false);
        f.keys.setOption(Keystrokes.RIGHT_CPS, false);
    }

    private static void customs(Fixture f) {
        f.keys.setNumber(Keystrokes.MODE, 1);
        List<ActionOption> actions = f.keys.actions();
        check(actions.size() == 1 && actions.get(0).id.equals("addCustomKey"), "Custom keys start empty with a single add action");
        actions.get(0).run();
        check(ids(f.keys.elements()).contains("keystrokes.custom.1"), "Adding a key creates a stable custom id");
        actions.get(0).run();
        check(ids(f.keys.elements()).contains("keystrokes.custom.2"), "New keys take the next free number");
        check(unique(ids(f.keys.elements())) == 9, "Custom keys keep every element id unique");
        check(f.keys.numbers().size() == 5 && f.keys.texts().size() == 2, "Each custom key exposes one binding and one caption");
        NumberOption binding = number(f.keys, "customKey");
        TextOption caption = f.keys.texts().get(0);
        check(binding.keyBinding && f.keys.number(binding) == 0, "Custom keys start unbound");
        f.keys.setNumber(binding, 48);
        HudMod custom = find(f.keys, "keystrokes.custom.1");
        check(render(custom).texts.contains("B"), "Custom captions follow their binding");
        check(render(custom).texts.size() == 1, "Custom cells never draw a CPS subtitle");
        f.keys.setText(caption, "jump");
        check(f.keys.text(caption).equals("jump"), "Custom captions route through the parent settings");
        check(render(custom).texts.contains("jump"), "Custom text wins over the bound key name");
        check(custom.width(new TestCanvas(64, 64)) == 46, "Long custom captions widen the cell by the box size");
        f.keys.setText(caption, "a");
        check(custom.width(new TestCanvas(64, 64)) == 22, "Short custom captions keep the box width");
        check(f.keys.number(number(f.keys, "customKey")) == 48, "A caption edit leaves the binding alone");
        List<String> before = ids(f.keys.elements());
        Keystrokes reloaded = f.reopen();
        reloaded.setNumber(Keystrokes.MODE, 1);
        check(ids(reloaded.elements()).equals(before), "Custom ids and order survive reload");
        check(reloaded.text(reloaded.texts().get(0)).equals("a"), "Custom text survives reload");
        check(reloaded.number(number(reloaded, "customKey")) == 48, "Custom bindings survive reload");
        ModSettings.Layout layout = new ModSettings.Layout();
        layout.x = 0.4F;
        layout.y = 0.5F;
        layout.scale = 1.5F;
        f.settings.setLayout("keystrokes.custom.1", layout);
        f.settings.savePreset();
        reloaded.actions().get(0).run();
        check(ids(reloaded.elements()).contains("keystrokes.custom.3"), "Keys added after a preset take the next number");
        f.registry.loadPreset(f.settings.activePreset());
        check(ids(reloaded.elements()).equals(before), "Loading a preset restores its custom key set");
        check(f.settings.layout("keystrokes.custom.1").scale == 1.5F, "Custom key positions survive preset switching");
        check("1,2".equals(f.settings.text("keystrokes", "customKeys")), "The hidden id list is stored with the preset");
        reloaded.actions().get(1).run();
        check(!ids(reloaded.elements()).contains("keystrokes.custom.1"), "Removing a key drops its id");
        check(ids(reloaded.elements()).contains("keystrokes.custom.2"), "Removing a key leaves the others alone");
        check("2".equals(f.settings.text("keystrokes", "customKeys")), "Removal updates the hidden id list");
        reloaded.actions().get(0).run();
        check(ids(reloaded.elements()).contains("keystrokes.custom.3"), "Numbers continue after the highest remaining key");
        reloaded.actions().get(2).run();
        reloaded.actions().get(0).run();
        check(ids(reloaded.elements()).contains("keystrokes.custom.4") && !ids(reloaded.elements()).contains("keystrokes.custom.3"),
                "Removing the newest key cannot resurrect its old settings on the next add");
    }

    private static void malformed(Fixture f) {
        f.settings.setText("keystrokes", "customKeys", "abc,3,,7,x9,3,0,-2,9999");
        Keystrokes keys = f.reopen();
        keys.setNumber(Keystrokes.MODE, 1);
        List<String> custom = ids(keys.elements()).subList(7, ids(keys.elements()).size());
        check(custom.equals(Arrays.asList("keystrokes.custom.3", "keystrokes.custom.7")), "Malformed ids are skipped individually and duplicates are dropped");
        StringBuilder many = new StringBuilder();
        for (int number = 1; number <= 40; number++) {
            if (number > 1) {
                many.append(',');
            }
            many.append(number);
        }
        f.settings.setText("keystrokes", "customKeys", many.toString());
        Keystrokes capped = f.reopen();
        capped.setNumber(Keystrokes.MODE, 1);
        check(ids(capped.elements()).size() == 7 + 16, "Oversized id lists are truncated to the custom key limit");
        for (int index = 0; index < 20; index++) {
            try {
                capped.actions().get(0).run();
            } catch (IllegalArgumentException expected) {
                break;
            }
        }
        check(capped.numbers().size() == 3 + 16, "Adding past the limit is rejected");
    }

    private static void gui(Fixture f) {
        Keystrokes keys = f.reopen();
        check(ModOptions.available(keys), "Keystrokes exposes an options panel");
        TestCanvas canvas = new TestCanvas(512, 600);
        ModOptions panel = new ModOptions(keys, f.host.bridge);
        panel.draw(canvas, 512, 600, -1, -1);
        check(canvas.clipsBalanced(), "Options drawing restores clipping");
        click(panel, keys, canvas, keys.options().size() + keys.numbers().size() + keys.texts().size(), 20);
        check(keys.numbers().size() == 4 && keys.texts().size() == 1 && keys.actions().size() == 2, "The add action creates one custom key");
        NumberOption binding = number(keys, "customKey");
        click(panel, keys, canvas, keys.options().size() + 3, 100);
        click(panel, keys, canvas, keys.options().size() + 3, 100);
        check(keys.number(binding) == Keys.MOUSE_LEFT, "The panel assigns a physical mouse binding to a custom key");
        click(panel, keys, canvas, keys.options().size() + keys.numbers().size(), 100);
        for (char character : "duel".toCharArray()) {
            panel.key(character, 0);
        }
        panel.key('\0', Keys.ENTER);
        check(keys.text(keys.texts().get(0)).equals("duel"), "The panel edits custom key text");
        click(panel, keys, canvas, keys.options().size() + keys.numbers().size() + keys.texts().size() + 1, 20);
        check(keys.texts().isEmpty() && keys.actions().size() == 1, "The panel removes a custom key");
    }

    private static void click(ModOptions panel, Keystrokes keys, TestCanvas canvas, int row, int offsetX) {
        panel.draw(canvas, 512, 600, -1, -1);
        int rows = keys.options().size() + keys.numbers().size() + keys.texts().size() + keys.actions().size();
        int width = Math.min(320, 512 - 20);
        int height = Math.min(600 - 30, 50 + rows * 28);
        panel.click((512 - width) / 2 + offsetX, (600 - height) / 2 + 30 + row * 28 + 10, 0);
    }

    private static TestCanvas render(HudMod mod) {
        TestCanvas canvas = new TestCanvas(320, 240);
        mod.render(canvas);
        return canvas;
    }

    private static boolean pressedIndicator(HudMod mod) {
        return render(mod).roundedColors.contains(0x907CA85A);
    }

    private static List<String> ids(List<HudMod> elements) {
        List<String> ids = new ArrayList<String>();
        for (HudMod element : elements) {
            ids.add(element.id());
        }
        return ids;
    }

    private static int unique(List<String> values) {
        List<String> seen = new ArrayList<String>();
        for (String value : values) {
            if (!seen.contains(value)) {
                seen.add(value);
            }
        }
        return seen.size();
    }

    private static int visible(List<HudMod> elements) {
        int total = 0;
        for (HudMod element : elements) {
            if (element.visible()) {
                total++;
            }
        }
        return total;
    }

    private static int count(List<String> values, String value) {
        int total = 0;
        for (String candidate : values) {
            if (candidate.equals(value)) {
                total++;
            }
        }
        return total;
    }

    private static HudMod find(Keystrokes keys, String id) {
        for (HudMod element : keys.elements()) {
            if (element.id().equals(id)) {
                return element;
            }
        }
        throw new AssertionError("Missing keystroke element " + id);
    }

    private static NumberOption number(Keystrokes keys, String id) {
        for (NumberOption option : keys.numbers()) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        throw new AssertionError("Missing number option " + id);
    }

    private static final class Fixture {
        final Host host;
        final ModSettings settings;
        final ModRegistry registry;
        final ClickStats stats = new ClickStats();
        final Keystrokes keys;

        Fixture(Path root) throws Exception {
            Files.createDirectories(root);
            host = new Host(root);
            settings = new ModSettings(root.toFile());
            registry = new ModRegistry(settings);
            keys = new Keystrokes(host.bridge, stats);
            registry.register(keys);
        }

        Keystrokes reopen() {
            Keystrokes reopened = new Keystrokes(host.bridge, stats);
            registry.register(reopened);
            return reopened;
        }
    }

    private static final class Host {
        final GameHost bridge;
        final int[] controls = {17, 30, 31, 32, 57, Keys.MOUSE_LEFT, Keys.MOUSE_RIGHT};
        boolean world = true;
        boolean screen;
        int physicalKey = Integer.MIN_VALUE;
        TestCanvas canvas = new TestCanvas(512, 300);

        Host(final Path root) {
            bridge = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "gameDirectory": return root.toFile();
                    case "inWorld": return world;
                    case "screenOpen": return screen;
                    case "keyDown": return physicalKey == (Integer) args[0];
                    case "controlKey": return controls[((Control) args[0]).ordinal()];
                    case "keyName": return keyName((Integer) args[0]);
                    case "canvas": return canvas;
                    case "version": return "1.8.9";
                    default: throw new AssertionError("Unexpected host call " + method.getName());
                }
            });
        }

        private static String keyName(int key) {
            switch (key) {
                case 17: return "W";
                case 30: return "A";
                case 31: return "S";
                case 32: return "D";
                case 33: return "UP";
                case 48: return "B";
                case 57: return "SPACE";
                case Keys.MOUSE_LEFT: return "LMB";
                case Keys.MOUSE_RIGHT: return "RMB";
                default: return "--";
            }
        }
    }
}
