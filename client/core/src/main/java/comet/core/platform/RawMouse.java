package comet.core.platform;

import java.util.ArrayList;
import java.util.List;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.LinuxEnvironmentPlugin;
import net.java.games.input.OSXEnvironmentPlugin;
import net.java.games.input.RawInputEnvironmentPlugin;

public final class RawMouse {
    public interface Source {
        float[] poll();
    }

    private Source source;
    private boolean initialized;
    private boolean active;
    private double remainderX;
    private double remainderY;
    private String status = "";

    public RawMouse() {
    }

    public RawMouse(Source source) {
        this.source = source;
        initialized = true;
    }

    public String status() {
        return status;
    }

    public void prepare() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            String os = System.getProperty("os.name", "");
            ControllerEnvironment environment = os.startsWith("Windows") ? new RawInputEnvironmentPlugin()
                    : os.startsWith("Linux") ? new LinuxEnvironmentPlugin() : os.startsWith("Mac") ? new OSXEnvironmentPlugin() : null;
            final List<net.java.games.input.Mouse> mice = new ArrayList<net.java.games.input.Mouse>();
            if (environment != null && environment.isSupported()) {
                for (Controller controller : environment.getControllers()) {
                    if (controller instanceof net.java.games.input.Mouse) {
                        net.java.games.input.Mouse mouse = (net.java.games.input.Mouse) controller;
                        if (mouse.getX() != null && mouse.getY() != null && mouse.getX().isRelative() && mouse.getY().isRelative()) {
                            mice.add(mouse);
                        }
                    }
                }
            }
            if (mice.isEmpty()) {
                status = "Raw input unavailable; using normal mouse input";
                return;
            }
            source = () -> {
                float x = 0;
                float y = 0;
                int connected = 0;
                for (net.java.games.input.Mouse mouse : mice) {
                    if (mouse.poll()) {
                        x += mouse.getX().getPollData();
                        y -= mouse.getY().getPollData();
                        connected++;
                    }
                }
                return connected == 0 ? null : new float[] {x, y};
            };
        } catch (RuntimeException | LinkageError failure) {
            status = "Raw input unavailable; using normal mouse input";
            System.err.println("[Comet] " + status + ": " + failure.getMessage());
        }
    }

    public void reset() {
        active = false;
        remainderX = 0;
        remainderY = 0;
    }

    public int[] read(boolean enabled, boolean focused) {
        if (!enabled) {
            reset();
            return null;
        }
        prepare();
        if (source == null) {
            return null;
        }
        float[] movement;
        try {
            movement = source.poll();
        } catch (RuntimeException | LinkageError failure) {
            movement = null;
        }
        if (movement == null || movement.length != 2 || !Float.isFinite(movement[0]) || !Float.isFinite(movement[1])) {
            reset();
            status = "Raw mouse disconnected; using normal mouse input";
            return null;
        }
        status = "";
        if (!focused || !active) {
            reset();
            active = focused;
            return new int[] {0, 0};
        }
        remainderX += movement[0];
        remainderY += movement[1];
        int x = (int) remainderX;
        int y = (int) remainderY;
        remainderX -= x;
        remainderY -= y;
        return new int[] {x, y};
    }
}
