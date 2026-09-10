package comet.core.render;

import comet.core.mod.ModRegistry;
import comet.core.mod.ModSettings;
import comet.core.mod.MotionBlur;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

public final class BlurGpuTests {
    private static long clock = 1000000000L;

    public static void main(String[] args) throws Exception {
        Path parent = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "redsun");
        Files.createDirectories(parent);
        Path root = Files.createTempDirectory(parent, "comet-blur-gpu-");
        Pbuffer buffer = null;
        MotionBlurPass pass = new MotionBlurPass();
        try {
            buffer = new Pbuffer(128, 128, new PixelFormat(), null, null);
            buffer.makeCurrent();
            MotionBlur mod = new MotionBlur();
            new ModRegistry(new ModSettings(root.toFile())).register(mod);
            mod.setNumber(MotionBlur.STRENGTH, 10);
            frame(pass, mod, 1F, 16, 0);
            require(pixel(64) > 0.99F, "First frame preserves the source");
            frame(pass, mod, 0F, 4, 1);
            float edge = pixel(2);
            float centre = pixel(64);
            require(edge > 0.8F && edge < 0.99F, "Every-frame floating history blends instead of skipping frames");
            require(centre < edge - 0.04F, "History is reduced near the crosshair");
            for (int i = 0; i < 300; i++) frame(pass, mod, 0F, 1000.0 / 300, 1);
            require(pixel(2) < 1F / 255F, "History converges without permanent 8-bit ghosts");
            float slow = decay(pass, mod, 60);
            float fast = decay(pass, mod, 300);
            require(Math.abs(slow - fast) < 0.012F, "GPU history decay matches at 60 and 300 fps");
            pass.release();
            frame(pass, mod, 0F, 16, 0);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(31, 0, 2, 128);
            GL11.glClearColor(1F, 0F, 0F, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glRotatef(4F, 0F, 1F, 0F);
            clock += 16000000L;
            pass.capture(clock);
            pass.apply(mod);
            require(pixel(32) < 0.9F && pixel(29) > 0.01F, "Camera rotation spreads a sharp edge into a spatial streak");
            frame(pass, mod, 1F, 150, 0);
            require(pixel(2) > 0.99F, "A long stall discards stale history");
            GL11.glViewport(8, 8, 96, 96);
            frame(pass, mod, 0.5F, 16, 0);
            require(Math.abs(pixel(64) - 0.5F) < 0.01F, "Resize resets history and handles an offset viewport");
            System.out.println("Comet blur GPU: Hybrid, float decay, centre falloff, timing, resize and GL state passed");
        } finally {
            if (buffer != null) {
                pass.release();
                buffer.destroy();
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static float decay(MotionBlurPass pass, MotionBlur mod, int fps) {
        pass.release();
        frame(pass, mod, 1F, 16, 0);
        for (int i = 0; i < fps / 10; i++) frame(pass, mod, 0F, 1000.0 / fps, 0);
        return pixel(2);
    }

    private static void frame(MotionBlurPass pass, MotionBlur mod, float red, double ms, float yaw) {
        GL11.glClearColor(red, 0F, 0F, 1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glFrustum(-0.07, 0.07, -0.07, 0.07, 0.1, 100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotatef(yaw, 0F, 1F, 0F);
        clock += (long) (ms * 1e6);
        pass.capture(clock);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        pass.apply(mod);
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE1, "Active texture restored");
        require(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_TEXTURE, "Matrix mode restored");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "No GL errors");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static float pixel(int x) {
        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(x, 64, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
        return pixel.get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
