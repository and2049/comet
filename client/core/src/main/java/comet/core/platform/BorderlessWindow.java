package comet.core.platform;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class BorderlessWindow {
    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";
    public interface Backend {
        State current();
        State desktop();
        void apply(State state) throws Exception;
    }

    public static final class State {
        public final int width;
        public final int height;
        public final int x;
        public final int y;
        public final String decoration;
        public final boolean resizable;

        public State(int width, int height, int x, int y, String decoration, boolean resizable) {
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            this.decoration = decoration;
            this.resizable = resizable;
        }
    }

    private final Backend backend;
    private boolean active;
    private State previous;
    private String status = "";

    public BorderlessWindow() {
        this(new LwjglBackend());
    }

    public BorderlessWindow(Backend backend) {
        this.backend = backend;
    }

    public String status() {
        return status;
    }

    public boolean active() {
        return active;
    }

    public boolean transition() {
        State before = backend.current();
        try {
            backend.apply(active ? previous : backend.desktop());
            if (!active) {
                previous = before;
            }
            active = !active;
            status = "";
            return true;
        } catch (Exception failure) {
            try {
                backend.apply(before);
            } catch (Exception rollback) {
                failure.addSuppressed(rollback);
            }
            status = "Could not change fullscreen mode";
            System.err.println("[Comet] Fullscreen transition failed: " + failure.getMessage());
            return false;
        }
    }

    private static final class LwjglBackend implements Backend {
        public State current() {
            return new State(Display.getWidth(), Display.getHeight(), Display.getX(), Display.getY(), System.getProperty(UNDECORATED), Display.isResizable());
        }

        public State desktop() {
            DisplayMode desktop = Display.getDesktopDisplayMode();
            return new State(desktop.getWidth(), desktop.getHeight(), 0, 0, "true", false);
        }

        public void apply(State state) throws LWJGLException {
            if (state.decoration == null) {
                System.clearProperty(UNDECORATED);
            } else {
                System.setProperty(UNDECORATED, state.decoration);
            }
            Display.setFullscreen(false);
            Display.setResizable(state.resizable);
            Display.setDisplayMode(new DisplayMode(state.width, state.height));
            Display.setLocation(state.x, state.y);
            Display.update();
            int widthDifference = state.width - Display.getWidth();
            int heightDifference = state.height - Display.getHeight();
            if (widthDifference != 0 || heightDifference != 0) {
                Display.setDisplayMode(new DisplayMode(state.width + widthDifference, state.height + heightDifference));
                Display.setLocation(state.x, state.y);
                Display.update();
            }
        }
    }
}
