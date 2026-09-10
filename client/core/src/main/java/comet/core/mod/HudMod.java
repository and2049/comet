package comet.core.mod;

import comet.core.bridge.Canvas;

public abstract class HudMod extends Mod {
    protected boolean preview;

    public abstract int width(Canvas canvas);

    public abstract int height(Canvas canvas);

    public abstract void render(Canvas canvas);

    protected void background(Canvas canvas, int width, int height) {
        if (global(GlobalSetting.HUD_BACKGROUND)) {
            canvas.roundedFill(0, 0, width, height, 3, 0x70000000);
        }
    }

    protected void text(Canvas canvas, String text, int x, int y) {
        canvas.text(text, x, y, 0xFFFFFFFF, global(GlobalSetting.TEXT_SHADOW));
    }

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
