package comet.core.mod;

import comet.core.bridge.GameHost;
import comet.core.ui.TestCanvas;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class FpsParityTests {
    public static void run(Path root) {
        AtomicInteger value = new AtomicInteger();
        GameHost host = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> value.get());
        FpsCounter fps = new FpsCounter(host);
        ModSettings settings = new ModSettings(root.toFile());
        fps.attach(settings);
        TestCanvas canvas = new TestCanvas(320, 240);
        for (int sample : new int[] {0, 9, 99, 1000}) {
            value.set(sample);
            for (boolean reverse : new boolean[] {false, true}) {
                fps.setOption(FpsCounter.REVERSE, reverse);
                String expected = reverse ? "FPS: " + sample : sample + " FPS";
                check(expected.equals(fps.label()), "Direct FPS without caching or averaging");
                check(fps.width(canvas) == canvas.textWidth(expected) + 6, "Measure selected format");
                canvas.texts.clear();
                fps.render(canvas);
                check(canvas.texts.contains(expected), "Render selected format");
            }
        }
        settings.savePreset();
        fps.setOption(FpsCounter.REVERSE, false);
        settings.loadPreset("Default");
        check(fps.option(FpsCounter.REVERSE), "Preset restores format");
        fps.attach(new ModSettings(root.toFile()));
        check(fps.label().equals("FPS: 1000"), "Format survives restart");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
