package comet.core.ui;

import comet.core.bridge.Canvas;
import comet.core.text.Text;

public final class Widgets {
    public static final int RADIUS = 8;
    public static final int FILL = 0xCC141414;
    public static final int HOVER = 0xE0202020;
    public static final int DISABLED = 0x80101010;
    public static final int BORDER = 0x30FFFFFF;
    public static final float LABEL = 9;

    private Widgets() {
    }

    public static void pill(Canvas canvas, int x, int y, int width, int height, int fill) {
        canvas.roundedFill(x - 1, y - 1, width + 2, height + 2, RADIUS + 1, BORDER);
        canvas.roundedFill(x, y, width, height, RADIUS, fill);
    }

    public static void button(Canvas canvas, int x, int y, int width, int height, String label, boolean hover, boolean enabled) {
        pill(canvas, x, y, width, height, !enabled ? DISABLED : hover ? HOVER : FILL);
        float size = Text.fit(canvas, label, LABEL, width - 8, 6);
        float textY = y + (height - Text.height(canvas, label, size)) / 2;
        Text.drawCentered(canvas, label, x + width / 2.0F, textY, size, enabled ? 0xFFFFFFFF : 0x60FFFFFF);
    }
}
