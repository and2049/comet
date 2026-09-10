package comet.core.ui;

import comet.core.bridge.Canvas;
import comet.core.bridge.Images;
import java.awt.image.BufferedImage;

public final class Brand {
    private static float ratio;

    private Brand() {
    }

    private static float ratio() {
        if (ratio == 0) {
            BufferedImage image = Images.read("comet", "wordmark.png");
            ratio = image == null ? 0.25F : image.getHeight() / (float) image.getWidth();
        }
        return ratio;
    }

    public static int height(int width) {
        return Math.round(width * ratio());
    }

    public static void wordmark(Canvas canvas, int centerX, int centerY, int width) {
        int height = height(width);
        canvas.image("comet", "wordmark.png", centerX - width / 2, centerY - height / 2, width, height);
    }
}
