package comet.core.mod;

import comet.core.bridge.Canvas;

public final class HudLayout {
    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 3.0F;

    private HudLayout() {
    }

    public static final class Placement {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final float scale;
        public final boolean locked;

        Placement(int x, int y, int width, int height, float scale, boolean locked) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.locked = locked;
        }

        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static float clampScale(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static ModSettings.Layout defaults(HudMod mod) {
        ModSettings.Layout layout = new ModSettings.Layout();
        layout.x = mod.defaultX();
        layout.y = mod.defaultY();
        return layout;
    }

    public static Placement resolve(HudMod mod, ModSettings.Layout layout, Canvas canvas) {
        ModSettings.Layout current = layout == null ? defaults(mod) : layout;
        float scale = clampScale(current.scale);
        int width = Math.round(mod.width(canvas) * scale);
        int height = Math.round(mod.height(canvas) * scale);
        int expansion = Math.round(mod.horizontalExpansion(canvas) * scale);
        int x = clamp(Math.round(current.x * canvas.width()), canvas.width() - width + 2 * expansion) - expansion;
        int y = clamp(Math.round(current.y * canvas.height()), canvas.height() - height);
        return new Placement(x, y, width, height, scale, current.locked);
    }

    public static float fraction(int pixels, int extent, int screen) {
        return screen <= 0 ? 0 : clamp(pixels, screen - extent) / (float) screen;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }
}
