package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;

public final class Coordinates extends HudMod {
    private final GameHost host;

    public Coordinates(GameHost host) {
        this.host = host;
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

    public String[] labels() {
        double[] position = preview ? new double[] {123, 64, -456} : host.position();
        String[] labels = new String[3];
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            labels[axis] = axes[axis] + ": " + (position == null ? "--" : Long.toString((long) Math.floor(position[axis])));
        }
        return labels;
    }

    @Override
    public int width(Canvas canvas) {
        int width = 0;
        for (String label : labels()) width = Math.max(width, canvas.textWidth(label));
        return width + 6;
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() * 3 + 10;
    }

    @Override
    public void render(Canvas canvas) {
        background(canvas, width(canvas), height(canvas));
        int y = 3;
        for (String label : labels()) {
            text(canvas, label, 3, y);
            y += canvas.textHeight() + 2;
        }
    }
}
