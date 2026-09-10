package comet.core.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModRegistry {
    private final List<Mod> mods = new ArrayList<Mod>();
    private final ModSettings settings;

    public ModRegistry(ModSettings settings) {
        this.settings = settings;
    }

    public ModSettings settings() {
        return settings;
    }

    public void register(Mod mod) {
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
    }
}
