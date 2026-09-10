package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;

public final class PingCounter extends HudMod {
    private final GameHost host;

    public PingCounter(GameHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "ping";
    }

    @Override
    public String name() {
        return "Ping";
    }

    @Override
    public String description() {
        return "Shows the server-reported latency.";
    }

    @Override
    public float defaultY() {
        return 0.5F;
    }

    public String label() {
        if (preview) return "42 ms";
        if (host.singleplayer()) return "Local";
        int ping = host.ping();
        return ping < 0 ? "-- ms" : ping + " ms";
    }
    @Override
    public int width(Canvas canvas) {
        return canvas.textWidth(label()) + 6;
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() + 6;
    }

    @Override
    public void render(Canvas canvas) {
        background(canvas, width(canvas), height(canvas));
        text(canvas, label(), 3, 3);
    }
}
