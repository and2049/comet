package comet.core.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class ModRegistry {
    private final List<Mod> mods = new ArrayList<Mod>();
    private final ModSettings settings;
    private final Set<Integer> pressedKeys = new HashSet<Integer>();

    public ModRegistry(ModSettings settings) {
        this.settings = settings;
    }

    public ModSettings settings() {
        return settings;
    }

    public void register(Mod mod) {
        mod.attach(settings);
        mods.add(mod);
    }

    public List<Mod> all() {
        return Collections.unmodifiableList(mods);
    }

    public List<Mod> enabled() {
        List<Mod> result = new ArrayList<Mod>();
        for (Mod mod : mods) {
            if (isEnabled(mod)) {
                result.add(mod);
            }
        }
        return result;
    }

    public List<HudMod> hud() {
        List<HudMod> result = new ArrayList<HudMod>();
        for (Mod mod : enabled()) {
            if (mod instanceof HudMod) {
                result.add((HudMod) mod);
            }
        }
        return result;
    }

    public int enabledCount() {
        return enabled().size();
    }

    public boolean isEnabled(Mod mod) {
        Boolean stored = settings.enabled(mod.id());
        return stored == null ? mod.enabledByDefault() : stored;
    }

    public void toggle(Mod mod) {
        settings.setEnabled(mod.id(), !isEnabled(mod));
        mod.reset();
    }

    public void loadPreset(String name) {
        settings.loadPreset(name);
        reset();
    }

    public void deletePreset(String name) {
        boolean active = name.equals(settings.activePreset());
        settings.deletePreset(name);
        if (active) {
            reset();
        }
    }

    public void tick() {
        for (Mod mod : enabled()) {
            mod.tick();
        }
    }

    public void keyState(int key, boolean pressed) {
        if (key == 0 || !(pressed ? pressedKeys.add(key) : pressedKeys.remove(key))) {
            return;
        }
        for (Mod mod : enabled()) {
            mod.keyState(key, pressed);
        }
    }

    public void releaseKeys() {
        pressedKeys.clear();
    }

    public void reset() {
        releaseKeys();
        for (Mod mod : mods) {
            mod.reset();
        }
    }

    public void preview(boolean enabled) {
        for (Mod mod : mods) {
            if (mod instanceof HudMod) {
                ((HudMod) mod).preview(enabled);
            }
        }
    }
}
