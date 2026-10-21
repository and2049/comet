package comet.core.render;

import comet.core.mod.ModRegistry;
import comet.core.mod.ModSettings;
import comet.core.mod.MotionBlur;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
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
            buffer = new Pbuffer(128, 128, new PixelFormat().withAlphaBits(8), null, null);
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
            GL11.glViewport(0, 0, 128, 128);
            temporal(pass, mod);
            int target = EXTFramebufferObject.glGenFramebuffersEXT();
            int texture = GL11.glGenTextures();
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 128, 128, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, target);
                EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, texture, 0);
                GL11.glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
                GL11.glReadBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
                require(EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                        == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT, "RGBA8 test target is complete");
                temporal(pass, mod);
            } finally {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                EXTFramebufferObject.glDeleteFramebuffersEXT(target);
                GL11.glDeleteTextures(texture);
            }
            System.out.println("Comet blur GPU: all four methods, all strengths, RGBA8 feedback, timing, transitions, patterns, FBO/default targets and GL state passed");
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

    private static void temporal(MotionBlurPass pass, MotionBlur mod) throws Exception {
        float[][] retention = {
                {0.72F, 0.75F, 0.78F, 0.81F, 0.84F, 0.87F, 0.90F, 0.93F, 0.96F, 0.99F},
                {0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 0.99F},
                {0.095F, 0.190F, 0.285F, 0.380F, 0.475F, 0.570F, 0.665F, 0.675F, 0.675F, 0.675F}
        };
        for (int method = MotionBlur.DECAYING_MAX; method <= MotionBlur.CAPPED_BLEND; method++) {
            mod.setNumber(MotionBlur.ALGORITHM, method);
            for (int strength = 1; strength <= 10; strength++) {
                mod.setNumber(MotionBlur.STRENGTH, strength);
                pass.release();
                frame(pass, mod, 1F, 16, 0);
                close(pixel(64), 1F, "Initial activation preserves RGB");
                float factor = retention[method - MotionBlur.DECAYING_MAX][strength - 1];
                for (int i = 0; i < 64; i++) {
                    float previous = pixel(64);
                    frame(pass, mod, 0F, i == 1 ? 500 : 1000.0 / 300, i * 3);
                    close(pixel(64), quantize(previous * factor), "Processed RGBA8 feedback, including stalls and rotation");
                    close(pixel(2), pixel(64), "Temporal methods have no radial falloff");
                    close(rgba(64, 64)[3], 1F, "Output alpha remains opaque");
                }
                pass.release();
                frame(pass, mod, 0F, 16, 0);
                frame(pass, mod, 1F, 16, 0);
                close(pixel(64), method == MotionBlur.DECAYING_MAX ? 1F : quantize(1F - factor), "Dark-to-bright response");
            }
            mod.setNumber(MotionBlur.STRENGTH, 5);
            pass.release();
            frame(pass, mod, 1F, 16, 0);
            GL11.glClearColor(0F, 1F, 0F, 0.2F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            pass.apply(mod);
            float[] mixed = rgba(64, 64);
            float factor = retention[method - MotionBlur.DECAYING_MAX][4];
            close(mixed[0], quantize(factor), "Red channel retains history without a camera capture");
            close(mixed[1], method == MotionBlur.DECAYING_MAX ? 1F : quantize(1F - factor), "Green channel uses current input");
            close(mixed[2], 0F, "Channels do not bleed");
            close(mixed[3], 1F, "Transparent current input produces opaque output");
            pass.release();
            frame(pass, mod, 1F, 16, 0);
            frame(pass, mod, 0F, 1000.0 / 60, 0);
            frame(pass, mod, 0F, 1000.0 / 60, 0);
            float slow = pixel(64);
            pass.release();
            frame(pass, mod, 1F, 16, 0);
            frame(pass, mod, 0F, 1000.0 / 300, 0);
            frame(pass, mod, 0F, 1000, 0);
            close(pixel(64), slow, "Same frame sequence is independent of timestamps");
            pattern(pass, mod);
            GL11.glViewport(8, 8, 96, 96);
            frame(pass, mod, 0.4F, 16, 0);
            close(pixel(64), 0.4F, "Resize deliberately reseeds with current input");
            GL11.glViewport(0, 0, 128, 128);
        }
        mod.setNumber(MotionBlur.ALGORITHM, MotionBlur.DECAYING_MAX);
        mod.setNumber(MotionBlur.STRENGTH, 5);
        pass.release();
        frame(pass, mod, 1F, 16, 0);
        frame(pass, mod, 0F, 16, 0);
        float previous = pixel(64);
        mod.setNumber(MotionBlur.ALGORITHM, MotionBlur.FRAME_BLEND);
        frame(pass, mod, 0F, 16, 0);
        close(pixel(64), quantize(previous * 0.5F), "Changing temporal methods preserves processed history");
        previous = pixel(64);
        mod.setNumber(MotionBlur.STRENGTH, 10);
        frame(pass, mod, 0F, 16, 0);
        close(pixel(64), quantize(previous * 0.99F), "Changing strength preserves processed history");
        previous = pixel(64);
        mod.setNumber(MotionBlur.ALGORITHM, MotionBlur.CAPPED_BLEND);
        frame(pass, mod, 0F, 16, 0);
        close(pixel(64), quantize(previous * 0.675F), "Capped Blend consumes the preceding method's output");
        mod.setNumber(MotionBlur.STRENGTH, 5);
        java.lang.reflect.Field history = MotionBlurPass.class.getDeclaredField("historyTexture");
        history.setAccessible(true);
        for (float alpha : new float[] {0F, 0.325F, 0.5F, 1F}) {
            ByteBuffer pixels = BufferUtils.createByteBuffer(128 * 128 * 4);
            for (int i = 0; i < 128 * 128; i++) pixels.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) Math.round(alpha * 255));
            pixels.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, history.getInt(pass));
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 128, 128, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            frame(pass, mod, 0F, 16, 0);
            float storedAlpha = quantize(alpha);
            close(pixel(64), quantize(Math.max(0F, Math.min(storedAlpha - 0.325F, storedAlpha * 0.475F))), "Capped Blend uses the full history-alpha formula");
            previous = pixel(64);
            frame(pass, mod, 0F, 16, 0);
            close(pixel(64), quantize(previous * 0.475F), "History alpha resets to one rather than decaying");
        }
        mod.setNumber(MotionBlur.ALGORITHM, MotionBlur.HYBRID);
        frame(pass, mod, 0.7F, 16, 0);
        close(pixel(64), 0.7F, "Entering Hybrid resets history and reallocates float targets");
        mod.setNumber(MotionBlur.ALGORITHM, MotionBlur.FRAME_BLEND);
        frame(pass, mod, 0.2F, 16, 0);
        close(pixel(64), 0.2F, "Leaving Hybrid resets history and reallocates RGBA8 targets");
        pass.release();
    }

    private static void pattern(MotionBlurPass pass, MotionBlur mod) {
        pass.release();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                GL11.glScissor(x, y, 1, 1);
                GL11.glClearColor((x + y) % 2, x < 64 ? 1F : 0F, y < 64 ? 1F : 0F, 0.2F);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        pass.apply(mod);
        for (int y : new int[] {0, 1, 63, 64, 126, 127}) {
            for (int x : new int[] {0, 1, 63, 64, 126, 127}) {
                float[] value = rgba(x, y);
                close(value[0], (x + y) % 2, "Native-resolution checkerboard has no filtering or spatial spreading");
                close(value[1], x < 64 ? 1F : 0F, "Horizontal orientation and boundaries are preserved");
                close(value[2], y < 64 ? 1F : 0F, "Vertical orientation and boundaries are preserved");
                close(value[3], 1F, "Initial transparent input becomes opaque");
            }
        }
    }

    private static float quantize(float value) {
        return Math.round(value * 255F) / 255F;
    }

    private static void close(float actual, float expected, String message) {
        require(Math.abs(actual - expected) <= 1.01F / 255F, message + ": expected " + expected + ", got " + actual);
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
        int framebuffer = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(false, true, false, true);
        pass.apply(mod);
        require(GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == framebuffer, "Framebuffer restored");
        require(GL11.glGetInteger(GL11.GL_DRAW_BUFFER) == drawBuffer, "Draw buffer restored");
        require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "Texture binding restored");
        require(GL11.glIsEnabled(GL11.GL_BLEND) && GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Blend and depth state restored");
        ByteBuffer mask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        require(mask.get(0) == 0 && mask.get(1) != 0 && mask.get(2) == 0 && mask.get(3) != 0, "Color mask restored");
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE1, "Active texture restored");
        require(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_TEXTURE, "Matrix mode restored");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "No GL errors");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static float pixel(int x) {
        return rgba(x, 64)[0];
    }

    private static float[] rgba(int x, int y) {
        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
        return new float[] {pixel.get(0), pixel.get(1), pixel.get(2), pixel.get(3)};
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
