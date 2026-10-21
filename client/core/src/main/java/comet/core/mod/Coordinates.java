package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.CoordinateBackend;
import comet.core.bridge.GameHost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Coordinates extends HudMod {
    private static final int PADDING = 3;
    private static final int ROW_GAP = 2;
    private static final int DIRECTION_WIDTH = 20;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final double[] PREVIEW_POSITION = {123.0, 64.0, -456.0};
    private static final int[] PREVIEW_COUNTS = {92, 4269};
    private static final float PREVIEW_YAW = 180.0F;
    private static final String PREVIEW_BIOME = "Plains";
    private static final Slot[] ROWS = {Slot.X, Slot.Y, Slot.Z, Slot.C, Slot.BIOME};
    private static final Slot[] SIGNED_AXES = {Slot.X, Slot.Z};

    public static final Option SHOW_WHILE_TYPING = new Option("showWhileTyping", "Show while typing", true);
    public static final Option MOVE_CHILDREN = new Option("moveChildrenIndividually", "Move elements individually", false);
    public static final Option DECIMAL = new Option("decimalCoordinates", "Decimal coordinates", false);
    public static final NumberOption MODE = new NumberOption("mode", "Layout", 0, 0, 1, "", "Vertical", "Horizontal");
    public static final NumberOption COPY = NumberOption.keybind("copyCoords", "Copy coordinates");
    public static final Option SHOW_X = new Option("showX", "Show X", true);
    public static final Option SHOW_Y = new Option("showY", "Show Y", true);
    public static final Option SHOW_Z = new Option("showZ", "Show Z", true);
    public static final Option SHOW_C = new Option("showC", "Show renderer counts", false);
    public static final Option SHOW_DIRECTION = new Option("showDirection", "Show direction", false);
    public static final Option SHOW_BIOME = new Option("showBiome", "Show biome", false);
    public static final Option LABEL_X = new Option("labelX", "X label", true);
    public static final Option LABEL_Y = new Option("labelY", "Y label", true);
    public static final Option LABEL_Z = new Option("labelZ", "Z label", true);
    public static final Option LABEL_C = new Option("labelC", "Renderer count label", true);
    public static final Option LABEL_DIRECTION = new Option("labelDirection", "Direction label", true);
    public static final Option LABEL_BIOME = new Option("labelBiome", "Biome label", true);

    private final GameHost host;
    private final List<Element> elements;
    private String cachedBiome;

    public Coordinates(GameHost host) {
        this.host = host;
        List<Element> created = new ArrayList<Element>();
        for (Slot slot : Slot.values()) {
            created.add(new Element(slot));
        }
        this.elements = Collections.unmodifiableList(created);
    }

    @Override
    public String id() {
        return "coordinates";
    }

    @Override
    public String name() {
        return "Coordinates";
    }

    @Override
    public String description() {
        return "Shows your current block coordinates.";
    }

    @Override
    public float defaultY() {
        return 0.3F;
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(SHOW_WHILE_TYPING, MOVE_CHILDREN, DECIMAL, SHOW_X, SHOW_Y, SHOW_Z, SHOW_C, SHOW_DIRECTION,
                SHOW_BIOME, LABEL_X, LABEL_Y, LABEL_Z, LABEL_C, LABEL_DIRECTION, LABEL_BIOME);
    }

    @Override
    public List<NumberOption> numbers() {
        return Arrays.asList(MODE, COPY);
    }

    @Override
    public List<HudMod> elements() {
        if (!option(MOVE_CHILDREN)) {
            return Collections.singletonList(this);
        }
        List<HudMod> result = new ArrayList<HudMod>();
        for (Element element : elements) {
            result.add(bindElement(element));
            element.preview(preview);
        }
        return result;
    }

    @Override
    public boolean visible() {
        if (option(MOVE_CHILDREN)) {
            return false;
        }
        Snapshot snapshot = snapshot();
        if (!option(SHOW_WHILE_TYPING) && snapshot.chatOpen) {
            return false;
        }
        for (Slot slot : Slot.values()) {
            if (shows(snapshot, slot)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void preview(boolean enabled) {
        super.preview(enabled);
        for (Element element : elements) {
            element.preview(enabled);
        }
    }

    @Override
    public void keyState(int key, boolean pressed) {
        if (pressed && key != 0 && key == number(COPY)) {
            copy();
        }
    }

    @Override
    public void tick() {
        CoordinateBackend backend = backend();
        if (backend.position() == null) {
            return;
        }
        String biome = backend.biomeName();
        if (biome != null) {
            cachedBiome = biome;
        }
    }

    @Override
    public void reset() {
        cachedBiome = null;
    }

    public String biomeName() {
        return cachedBiome;
    }

    public String[] labels() {
        Snapshot snapshot = snapshot();
        String[] labels = new String[3];
        Slot[] axes = {Slot.X, Slot.Y, Slot.Z};
        for (int axis = 0; axis < axes.length; axis++) {
            labels[axis] = label(axes[axis]) + value(snapshot, axes[axis]);
        }
        return labels;
    }

    public static String format(double value, boolean decimal) {
        if (!Double.isFinite(value)) return "--";
        String text = BigDecimal.valueOf(value).setScale(decimal ? 2 : 0, decimal ? RoundingMode.DOWN : RoundingMode.FLOOR)
                .stripTrailingZeros().toPlainString();
        return decimal && text.indexOf('.') < 0 ? text + ".0" : text;
    }

    public static String cardinal(float yaw) {
        double degrees = normalize(yaw) + 180.0 + 22.5;
        degrees %= 360.0;
        degrees /= 45.0;
        int index = (int) degrees;
        if (degrees < index) {
            index -= 1;
        }
        return CARDINALS[Math.max(0, Math.min(CARDINALS.length - 1, index))];
    }

    public static String sign(String cardinal, boolean xAxis) {
        if (xAxis) {
            return cardinal.contains("W") ? "-" : cardinal.contains("E") ? "+" : "";
        }
        return cardinal.contains("N") ? "-" : cardinal.contains("S") ? "+" : "";
    }

    public static boolean suppressed(boolean reducedDebug, boolean preview) {
        return reducedDebug && !preview;
    }

    private static float normalize(float yaw) {
        yaw %= 360.0F;
        if (yaw >= 180.0F) {
            yaw -= 360.0F;
        }
        if (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    private void copy() {
        if (backend().reducedDebugInfo()) return;
        double[] position = position();
        if (position == null) {
            return;
        }
        boolean decimal = option(DECIMAL);
        CoordinateBackend backend = backend();
        backend.copy("X: " + format(position[0], decimal) + " Y: " + format(position[1], decimal) + " Z: "
                + format(position[2], decimal));
        backend.notify("Copied coordinates to clipboard!");
    }

    private double[] position() {
        Snapshot snapshot = snapshot();
        return snapshot.position;
    }

    private CoordinateBackend backend() {
        CoordinateBackend backend = host.coordinates();
        return backend == null ? CoordinateBackend.NONE : backend;
    }

    private Snapshot snapshot() {
        if (preview) {
            return new Snapshot(PREVIEW_POSITION.clone(), PREVIEW_YAW, PREVIEW_BIOME, false, false);
        }
        CoordinateBackend backend = backend();
        double[] position = backend.position();
        if (position == null) {
            position = host.position();
        }
        float yaw = backend.cameraYaw();
        if (Float.isNaN(yaw)) {
            yaw = backend.playerYaw();
        }
        return new Snapshot(position, yaw, cachedBiome, backend.reducedDebugInfo(), backend.chatOpen());
    }

    private String counts() {
        int[] counts = preview ? PREVIEW_COUNTS : backend().renderCounts();
        return counts == null ? "--" : counts[0] + "/" + counts[1];
    }

    private boolean shows(Snapshot snapshot, Slot slot) {
        if (!option(show(slot))) {
            return false;
        }
        if (slot == Slot.DIRECTION && Float.isNaN(snapshot.yaw)) {
            return false;
        }
        return slot == Slot.C || !suppressed(snapshot.reducedDebug, preview);
    }

    private String label(Slot slot) {
        return option(labelOption(slot)) ? slot.tag + ": " : "";
    }

    private String value(Snapshot snapshot, Slot slot) {
        switch (slot) {
            case X:
                return axis(snapshot, 0);
            case Y:
                return axis(snapshot, 1);
            case Z:
                return axis(snapshot, 2);
            case C:
                return counts();
            case DIRECTION:
                return cardinal(snapshot.yaw);
            default:
                return snapshot.biome == null ? "--" : snapshot.biome;
        }
    }

    private String axis(Snapshot snapshot, int index) {
        return snapshot.position == null ? "--" : format(snapshot.position[index], option(DECIMAL));
    }

    private Layout build(Canvas canvas) {
        Snapshot snapshot = snapshot();
        return number(MODE) == 1 ? horizontal(canvas, snapshot) : vertical(canvas, snapshot);
    }

    @Override
    public int width(Canvas canvas) {
        return build(canvas).width;
    }

    @Override
    public int height(Canvas canvas) {
        return build(canvas).height;
    }

    @Override
    public void render(Canvas canvas) {
        Layout layout = build(canvas);
        background(canvas, layout.width, layout.height);
        draw(canvas, layout, global(GlobalSetting.TEXT_SHADOW));
    }

    private void draw(Canvas canvas, Layout layout, boolean shadow) {
        for (Draw text : layout.texts) {
            canvas.text(text.value, text.x, text.y, text.color, shadow);
        }
    }

    private Layout vertical(Canvas canvas, Snapshot snapshot) {
        int fontHeight = canvas.textHeight();
        List<Slot> rows = new ArrayList<Slot>();
        for (Slot slot : ROWS) {
            if (shows(snapshot, slot)) {
                rows.add(slot);
            }
        }
        List<Draw> texts = new ArrayList<Draw>();
        int[] rowY = new int[rows.size()];
        int rowWidth = 0;
        int y = PADDING;
        for (int index = 0; index < rows.size(); index++) {
            Slot slot = rows.get(index);
            String label = label(slot);
            String value = value(snapshot, slot);
            int x = PADDING;
            if (!label.isEmpty()) {
                texts.add(new Draw(label, x, y, TEXT_COLOR));
                x += canvas.textWidth(label);
            }
            texts.add(new Draw(value, x, y, TEXT_COLOR));
            rowWidth = Math.max(rowWidth, x + canvas.textWidth(value) - PADDING);
            rowY[index] = y;
            y += fontHeight + ROW_GAP;
        }
        int width = rows.isEmpty() ? 0 : rowWidth + PADDING * 2;
        int height = rows.isEmpty() ? 0 : y - ROW_GAP + PADDING;
        if (shows(snapshot, Slot.DIRECTION)) {
            String cardinal = cardinal(snapshot.yaw);
            String direction = label(Slot.DIRECTION) + cardinal;
            if (rows.isEmpty()) {
                width = canvas.textWidth(direction) + PADDING * 2;
                height = fontHeight + PADDING * 2;
                texts.add(new Draw(direction, PADDING, PADDING, TEXT_COLOR));
            } else {
                int cardinalWidth = canvas.textWidth(direction);
                int columnWidth = Math.max(DIRECTION_WIDTH, cardinalWidth + PADDING * 2);
                texts.add(new Draw(direction, PADDING + rowWidth + (columnWidth - cardinalWidth) / 2,
                        directionRow(rows, rowY), TEXT_COLOR));
                width = rowWidth + columnWidth + PADDING * 2;
                for (Slot axis : SIGNED_AXES) {
                    int index = rows.indexOf(axis);
                    if (index < 0) {
                        continue;
                    }
                    String mark = sign(cardinal, axis == Slot.X);
                    if (!mark.isEmpty()) {
                        texts.add(new Draw(mark, PADDING + rowWidth + (columnWidth - canvas.textWidth(mark)) / 2, rowY[index], TEXT_COLOR));
                    }
                }
            }
        }
        return new Layout(width, height, texts);
    }

    private int directionRow(List<Slot> rows, int[] rowY) {
        int index = rows.indexOf(Slot.Y);
        if (index >= 0) {
            return rowY[index];
        }
        int x = rows.indexOf(Slot.X);
        int z = rows.indexOf(Slot.Z);
        if (x >= 0 && z >= 0) {
            return rowY[x] + (rowY[z] - rowY[x]) / 2;
        }
        if (x < 0 && z < 0) {
            int c = rows.indexOf(Slot.C);
            if (c >= 0) {
                return rowY[c];
            }
            int biome = rows.indexOf(Slot.BIOME);
            return biome >= 0 ? rowY[biome] : PADDING;
        }
        return x >= 0 ? rowY[x] : rowY[z];
    }

    private Layout horizontal(Canvas canvas, Snapshot snapshot) {
        boolean direction = shows(snapshot, Slot.DIRECTION);
        String cardinal = direction ? cardinal(snapshot.yaw) : "";
        StringBuilder text = new StringBuilder();
        for (Slot slot : ROWS) {
            if (!shows(snapshot, slot)) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(label(slot)).append(value(snapshot, slot));
            if (direction && (slot == Slot.X || slot == Slot.Z)) {
                String mark = sign(cardinal, slot == Slot.X);
                if (!mark.isEmpty()) {
                    text.append(" (").append(mark).append(')');
                }
            }
        }
        if (direction) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(label(Slot.DIRECTION)).append(cardinal);
        }
        if (text.length() == 0) {
            return new Layout(0, 0, Collections.<Draw>emptyList());
        }
        String value = text.toString();
        return new Layout(canvas.textWidth(value) + PADDING * 2, canvas.textHeight() + PADDING * 2,
                Collections.singletonList(new Draw(value, PADDING, PADDING, TEXT_COLOR)));
    }

    private Option show(Slot slot) {
        switch (slot) {
            case X:
                return SHOW_X;
            case Y:
                return SHOW_Y;
            case Z:
                return SHOW_Z;
            case C:
                return SHOW_C;
            case DIRECTION:
                return SHOW_DIRECTION;
            default:
                return SHOW_BIOME;
        }
    }

    private Option labelOption(Slot slot) {
        switch (slot) {
            case X:
                return LABEL_X;
            case Y:
                return LABEL_Y;
            case Z:
                return LABEL_Z;
            case C:
                return LABEL_C;
            case DIRECTION:
                return LABEL_DIRECTION;
            default:
                return LABEL_BIOME;
        }
    }

    private enum Slot {
        X("x", "X", 0.30F),
        Y("y", "Y", 0.34F),
        Z("z", "Z", 0.38F),
        C("c", "C", 0.42F),
        DIRECTION("direction", "Direction", 0.46F),
        BIOME("biome", "Biome", 0.50F);

        private final String suffix;
        private final String tag;
        private final float defaultY;

        Slot(String suffix, String tag, float defaultY) {
            this.suffix = suffix;
            this.tag = tag;
            this.defaultY = defaultY;
        }
    }

    private static final class Snapshot {
        private final double[] position;
        private final float yaw;
        private final String biome;
        private final boolean reducedDebug;
        private final boolean chatOpen;

        private Snapshot(double[] position, float yaw, String biome, boolean reducedDebug,
                boolean chatOpen) {
            this.position = position;
            this.yaw = yaw;
            this.biome = biome;
            this.reducedDebug = reducedDebug;
            this.chatOpen = chatOpen;
        }
    }

    private static final class Draw {
        private final String value;
        private final int x;
        private final int y;
        private final int color;

        private Draw(String value, int x, int y, int color) {
            this.value = value;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private static final class Layout {
        private final int width;
        private final int height;
        private final List<Draw> texts;

        private Layout(int width, int height, List<Draw> texts) {
            this.width = width;
            this.height = height;
            this.texts = texts;
        }
    }

    private final class Element extends HudMod {
        private final Slot slot;

        private Element(Slot slot) {
            this.slot = slot;
        }

        @Override
        public String id() {
            return "coordinates." + slot.suffix;
        }

        @Override
        public String name() {
            return "Coordinates " + slot.tag;
        }

        @Override
        public String description() {
            return "Moves the " + slot.tag + " value of the coordinates HUD on its own.";
        }

        @Override
        public boolean enabledByDefault() {
            return true;
        }

        @Override
        public float defaultX() {
            return 0.01F;
        }

        @Override
        public float defaultY() {
            return slot.defaultY;
        }

        @Override
        public boolean visible() {
            if (!Coordinates.this.option(MOVE_CHILDREN)) {
                return false;
            }
            Snapshot snapshot = snapshot();
            if (!shows(snapshot, slot)) {
                return false;
            }
            return Coordinates.this.option(SHOW_WHILE_TYPING) || !snapshot.chatOpen;
        }

        @Override
        public int width(Canvas canvas) {
            return layout(canvas).width;
        }

        @Override
        public int height(Canvas canvas) {
            return layout(canvas).height;
        }

        @Override
        public void render(Canvas canvas) {
            Layout layout = layout(canvas);
            Coordinates.this.background(canvas, layout.width, layout.height);
            draw(canvas, layout, Coordinates.this.global(GlobalSetting.TEXT_SHADOW));
        }

        private Layout layout(Canvas canvas) {
            Snapshot snapshot = snapshot();
            String value = value(snapshot, slot);
            if (slot != Slot.DIRECTION) {
                String text = "[" + label(slot) + value + "]";
                return new Layout(canvas.textWidth(text) + PADDING * 2, canvas.textHeight() + PADDING * 2,
                        Collections.singletonList(new Draw(text, PADDING, PADDING, TEXT_COLOR)));
            }
            String cardinal = value;
            String above = sign(cardinal, true);
            String below = sign(cardinal, false);
            String text = "[" + label(slot) + cardinal + "]";
            int width = Math.max(canvas.textWidth(text),
                    Math.max(canvas.textWidth(above), canvas.textWidth(below))) + PADDING * 2;
            int height = canvas.textHeight() * 3 + ROW_GAP * 2 + PADDING * 2;
            int middle = PADDING + canvas.textHeight() + ROW_GAP;
            List<Draw> texts = new ArrayList<Draw>();
            texts.add(new Draw(above, (width - canvas.textWidth(above)) / 2, PADDING, TEXT_COLOR));
            texts.add(new Draw(text, PADDING, middle, TEXT_COLOR));
            texts.add(new Draw(below, (width - canvas.textWidth(below)) / 2, middle + canvas.textHeight() + ROW_GAP,
                    TEXT_COLOR));
            return new Layout(width, height, texts);
        }
    }
}
