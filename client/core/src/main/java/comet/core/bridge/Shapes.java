package comet.core.bridge;

public final class Shapes {
    private Shapes() {
    }

    public static double[] roundedOutline(double x, double y, double width, double height, double radius, int segments) {
        double r = Math.min(radius, Math.min(width, height) / 2.0D);
        double[][] centers = {{x + r, y + height - r}, {x + width - r, y + height - r}, {x + width - r, y + r}, {x + r, y + r}};
        double[] points = new double[(4 * (segments + 1) + 1) * 2];
        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int step = 0; step <= segments; step++) {
                double angle = Math.toRadians(180 - corner * 90 - step * 90.0D / segments);
                points[index++] = centers[corner][0] + Math.cos(angle) * r;
                points[index++] = centers[corner][1] + Math.sin(angle) * r;
            }
        }
        points[index++] = points[0];
        points[index] = points[1];
        return points;
    }
}
