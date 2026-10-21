package comet.core.anim;

public final class Glint {
    public static final int GUI_COLOR = 0xFF9078C8;
    public static final int WORLD_COLOR = 0xFF57327B;
    public static final long FIRST_PERIOD = 3000L;
    public static final long SECOND_PERIOD = 4873L;
    public static final float GUI_SCALE = 8.0F;
    public static final float WORLD_SCALE = 4.0F;
    public static final float FIRST_ANGLE = -50.0F;
    public static final float WORLD_SECOND_ANGLE = 10.0F;

    private Glint() {
    }

    public static float scale(boolean gui) {
        return gui ? GUI_SCALE : WORLD_SCALE;
    }

    public static int color(boolean gui) {
        return gui ? GUI_COLOR : WORLD_COLOR;
    }

    public static float firstScroll(long millis, boolean gui) {
        return (float) (millis % FIRST_PERIOD) / FIRST_PERIOD / scale(gui);
    }

    public static float secondScroll(long millis, boolean gui) {
        return (float) (millis % SECOND_PERIOD) / SECOND_PERIOD / scale(gui);
    }

    public static float secondAngle(boolean gui) {
        return gui ? FIRST_ANGLE : WORLD_SECOND_ANGLE;
    }

    public static float secondOffset(float scroll, boolean gui) {
        return gui ? scroll : -scroll;
    }
}
