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

    public List<NumberOption> numbers() {
        return Collections.emptyList();
    }

    public int number(NumberOption option) {
        Integer value = settings == null ? null : settings.number(id(), option.id);
        return value == null ? option.defaultValue : option.clamp(value);
    }

    public void setNumber(NumberOption option, int value) {
        settings.setNumber(id(), option.id, option.clamp(value));
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

    protected boolean global(GlobalSetting setting) {
        return settings == null ? setting.defaultValue : settings.global(setting);
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
