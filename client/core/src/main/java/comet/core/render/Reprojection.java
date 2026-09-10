package comet.core.render;

public final class Reprojection {
    private Reprojection() {
    }

    public static float[] matrix(float[] previousProjection, float[] previousModelview, float[] currentProjection, float[] currentModelview) {
        float[] scale = new float[16];
        scale[0] = 1F / currentProjection[0];
        scale[5] = 1F / currentProjection[5];
        scale[10] = -1F;
        scale[15] = 1F;
        return multiply(previousProjection, multiply(rotation(previousModelview, false), multiply(rotation(currentModelview, true), scale)));
    }

    public static float[] project(float[] matrix, float x, float y) {
        float[] q = new float[4];
        for (int row = 0; row < 4; row++) {
            q[row] = matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] + matrix[12 + row];
        }
        return new float[] {q[0] / q[3], q[1] / q[3]};
    }

    static float[] rotation(float[] modelview, boolean transpose) {
        float[] result = new float[16];
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                result[column * 4 + row] = transpose ? modelview[row * 4 + column] : modelview[column * 4 + row];
            }
        }
        result[15] = 1F;
        return result;
    }

    static float[] multiply(float[] a, float[] b) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0F;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                result[column * 4 + row] = sum;
            }
        }
        return result;
    }
}
