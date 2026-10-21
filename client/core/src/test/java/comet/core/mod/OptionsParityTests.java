package comet.core.mod;

import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.ui.ModOptions;
import comet.core.ui.TestCanvas;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OptionsParityTests {
    public static void run(Path root) throws Exception {
        ModSettings settings = new ModSettings(root.toFile());
        Fixture mod = new Fixture();
        mod.attach(settings);
        GameHost host = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> "Key " + args[0]);
        ModOptions panel = new ModOptions(mod, host);
        TestCanvas canvas = new TestCanvas(320, 240);
        panel.draw(canvas, 320, 240, -1, -1);
        panel.click(280, 66, 0);
        check(mod.number(Fixture.DECIMAL) == 210 && mod.decimal(Fixture.DECIMAL) == 2.1, "Two-decimal options step by a usable tenth");
        mod.setNumber(Fixture.DECIMAL, 420);
        panel.click(280, 66, 0);
        check(mod.decimal(Fixture.DECIMAL) == 4.25, "Decimal steps still reach a fractional endpoint");
        panel.click(230, 94, 0);
        panel.key('\0', Keys.RIGHT_SHIFT);
        check(mod.number(Fixture.KEY) == Keys.RIGHT_SHIFT && panel.isOpen(), "Capture menu key without closing");
        panel.click(230, 94, 0);
        panel.key('x', 0);
        check(mod.number(Fixture.KEY) == Keys.RIGHT_SHIFT, "Character-only events leave the binding unchanged");
        panel.click(230, 94, 1);
        check(mod.number(Fixture.KEY) == Keys.MOUSE_RIGHT, "Mouse binding capture");
        panel.click(230, 122, 0);
        panel.key('x', 0);
        panel.key('\0', Keys.ENTER);
        check(mod.text(Fixture.TEXT).equals("x"), "Text draft commits");
        panel.click(230, 122, 0);
        panel.key('z', 0);
        panel.key('\0', Keys.ESCAPE);
        check(mod.text(Fixture.TEXT).equals("x"), "Escape discards text draft");
        panel.scroll(-100);
        panel.draw(canvas, 320, 240, -1, -1);
        panel.click(240, 198, 0);
        check(mod.actions == 1 && canvas.clipsBalanced(), "Scrolled action remains reachable with balanced clipping");
        settings.createPreset("Custom");
        mod.setText(Fixture.TEXT, "changed");
        check(settings.presetModified(), "Text edits dirty preset");
        settings.loadPreset("Custom");
        check(mod.text(Fixture.TEXT).equals("x"), "Text snapshot deep copy");
        ModSettings reloaded = new ModSettings(root.toFile());
        check("x".equals(reloaded.text(mod.id(), Fixture.TEXT.id)), "Text restart persistence");
        check(Fixture.TEXT.sanitize("a\nb\u00a7c").equals("abc"), "Reject controls and game formatting");
        ModSettings failure = new ModSettings(root.resolve("failure").toFile());
        Files.createDirectories(root.resolve("failure/comet/settings.json/occupied"));
        try {
            failure.setText("fixture", "text", "lost");
            throw new AssertionError("Write should fail");
        } catch (IllegalStateException expected) {
            check(failure.text("fixture", "text") == null, "Failed write keeps memory unchanged");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Fixture extends Mod {
        static final NumberOption DECIMAL = NumberOption.decimal("decimal", "Decimal", 2, 1, 4.25, 2);
        static final NumberOption KEY = NumberOption.keybind("key", "Key");
        static final TextOption TEXT = new TextOption("text", "Text", "default", 12);
        int actions;

        public String id() { return "fixture"; }
        public String name() { return "Fixture"; }
        public String description() { return ""; }
        public Category category() { return Category.HUD; }
        public List<NumberOption> numbers() { return Arrays.asList(DECIMAL, KEY); }
        public List<TextOption> texts() { return Arrays.asList(TEXT, new TextOption("two", "Two", "", 12), new TextOption("three", "Three", "", 12), new TextOption("four", "Four", "", 12)); }
        public List<ActionOption> actions() { return Collections.singletonList(new ActionOption("add", "Add", () -> actions++)); }
    }
}
