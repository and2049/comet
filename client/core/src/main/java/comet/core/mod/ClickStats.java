package comet.core.mod;

import comet.core.bridge.Keys;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

public final class ClickStats {
    private final LongSupplier clock;
    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> filtered = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();
    private boolean leftDown;
    private boolean rightDown;

    public ClickStats() {
        this(() -> System.nanoTime() / 1000000L);
    }

    public ClickStats(LongSupplier clock) {
        this.clock = clock;
    }

    public void record(int key, boolean pressed, boolean usingItem) {
        if (key == Keys.MOUSE_LEFT) {
            if (pressed && !leftDown) {
                long now = clock.getAsLong();
                left.addLast(now);
                if (!usingItem) filtered.addLast(now);
            }
            leftDown = pressed;
        } else if (key == Keys.MOUSE_RIGHT) {
            if (pressed && !rightDown) right.addLast(clock.getAsLong());
            rightDown = pressed;
        }
        prune();
    }

    private void prune(Deque<Long> values, long cutoff) {
        while (!values.isEmpty() && values.peekFirst() <= cutoff) values.removeFirst();
    }

    public void prune() {
        long cutoff = clock.getAsLong() - 1000;
        prune(left, cutoff);
        prune(filtered, cutoff);
        prune(right, cutoff);
    }

    public int left(boolean ignoreUsingItem) {
        prune();
        return (ignoreUsingItem ? filtered : left).size();
    }

    public int right() {
        prune();
        return right.size();
    }

    public void reset() {
        left.clear();
        filtered.clear();
        right.clear();
        leftDown = false;
        rightDown = false;
    }
}
