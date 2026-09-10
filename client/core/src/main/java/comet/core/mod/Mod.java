package comet.core.mod;

import comet.core.event.KeyListener;
import comet.core.event.TickListener;
import java.util.Collections;
import java.util.List;

public abstract class Mod implements TickListener, KeyListener {
    private ModSettings settings;

    public abstract String id();

    public abstract String name();

    public abstract String description();

    public abstract Category category();

    public String glyph() {
        return name().substring(0, Math.min(3, name().length())).toUpperCase();
    }

    public boolean enabledByDefault() {
        return false;
    }

    public List<Option> options() {
        return Collections.emptyList();
    }

    void attach(ModSettings settings) {
        this.settings = settings;
    }

    public boolean option(Option option) {
        Boolean stored = settings == null ? null : settings.option(id(), option.id);
        return stored == null ? option.defaultValue : stored;
    }

    public void setOption(Option option, boolean value) {
        settings.setOption(id(), option.id, value);
    }

    public void reset() {
    }

    @Override
    public void tick() {
    }

    @Override
    public void keyState(int key, boolean pressed) {
    }
}
