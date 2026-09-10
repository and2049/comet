package comet.core.mod;

import comet.core.bridge.Canvas;

public abstract class HudMod extends Mod {
    protected boolean preview;

    public abstract int width(Canvas canvas);

    public abstract int height(Canvas canvas);

    public abstract void render(Canvas canvas);

    public int horizontalExpansion(Canvas canvas) {
        return 0;
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    public void preview(boolean enabled) {
        preview = enabled;
    }

    public float defaultX() {
        return 0.01F;
    }

    public float defaultY() {
        return 0.01F;
    }
}
