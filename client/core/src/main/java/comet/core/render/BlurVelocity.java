package comet.core.render;

public final class BlurVelocity {
    private final float[] velocity = new float[16];
    private boolean valid;

    public void reset() {
        java.util.Arrays.fill(velocity, 0F);
        valid = false;
    }

    public float[] update(float[] transform, double frameMs, float shutterMs) {
        float[] result = identity();
        if (!Double.isFinite(frameMs) || frameMs <= 0 || frameMs > 100) {
            reset();
            return result;
        }
        for (float value : transform) {
            if (!Float.isFinite(value)) {
                reset();
                return result;
            }
        }
        float blend = valid ? (float) -Math.expm1(-frameMs / 20.0) : 1F;
        float normalization = transform[11] + transform[15];
        if (normalization < 0.5F) {
            reset();
            return result;
        }
        for (int i = 0; i < 16; i++) {
            float target = (transform[i] / normalization - result[i]) / (float) frameMs;
            velocity[i] += (target - velocity[i]) * blend;
            result[i] += velocity[i] * shutterMs;
        }
        valid = true;
        return result;
    }

    public static float[] identity() {
        float[] result = new float[16];
        result[0] = 1F;
        result[5] = 1F;
        result[11] = 1F;
        return result;
    }
}
