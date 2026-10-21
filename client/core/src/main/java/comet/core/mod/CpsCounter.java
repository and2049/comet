package comet.core.mod;

import comet.core.bridge.Canvas;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

public final class CpsCounter extends HudMod {
    static final int MIN_DIGITS = 2;
    private static final int PADDING = 3;
    public static final Option LABEL = new Option("label", "Show CPS label", false);
    public static final Option FILTER = new Option("ignoreUsingItem", "Ignore left clicks while using items", false);
    public static final Option BRACKETS = new Option("brackets", "Brackets", true);
    public static final NumberOption BUTTONS = new NumberOption("buttons", "Display", 0, 0, 2, "", "Both", "Left", "Right");
    public static final NumberOption ORDER = new NumberOption("labelOrder", "Label position", 0, 0, 1, "", "Before", "After");
    private final ClickStats stats;
    private final boolean shared;

    public CpsCounter() {
        this(() -> System.nanoTime() / 1000000L);
    }

    CpsCounter(LongSupplier clock) {
        this.stats = new ClickStats(clock);
        this.shared = false;
    }

    public CpsCounter(ClickStats stats) {
        this.stats = stats;
        this.shared = true;
    }

    @Override
    public String id() {
        return "cps";
    }

    @Override
    public String name() {
        return "CPS";
    }

    @Override
    public String description() {
        return "Shows left and right clicks per second.";
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(LABEL, FILTER, BRACKETS);
    }

    @Override
    public List<NumberOption> numbers() {
        return Arrays.asList(BUTTONS, ORDER);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public float defaultY() {
        return 0.07F;
    }

    @Override
    public void keyState(int key, boolean pressed) {
        if (!shared) stats.record(key, pressed, false);
    }

    @Override
    public void tick() {
        stats.prune();
    }

    @Override
    public void reset() {
        if (!shared) stats.reset();
    }

    @Override
    public int width(Canvas canvas) {
        return (number(BUTTONS) == 0 ? layout(canvas).width : canvas.textWidth(label())) + PADDING * 2;
    }

    @Override
    public int horizontalExpansion(Canvas canvas) {
        if (number(BUTTONS) != 0) return 0;
        Layout current = layout(canvas);
        return (current.width - layout(canvas, 0, 0, current.prefix).width
                - canvas.textWidth(suffix()) + (option(BRACKETS) ? 0 : canvas.textWidth("[  ]"))) / 2;
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        if (number(BUTTONS) != 0) {
            background(canvas, width(canvas), height(canvas));
            text(canvas, label(), PADDING, PADDING);
            return;
        }
        Layout layout = layout(canvas);
        background(canvas, layout.width + PADDING * 2, height(canvas));
        int x = PADDING;
        if (!layout.prefix.isEmpty()) {
            text(canvas, layout.prefix, x, PADDING);
            x += canvas.textWidth(layout.prefix);
        }
        if (option(BRACKETS)) {
            text(canvas, "[ ", x, PADDING);
            x += canvas.textWidth("[ ");
        }
        text(canvas, layout.left, x + layout.slot - canvas.textWidth(layout.left), PADDING);
        x += layout.slot;
        text(canvas, " | ", x, PADDING);
        x += canvas.textWidth(" | ");
        text(canvas, layout.right, x, PADDING);
        x += layout.slot;
        if (option(BRACKETS)) {
            text(canvas, " ]", x, PADDING);
            x += canvas.textWidth(" ]");
        }
        if (!suffix().isEmpty()) text(canvas, suffix(), x, PADDING);
    }

    private Layout layout(Canvas canvas) {
        Layout base = layout(canvas, stats.left(option(FILTER)), stats.right(), prefix());
        return new Layout(base.left, base.right, base.prefix, base.slot,
                base.width + canvas.textWidth(suffix()) - (option(BRACKETS) ? 0 : canvas.textWidth("[  ]")));
    }

    private String prefix() {
        return option(LABEL) && number(ORDER) == 0 ? "CPS " : "";
    }

    private String suffix() {
        return option(LABEL) && number(ORDER) == 1 ? " CPS" : "";
    }

    public String label() {
        String value = number(BUTTONS) == 1 ? Integer.toString(stats.left(option(FILTER))) : number(BUTTONS) == 2
                ? Integer.toString(stats.right()) : stats.left(option(FILTER)) + " | " + stats.right();
        return prefix() + (option(BRACKETS) ? "[ " + value + " ]" : value) + suffix();
    }

    static Layout layout(Canvas canvas, int leftClicks, int rightClicks, String prefix) {
        String left = Integer.toString(leftClicks);
        String right = Integer.toString(rightClicks);
        int digit = 0;
        for (char character = '0'; character <= '9'; character++) {
            digit = Math.max(digit, canvas.textWidth(Character.toString(character)));
        }
        int slot = digit * Math.max(MIN_DIGITS, Math.max(left.length(), right.length()));
        int width = canvas.textWidth(prefix) + canvas.textWidth("[ ") + slot + canvas.textWidth(" | ") + slot + canvas.textWidth(" ]");
        return new Layout(left, right, prefix, slot, width);
    }

    static final class Layout {
        final String left;
        final String right;
        final String prefix;
        final int slot;
        final int width;

        Layout(String left, String right, String prefix, int slot, int width) {
            this.left = left;
            this.right = right;
            this.prefix = prefix;
            this.slot = slot;
            this.width = width;
        }
    }
}
