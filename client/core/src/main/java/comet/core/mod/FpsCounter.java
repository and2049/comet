package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import java.util.Collections;
import java.util.List;

public final class FpsCounter extends HudMod {
    private static final int PADDING = 3;
    public static final Option REVERSE = new Option("reverseOrder", "Label before value", false);
    private final GameHost host;

    public FpsCounter(GameHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "fps";
    }

    @Override
    public String name() {
        return "FPS";
    }

    @Override
    public String description() {
        return "Shows the current frame rate.";
    }

    @Override
    public String glyph() {
        return "FPS";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public int width(Canvas canvas) {
        return canvas.textWidth(label()) + PADDING * 2;
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        background(canvas, width(canvas), height(canvas));
        text(canvas, label(), PADDING, PADDING);
    }

    @Override
    public List<Option> options() {
        return Collections.singletonList(REVERSE);
    }

    public String label() {
        return option(REVERSE) ? "FPS: " + host.fps() : host.fps() + " FPS";
    }
}
