package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.Control;
import comet.core.bridge.GameHost;
import java.util.Arrays;
import java.util.List;

public final class Keystrokes extends HudMod {
    public static final Option MOUSE = new Option("mouseButtons", "Mouse buttons", true);
    public static final Option JUMP = new Option("jumpKey", "Jump key", true);
    private final GameHost host;

    public Keystrokes(GameHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "keystrokes";
    }

    @Override
    public String name() {
        return "Keystrokes";
    }

    @Override
    public String description() {
        return "Shows physical movement and mouse inputs.";
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(MOUSE, JUMP);
    }

    @Override
    public float defaultX() {
        return 0.85F;
    }

    @Override
    public float defaultY() {
        return 0.02F;
    }

    @Override
    public int width(Canvas canvas) {
        return 70;
    }

    @Override
    public int height(Canvas canvas) {
        return 46 + (option(MOUSE) ? 24 : 0) + (option(JUMP) ? 18 : 0);
    }

    public boolean pressed(Control control) {
        int key = host.controlKey(control);
        return !preview && host.inWorld() && !host.screenOpen() && key != 0 && host.keyDown(key);
    }

    @Override
    public void render(Canvas canvas) {
        cell(canvas, Control.FORWARD, 24, 0, 22, 22);
        cell(canvas, Control.LEFT, 0, 24, 22, 22);
        cell(canvas, Control.BACK, 24, 24, 22, 22);
        cell(canvas, Control.RIGHT, 48, 24, 22, 22);
        int y = 48;
        if (option(MOUSE)) {
            cell(canvas, Control.ATTACK, 0, y, 34, 22);
            cell(canvas, Control.USE, 36, y, 34, 22);
            y += 24;
        }
        if (option(JUMP)) cell(canvas, Control.JUMP, 0, y, 70, 16);
    }

    private void cell(Canvas canvas, Control control, int x, int y, int width, int height) {
        canvas.push(x, y, 1);
        background(canvas, width, height);
        boolean down = pressed(control);
        if (down) canvas.roundedFill(0, 0, width, height, 3, 0x907CA85A);
        String label = host.keyName(host.controlKey(control));
        if (label == null || label.isEmpty()) label = "--";
        float scale = Math.min(1, (width - 4.0F) / Math.max(1, canvas.textWidth(label)));
        canvas.push(Math.round((width - canvas.textWidth(label) * scale) / 2), Math.round((height - canvas.textHeight() * scale) / 2), scale);
        text(canvas, label, 0, 0);
        canvas.pop();
        canvas.pop();
    }
}
