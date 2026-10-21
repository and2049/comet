package comet.core.mod;

import comet.core.bridge.CoordinateBackend;
import comet.core.bridge.GameHost;
import comet.core.ui.TestCanvas;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CoordinatesParityTests {
    private static int assertions;

    public static void run(Path root) {
        format();
        direction();
        signs();
        debug();
        counts();
        fallback(root);
        grouping(root);
        layouts(root);
        elements(root);
        reducedDebug(root);
        typing(root);
        clipboard(root);
        lifecycle(root);
        System.out.println("Coordinates parity: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Coordinates create(Path root, String name, Backend backend, double[][] position) {
        GameHost host = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(),
                new Class<?>[] {GameHost.class}, (proxy, method, args) -> {
                    String call = method.getName();
                    if (call.equals("coordinates")) {
                        return backend;
                    }
                    if (call.equals("position")) {
                        return position[0];
                    }
                    if (call.equals("inWorld")) {
                        return true;
                    }
                    if (call.equals("screenOpen")) {
                        return false;
                    }
                    if (call.equals("version")) {
                        return "1.8.9";
                    }
                    if (call.equals("toString")) {
                        return "CoordinateHost";
                    }
                    if (call.equals("hashCode")) {
                        return 0;
                    }
                    if (call.equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new AssertionError("Unexpected host call " + call);
                });
        Coordinates coordinates = new Coordinates(host);
        coordinates.attach(new ModSettings(root.resolve("coordinates").resolve(name).toFile()));
        return coordinates;
    }

    private static HudMod element(List<HudMod> elements, String id) {
        for (HudMod element : elements) {
            if (element.id().equals(id)) {
                return element;
            }
        }
        throw new AssertionError("Missing element " + id);
    }

    private static void format() {
        check(Coordinates.format(-0.01, false).equals("-1"), "Near-zero negative coordinates floor to minus one");
        check(Coordinates.format(63.99, false).equals("63"), "Positive coordinates floor toward negative infinity");
        check(Coordinates.format(-123.5, false).equals("-124"), "Negative coordinates floor away from zero");
        check(Coordinates.format(1, false).equals("1"), "Whole coordinates stay integral");
        check(Coordinates.format(-0.019, true).equals("-0.01"), "Decimal coordinates truncate toward zero");
        check(Coordinates.format(12.349, true).equals("12.34"), "Decimal coordinates keep two places");
        check(Coordinates.format(-12.349, true).equals("-12.34"), "Negative decimals truncate toward zero");
        check(Coordinates.format(1, true).equals("1.0"), "Whole decimal coordinates keep a fractional digit");
        check(Coordinates.format(2147483647.5, false).equals("2147483647"), "Large positive integers floor correctly");
        check(Coordinates.format(-2147483649.5, false).equals("-2147483650"), "Large negatives never wrap to positive");
        check(Coordinates.format(1234.5, true).equals("1234.5"), "Exact decimals keep their fractional digit");
        check(Coordinates.format(9999999.5, true).equals("9999999.5"), "Decimals below ten million stay plain");
        check(Coordinates.format(10000000.0, true).equals("10000000.0"), "World coordinates stay readable without scientific notation");
        check(Coordinates.format(29999999.98, true).equals("29999999.98"), "Decimals remain correct near the world border");
        check(Coordinates.format(Double.NaN, true).equals("--"), "Unavailable positions never produce misleading numbers");
    }

    private static void direction() {
        check(Coordinates.cardinal(0).equals("S"), "Yaw zero faces south");
        check(Coordinates.cardinal(90).equals("W"), "Yaw 90 faces west");
        check(Coordinates.cardinal(180).equals("N"), "Yaw 180 faces north");
        check(Coordinates.cardinal(-90).equals("E"), "Negative yaw faces east");
        check(Coordinates.cardinal(45).equals("SW"), "Yaw 45 faces south-west");
        check(Coordinates.cardinal(135).equals("NW"), "Yaw 135 faces north-west");
        String[] below = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String[] at = {"NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        for (int step = 0; step < 8; step++) {
            float boundary = -157.5F + 45F * step;
            check(Coordinates.cardinal(boundary - 0.1F).equals(below[step]),
                    "Below the boundary at step " + step + " keeps the previous cardinal");
            check(Coordinates.cardinal(boundary).equals(at[step]), "The boundary at step " + step + " advances");
        }
        check(Coordinates.cardinal(360).equals("S") && Coordinates.cardinal(540).equals("N"), "Yaw wraps past a turn");
        check(Coordinates.cardinal(-360).equals("S") && Coordinates.cardinal(-540).equals("N"),
                "Negative yaw wraps symmetrically");
        for (int step = 0; step < 8; step++) {
            check(Coordinates.cardinal(step * 45).equals(Coordinates.cardinal(step * 45 - 360)),
                    "Cardinals repeat every turn at " + step);
        }
    }

    private static void signs() {
        check(Coordinates.sign("W", true).equals("-") && Coordinates.sign("E", true).equals("+"),
                "West and east sign the X axis");
        check(Coordinates.sign("N", false).equals("-") && Coordinates.sign("S", false).equals("+"),
                "North and south sign the Z axis");
        check(Coordinates.sign("N", true).isEmpty() && Coordinates.sign("S", true).isEmpty(),
                "North and south leave the X axis unsigned");
        check(Coordinates.sign("E", false).isEmpty() && Coordinates.sign("W", false).isEmpty(),
                "East and west leave the Z axis unsigned");
        check(Coordinates.sign("NW", true).equals("-") && Coordinates.sign("SE", false).equals("+"),
                "Diagonals sign both of their axes");
        check(Coordinates.sign("NE", true).equals("+") && Coordinates.sign("SW", false).equals("+"),
                "Diagonals follow the axis they lean towards");
    }

    private static void debug() {
        check(Coordinates.suppressed(true, false), "Reduced debug suppresses elements");
        check(!Coordinates.suppressed(true, true), "Preview bypasses reduced debug");
        check(!Coordinates.suppressed(false, false) && !Coordinates.suppressed(false, true),
                "Normal debug shows every element");
    }

    private static void counts() {
        int[] rendered = CoordinateBackend.renderCounts("C: 92/4269 (s)D: 5, P: 12");
        check(rendered != null && rendered[0] == 92 && rendered[1] == 4269, "Renderer counts parse the modern debug line");
        int[] legacy = CoordinateBackend.renderCounts("C: 7/1024. F: 0, O: 3, E: 5");
        check(legacy != null && legacy[0] == 7 && legacy[1] == 1024, "Renderer counts parse the legacy debug line");
        check(CoordinateBackend.renderCounts(null) == null, "A missing debug line has no counts");
        check(CoordinateBackend.renderCounts("no counters here") == null, "An unrelated debug line has no counts");
        check(CoordinateBackend.renderCounts("C: x/y") == null, "Malformed counters are reported as unavailable");
        check(CoordinateBackend.NONE.renderCounts() == null, "The default backend reports counters as unavailable");
        check(CoordinateBackend.NONE.position() == null && CoordinateBackend.NONE.biomeName() == null,
                "The default backend has no game state");
        check(!CoordinateBackend.NONE.reducedDebugInfo(), "The default backend has no reduced debug");
        check(Float.isNaN(CoordinateBackend.NONE.cameraYaw()) && Float.isNaN(CoordinateBackend.NONE.playerYaw()),
                "The default backend has no camera");
    }

    private static void fallback(Path root) {
        double[][] position = {{-0.01, 63.99, -123.5}};
        Coordinates coordinates = create(root, "fallback", null, position);
        check(Arrays.equals(coordinates.labels(), new String[] {"X: -1", "Y: 63", "Z: -124"}),
                "A missing backend falls back to the host position");
        TestCanvas canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("X: ") && canvas.texts.contains("-1"), "The host position still renders");
        position[0] = null;
        check(coordinates.labels()[0].equals("X: --"), "A missing player is explicit without a backend");
        Backend backend = new Backend();
        double[][] live = {{12.7, 64.2, -8.1}};
        Coordinates bridged = create(root, "fallback-bridge", backend, live);
        backend.pos = live[0];
        check(Arrays.equals(bridged.labels(), new String[] {"X: 12", "Y: 64", "Z: -9"}),
                "A backend position overrides the host position");
    }

    private static void grouping(Path root) {
        Backend backend = new Backend();
        double[][] position = {{12.7, 64.2, -8.1}};
        Coordinates coordinates = create(root, "grouping", backend, position);
        backend.pos = position[0];
        check(coordinates.option(Coordinates.SHOW_X) && coordinates.option(Coordinates.SHOW_Y)
                && coordinates.option(Coordinates.SHOW_Z), "X, Y and Z start enabled");
        check(!coordinates.option(Coordinates.SHOW_C) && !coordinates.option(Coordinates.SHOW_DIRECTION)
                && !coordinates.option(Coordinates.SHOW_BIOME), "Optional elements start hidden");
        check(coordinates.number(Coordinates.MODE) == 0, "Vertical is the default layout");
        check(!coordinates.option(Coordinates.DECIMAL) && !coordinates.option(Coordinates.MOVE_CHILDREN),
                "Integer coordinates stay grouped by default");
        check(coordinates.number(Coordinates.COPY) == 0, "The copy keybind starts unbound");
        List<HudMod> grouped = coordinates.elements();
        check(grouped.size() == 1 && grouped.get(0) == coordinates, "Grouped mode exposes the parent only");
        check(coordinates.visible(), "The grouped HUD is visible with a player in a world");
        coordinates.setOption(Coordinates.MOVE_CHILDREN, true);
        List<HudMod> individual = coordinates.elements();
        check(individual.size() == 6, "Individual mode exposes every element");
        Set<String> ids = new HashSet<String>();
        for (HudMod element : individual) {
            ids.add(element.id());
        }
        check(ids.equals(new HashSet<String>(Arrays.asList("coordinates.x", "coordinates.y", "coordinates.z",
                "coordinates.c", "coordinates.direction", "coordinates.biome"))), "Elements own namespaced ids");
        check(!coordinates.visible(), "The parent hides while elements move individually");
        coordinates.setOption(Coordinates.MOVE_CHILDREN, false);
        check(coordinates.elements().size() == 1, "Grouping hides the child elements again");
    }

    private static void layouts(Path root) {
        Backend backend = new Backend();
        double[][] position = {{12.7, 64.2, -8.1}};
        Coordinates coordinates = create(root, "layouts", backend, position);
        backend.pos = position[0];
        check(Arrays.equals(coordinates.labels(), new String[] {"X: 12", "Y: 64", "Z: -9"}),
                "Labels floor the default integer coordinates");
        TestCanvas canvas = new TestCanvas(320, 240);
        check(canvas.texts.isEmpty(), "A fresh canvas starts empty");
        coordinates.render(canvas);
        check(canvas.texts.contains("X: ") && canvas.texts.contains("Y: ") && canvas.texts.contains("Z: "),
                "Vertical rows carry their labels");
        check(canvas.texts.contains("12") && canvas.texts.contains("64") && canvas.texts.contains("-9"),
                "Vertical rows carry their floored values");
        check(canvas.roundedColors.contains(0x70000000), "The grouped HUD draws its background");
        coordinates.setOption(Coordinates.DECIMAL, true);
        check(Arrays.equals(coordinates.labels(), new String[] {"X: 12.7", "Y: 64.2", "Z: -8.1"}),
                "Labels truncate decimals to two places");
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("12.7") && canvas.texts.contains("-8.1"), "Decimal rows truncate toward zero");
        coordinates.setNumber(Coordinates.MODE, 1);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("X: 12.7, Y: 64.2, Z: -8.1"), "Horizontal rows join with comma separators");
        coordinates.setOption(Coordinates.SHOW_DIRECTION, true);
        backend.yaw = 90F;
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("X: 12.7 (-), Y: 64.2, Z: -8.1 Direction: W"), "Horizontal signs follow their axis");
        coordinates.setOption(Coordinates.LABEL_DIRECTION, false);
        coordinates.setNumber(Coordinates.MODE, 0);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("W") && canvas.texts.contains("-") && !canvas.texts.contains("+"),
                "Vertical west signs only the X row");
        backend.yaw = 0F;
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("S") && canvas.texts.contains("+") && !canvas.texts.contains("-"),
                "Vertical south signs only the Z row");
        backend.yaw = 180F;
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("N") && canvas.texts.contains("-") && !canvas.texts.contains("+"),
                "Vertical north signs the Z row negatively");
        backend.yaw = Float.NaN;
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(!canvas.texts.contains("N") && !canvas.texts.contains("S") && !canvas.texts.contains("W"),
                "A missing camera hides the direction element");
        backend.yaw = 180F;
        coordinates.setOption(Coordinates.SHOW_BIOME, true);
        backend.biome = "Plains";
        coordinates.tick();
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("Biome: ") && canvas.texts.contains("Plains"), "The biome row follows the labels");
        check(coordinates.biomeName().equals("Plains"), "The biome is cached for the next frame");
        coordinates.setOption(Coordinates.LABEL_Z, false);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(!canvas.texts.contains("Z: ") && canvas.texts.contains("-8.1") && canvas.texts.contains("X: "),
                "A single label toggle only affects its own row");
        coordinates.setOption(Coordinates.LABEL_Z, true);
        coordinates.setOption(Coordinates.SHOW_Y, false);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(!canvas.texts.contains("Y: ") && !canvas.texts.contains("64.2"), "Disabled elements drop their row");
        coordinates.setNumber(Coordinates.MODE, 1);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("X: 12.7, Z: -8.1 (-), Biome: Plains N"),
                "Horizontal keeps the enabled elements and the biome");
    }

    private static void elements(Path root) {
        Backend backend = new Backend();
        double[][] position = {{12.7, 64.2, -8.1}};
        Coordinates coordinates = create(root, "elements", backend, position);
        backend.pos = position[0];
        backend.yaw = 180F;
        coordinates.setOption(Coordinates.MOVE_CHILDREN, true);
        List<HudMod> individual = coordinates.elements();
        for (HudMod element : individual) {
            check(element.category() == Category.HUD, "Elements stay in the HUD category");
            check(element.enabledByDefault(), "Elements are enabled by default");
        }
        check(element(individual, "coordinates.x").visible() && element(individual, "coordinates.y").visible()
                && element(individual, "coordinates.z").visible(), "X, Y and Z are visible individually");
        check(!element(individual, "coordinates.c").visible() && !element(individual, "coordinates.biome").visible(),
                "Deselected elements hide individually");
        coordinates.setOption(Coordinates.SHOW_DIRECTION, true);
        backend.yaw = Float.NaN;
        check(!element(individual, "coordinates.direction").visible(), "Direction hides without a camera");
        backend.yaw = 180F;
        check(element(individual, "coordinates.direction").visible(), "Direction appears with a camera");
        coordinates.setOption(Coordinates.SHOW_C, true);
        backend.counts = new int[] {92, 4269};
        check(element(individual, "coordinates.c").visible(), "Renderer counts appear when enabled");
        coordinates.setOption(Coordinates.SHOW_C, false);
        check(!element(individual, "coordinates.c").visible(), "Renderer counts hide when disabled");
        Set<String> layouts = new HashSet<String>();
        for (HudMod element : individual) {
            layouts.add(element.defaultX() + "/" + element.defaultY());
        }
        check(layouts.size() == 6, "Elements keep separate default layouts");
        coordinates.setOption(Coordinates.DECIMAL, true);
        TestCanvas canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.x").render(canvas);
        check(canvas.texts.contains("[X: 12.7]"), "Individual elements bracket their label and value");
        check(canvas.roundedColors.contains(0x70000000), "Individual elements draw their background");
        coordinates.setOption(Coordinates.LABEL_X, false);
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.x").render(canvas);
        check(canvas.texts.contains("[12.7]"), "Individual elements honour their label toggle");
        coordinates.setOption(Coordinates.LABEL_X, true);
        backend.yaw = 90F;
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.direction").render(canvas);
        check(canvas.texts.contains("[Direction: W]") && canvas.texts.contains("-"),
                "Individual direction brackets the cardinal and signs the X axis");
        backend.yaw = 0F;
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.direction").render(canvas);
        check(canvas.texts.contains("[Direction: S]") && canvas.texts.contains("+"),
                "Individual direction signs the Z axis");
        backend.counts = null;
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.c").render(canvas);
        check(canvas.texts.contains("[C: --]"), "Individual renderer counts mark an unavailable counter");
        coordinates.setOption(Coordinates.SHOW_C, true);
        backend.counts = new int[] {92, 4269};
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.c").render(canvas);
        check(canvas.texts.contains("[C: 92/4269]"), "Individual renderer counts show both figures");
        coordinates.setOption(Coordinates.SHOW_BIOME, true);
        backend.biome = "Plains";
        coordinates.tick();
        canvas = new TestCanvas(320, 240);
        element(individual, "coordinates.biome").render(canvas);
        check(canvas.texts.contains("[Biome: Plains]"), "Individual biome shows the cached name");
        coordinates.setOption(Coordinates.DECIMAL, false);
        coordinates.preview(true);
        coordinates.elements();
        canvas = new TestCanvas(320, 240);
        element(coordinates.elements(), "coordinates.x").render(canvas);
        check(canvas.texts.contains("[X: 123]"), "Individual elements share the preview snapshot");
        coordinates.preview(false);
    }

    private static void reducedDebug(Path root) {
        Backend backend = new Backend();
        double[][] position = {{12.7, 64.2, -8.1}};
        Coordinates coordinates = create(root, "reduced", backend, position);
        backend.pos = position[0];
        backend.yaw = 180F;
        backend.biome = "Plains";
        backend.counts = new int[] {92, 4269};
        backend.reduced = true;
        coordinates.setOption(Coordinates.SHOW_C, true);
        coordinates.setOption(Coordinates.SHOW_BIOME, true);
        coordinates.setOption(Coordinates.SHOW_DIRECTION, true);
        coordinates.tick();
        TestCanvas canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("C: ") && canvas.texts.contains("92/4269"),
                "Renderer counts survive reduced debug");
        check(!canvas.texts.contains("X: ") && !canvas.texts.contains("Y: ") && !canvas.texts.contains("Z: "),
                "Reduced debug suppresses the coordinate rows");
        check(!canvas.texts.contains("Biome: ") && !canvas.texts.contains("Direction: N"),
                "Reduced debug suppresses the biome and direction rows");
        check(coordinates.visible(), "A counts-only HUD stays visible under reduced debug");
        coordinates.setOption(Coordinates.SHOW_C, false);
        check(!coordinates.visible(), "A fully suppressed HUD hides under reduced debug");
        coordinates.setOption(Coordinates.SHOW_C, true);
        coordinates.preview(true);
        canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("X: ") && canvas.texts.contains("Direction: N") && canvas.texts.contains("Biome: "),
                "Preview bypasses reduced debug");
        coordinates.preview(false);
        coordinates.setOption(Coordinates.MOVE_CHILDREN, true);
        List<HudMod> individual = coordinates.elements();
        check(!element(individual, "coordinates.x").visible() && !element(individual, "coordinates.biome").visible(),
                "Reduced debug suppresses individual elements too");
        check(!element(individual, "coordinates.direction").visible(), "Reduced debug suppresses individual direction");
        check(element(individual, "coordinates.c").visible(), "Individual renderer counts survive reduced debug");
        coordinates.setOption(Coordinates.MOVE_CHILDREN, false);
    }

    private static void typing(Path root) {
        Backend backend = new Backend();
        double[][] position = {{1, 64, 2}};
        Coordinates coordinates = create(root, "typing", backend, position);
        backend.pos = position[0];
        check(coordinates.option(Coordinates.SHOW_WHILE_TYPING), "The HUD shows while typing by default");
        check(coordinates.visible(), "The HUD is visible with the chat closed");
        backend.chat = true;
        check(coordinates.visible(), "The default HUD stays visible with the chat open");
        coordinates.setOption(Coordinates.SHOW_WHILE_TYPING, false);
        check(!coordinates.visible(), "The HUD hides while the player types");
        coordinates.setOption(Coordinates.MOVE_CHILDREN, true);
        List<HudMod> individual = coordinates.elements();
        check(!element(individual, "coordinates.x").visible(), "Individual elements hide while typing");
        coordinates.setOption(Coordinates.SHOW_WHILE_TYPING, true);
        check(element(individual, "coordinates.x").visible(), "Individual elements honour show-while-typing");
        coordinates.setOption(Coordinates.MOVE_CHILDREN, false);
        backend.chat = false;
        check(coordinates.visible(), "The HUD returns when the chat closes");
    }

    private static void clipboard(Path root) {
        Backend backend = new Backend();
        double[][] position = {{12.7, 64.2, -8.1}};
        Coordinates coordinates = create(root, "clipboard", backend, position);
        backend.pos = position[0];
        coordinates.setOption(Coordinates.DECIMAL, true);
        coordinates.setNumber(Coordinates.COPY, 42);
        check(coordinates.number(Coordinates.COPY) == 42, "The copy keybind is stored");
        coordinates.keyState(42, true);
        check("X: 12.7 Y: 64.2 Z: -8.1".equals(backend.copied),
                "Copy uses exactly the displayed coordinates");
        check("Copied coordinates to clipboard!".equals(backend.message), "Copy notifies through the host");
        backend.copied = null;
        coordinates.keyState(41, true);
        check(backend.copied == null, "Unbound keys never copy");
        coordinates.keyState(42, false);
        check(backend.copied == null, "Releasing the key never copies");
        coordinates.setOption(Coordinates.DECIMAL, false);
        coordinates.keyState(42, true);
        check("X: 12 Y: 64 Z: -9".equals(backend.copied), "Copy follows the integer display");
        backend.copied = null;
        backend.reduced = true;
        coordinates.keyState(42, true);
        check(backend.copied == null, "Clipboard respects reduced debug");
        backend.reduced = false;
        backend.pos = null;
        position[0] = null;
        coordinates.keyState(42, true);
        check(backend.copied == null, "Copy does nothing without a player");
        check(coordinates.labels()[0].equals("X: --"), "Missing player coordinates stay explicit");
        backend.copied = null;
        coordinates.setNumber(Coordinates.COPY, 0);
        backend.pos = new double[] {1, 64, 2};
        position[0] = backend.pos;
        coordinates.keyState(0, true);
        check(backend.copied == null, "An unbound keybind never copies");
    }

    private static void lifecycle(Path root) {
        Backend backend = new Backend();
        double[][] position = {{1, 64, 2}};
        Coordinates coordinates = create(root, "lifecycle", backend, position);
        backend.pos = position[0];
        backend.biome = "Plains";
        coordinates.tick();
        check(coordinates.biomeName().equals("Plains"), "The biome is cached while a player exists");
        backend.biome = null;
        coordinates.tick();
        check(coordinates.biomeName().equals("Plains"), "An unavailable biome keeps the previous cache");
        backend.pos = null;
        position[0] = null;
        coordinates.tick();
        check(coordinates.biomeName().equals("Plains"), "A missing player keeps the previous cache");
        coordinates.reset();
        check(coordinates.biomeName() == null, "Leaving the world clears the biome cache");
        backend.pos = new double[] {1, 64, 2};
        position[0] = backend.pos;
        backend.biome = null;
        coordinates.tick();
        check(coordinates.biomeName() == null, "A cleared cache stays empty without a biome");
        coordinates.setOption(Coordinates.SHOW_BIOME, true);
        coordinates.preview(true);
        TestCanvas canvas = new TestCanvas(320, 240);
        coordinates.render(canvas);
        check(canvas.texts.contains("Plains"), "Preview supplies a biome without a world");
        coordinates.preview(false);
    }

    private static final class Backend implements CoordinateBackend {
        private double[] pos;
        private float yaw = Float.NaN;
        private int[] counts;
        private String biome;
        private boolean reduced;
        private boolean chat;
        private String copied;
        private String message;

        @Override
        public double[] position() {
            return pos;
        }

        @Override
        public float cameraYaw() {
            return yaw;
        }

        @Override
        public float playerYaw() {
            return yaw;
        }

        @Override
        public int[] renderCounts() {
            return counts;
        }

        @Override
        public String biomeName() {
            return biome;
        }

        @Override
        public boolean reducedDebugInfo() {
            return reduced;
        }

        @Override
        public boolean chatOpen() {
            return chat;
        }

        @Override
        public void copy(String text) {
            copied = text;
        }

        @Override
        public void notify(String value) {
            message = value;
        }
    }
}
