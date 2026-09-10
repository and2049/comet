package comet.core;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.mod.FpsCounter;
import comet.core.mod.HudLayout;
import comet.core.mod.HudMod;
import comet.core.mod.Mod;
import comet.core.mod.ModRegistry;
import comet.core.mod.ModSettings;
import comet.core.ui.ModsMenu;

public final class CometClient {
    private final GameHost host;
    private final ModRegistry mods;
    private boolean shiftDown;
    private boolean menuOpen;

    public CometClient(GameHost host) {
        this.host = host;
        this.mods = new ModRegistry(new ModSettings(host.gameDirectory()));
        mods.register(new FpsCounter(host));
    }

    public GameHost host() {
        return host;
    }

    public ModRegistry mods() {
        return mods;
    }

    public void tick() {
        boolean down = host.keyDown(Keys.RIGHT_SHIFT);
        if (down && !shiftDown && host.inWorld() && !host.screenOpen()) {
            openMods();
        }
        shiftDown = down;
        for (Mod mod : mods.enabled()) {
            mod.tick();
        }
    }

    public void openMods() {
        menuOpen = true;
        host.openScreen(new ModsMenu(this));
    }

    public void menuClosed() {
        menuOpen = false;
    }

    public HudLayout.Placement placement(HudMod mod, Canvas canvas) {
        return HudLayout.resolve(mod, mods.settings().layout(mod.id()), canvas);
    }

    public void renderElement(HudMod mod, HudLayout.Placement placement, Canvas canvas) {
        canvas.push(placement.x, placement.y, placement.scale);
        mod.render(canvas);
        canvas.pop();
    }

    public void renderHud(Canvas canvas) {
        if (!host.inWorld() || menuOpen) {
            return;
        }
        for (HudMod mod : mods.hud()) {
            renderElement(mod, placement(mod, canvas), canvas);
        }
    }
}
