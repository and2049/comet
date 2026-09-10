package comet.core.ui;

import comet.core.bridge.Canvas;
import comet.core.bridge.Images;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

public final class Backdrop {
    private static final int TOP = 0x30000000;
    private static final int BOTTOM = 0xB8101010;
    private static final String[] SLOTS = {"comet:backdrop-a", "comet:backdrop-b"};
    private static final long IDLE_NANOS = 2000000000L;
    private static Backdrop instance;
    private final int frames;
    private final int fps;
    private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<Frame>(3);
    private volatile long lastDraw;
    private Thread decoder;
    private int loaded;
    private int current;
    private double accumulator;
    private long lastTime;

    private static final class Frame {
        final int width;
        final int height;
        final int[] argb;

        Frame(BufferedImage image) {
            width = image.getWidth();
            height = image.getHeight();
            argb = image.getRGB(0, 0, width, height, null, 0, width);
        }
    }

    public static Backdrop get() {
        if (instance == null) {
            instance = new Backdrop();
        }
        return instance;
    }

    private Backdrop() {
        int count = 0;
        int rate = 12;
        try (InputStream in = Backdrop.class.getResourceAsStream("/assets/comet/backdrop/frames.txt")) {
            if (in != null) {
                String[] parts = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine().trim().split("\\s+");
                count = Integer.parseInt(parts[0]);
                rate = Integer.parseInt(parts[1]);
            }
        } catch (IOException error) {
            count = 0;
        } catch (RuntimeException error) {
            count = 0;
        }
        if (count == 0) {
            System.err.println("[Comet] Backdrop frames are missing");
        }
        frames = count;
        fps = rate;
    }

    private static String frame(int number) {
        return String.format("backdrop/frame%03d.jpg", number);
    }

    private void decode() {
        int sequence = 0;
        while (true) {
            if (System.nanoTime() - lastDraw > IDLE_NANOS) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException error) {
                    return;
                }
                continue;
            }
            BufferedImage image = Images.read("comet", frame(sequence % frames + 1));
            if (image == null) {
                System.err.println("[Comet] Backdrop frame " + (sequence % frames + 1) + " is missing");
                return;
            }
            try {
                queue.put(new Frame(image));
            } catch (InterruptedException error) {
                return;
            }
            sequence++;
        }
    }

    private void start() {
        if (decoder != null || frames == 0) {
            return;
        }
        decoder = new Thread(new Runnable() {
            @Override
            public void run() {
                decode();
            }
        }, "Comet backdrop");
        decoder.setDaemon(true);
        decoder.start();
    }

    private boolean advance(Canvas canvas, String slot) {
        Frame frame = queue.poll();
        if (frame == null) {
            return false;
        }
        canvas.texture(slot, frame.width, frame.height, frame.argb);
        return true;
    }

    public void draw(Canvas canvas) {
        int width = canvas.width();
        int height = canvas.height();
        long now = System.nanoTime();
        lastDraw = now;
        start();
        double elapsed = lastTime == 0 ? 0 : (now - lastTime) / 1.0e9D;
        lastTime = now;
        accumulator += Math.min(elapsed, 0.25D) * fps;
        while (loaded < 2 && advance(canvas, SLOTS[loaded])) {
            loaded++;
        }
        while (loaded == 2 && accumulator >= 1 && advance(canvas, SLOTS[current])) {
            accumulator -= 1;
            current = 1 - current;
        }
        if (loaded == 0) {
            canvas.fill(0, 0, width, height, 0xFF101010);
        } else {
            int[] size = canvas.textureSize(SLOTS[current]);
            double scale = Math.max(width / (double) size[0], height / (double) size[1]);
            int drawWidth = (int) Math.ceil(size[0] * scale);
            int drawHeight = (int) Math.ceil(size[1] * scale);
            int x = (width - drawWidth) / 2;
            int y = (height - drawHeight) / 2;
            canvas.draw(SLOTS[current], x, y, drawWidth, drawHeight, 1.0F);
            if (loaded == 2) {
                canvas.draw(SLOTS[1 - current], x, y, drawWidth, drawHeight, (float) Math.min(1.0D, accumulator));
            }
        }
        canvas.gradient(0, 0, width, height, TOP, BOTTOM);
    }
}
