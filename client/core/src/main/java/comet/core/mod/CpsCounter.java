package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

public final class CpsCounter extends HudMod {
    static final int MIN_DIGITS = 2;
    private static final int PADDING = 3;
    private static final long WINDOW = 1000L;
    private static final Option LABEL = new Option("label", "Show CPS label", false);
    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();
    private final LongSupplier clock;

    public CpsCounter() {
        this(() -> System.nanoTime() / 1000000L);
    }

    CpsCounter(LongSupplier clock) {
        this.clock = clock;
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
        return Collections.singletonList(LABEL);
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
        if (!pressed) {
            return;
        }
        if (key == Keys.MOUSE_LEFT) {
            left.addLast(clock.getAsLong());
        } else if (key == Keys.MOUSE_RIGHT) {
            right.addLast(clock.getAsLong());
        }
        tick();
    }

    @Override
    public void tick() {
        count(left);
        count(right);
    }

    @Override
    public void reset() {
        left.clear();
        right.clear();
    }

    private int count(Deque<Long> clicks) {
        long cutoff = clock.getAsLong() - WINDOW;
        while (!clicks.isEmpty() && clicks.peekFirst() <= cutoff) {
            clicks.removeFirst();
        }
        return clicks.size();
    }

    @Override
    public int width(Canvas canvas) {
        return layout(canvas).width + PADDING * 2;
    }

    @Override
    public int horizontalExpansion(Canvas canvas) {
        Layout current = layout(canvas);
        return (current.width - layout(canvas, 0, 0, current.prefix).width) / 2;
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        Layout layout = layout(canvas);
        canvas.roundedFill(0, 0, layout.width + PADDING * 2, height(canvas), 3, 0x70000000);
        int x = PADDING;
        if (!layout.prefix.isEmpty()) {
            canvas.text(layout.prefix, x, PADDING, 0xFFFFFFFF, true);
            x += canvas.textWidth(layout.prefix);
        }
        canvas.text("[ ", x, PADDING, 0xFFFFFFFF, true);
        x += canvas.textWidth("[ ");
        canvas.text(layout.left, x + layout.slot - canvas.textWidth(layout.left), PADDING, 0xFFFFFFFF, true);
        x += layout.slot;
        canvas.text(" | ", x, PADDING, 0xFFFFFFFF, true);
        x += canvas.textWidth(" | ");
        canvas.text(layout.right, x, PADDING, 0xFFFFFFFF, true);
        x += layout.slot;
        canvas.text(" ]", x, PADDING, 0xFFFFFFFF, true);
    }

    private Layout layout(Canvas canvas) {
        return layout(canvas, count(left), count(right), option(LABEL) ? "CPS " : "");
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
