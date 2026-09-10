package comet.core.bridge;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class Images {
    private Images() {
    }

    public static BufferedImage read(String namespace, String path) {
        try (InputStream in = Images.class.getResourceAsStream("/assets/" + namespace + "/" + path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException error) {
            return null;
        }
    }
}
