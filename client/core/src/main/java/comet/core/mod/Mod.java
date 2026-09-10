package comet.core.mod;

import comet.core.event.KeyListener;
import comet.core.event.TickListener;

public abstract class Mod implements TickListener, KeyListener {
    public abstract String id();

    public abstract String name();

    public abstract String description();

    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public void keyPressed(int key) {
    }
}
