package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;

public final class FpsCounter extends HudMod {
    private static final int PADDING = 3;
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
        return "FPS counter";
    }

    @Override
    public String description() {
        return "Shows the current frame rate.";
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
        canvas.roundedFill(0, 0, width(canvas), height(canvas), 3, 0x70000000);
        canvas.text(label(), PADDING, PADDING, 0xFFFFFFFF, true);
    }

    private String label() {
        return host.fps() + " FPS";
    }
}
