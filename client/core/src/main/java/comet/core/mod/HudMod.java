package comet.core.mod;

import comet.core.bridge.Canvas;

public abstract class HudMod extends Mod {
    public abstract int width(Canvas canvas);

    public abstract int height(Canvas canvas);

    public abstract void render(Canvas canvas);

    public float defaultX() {
        return 0.01F;
    }

    public float defaultY() {
        return 0.01F;
    }
}
