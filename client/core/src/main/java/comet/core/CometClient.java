package comet.core;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.mod.CpsCounter;
import comet.core.mod.FpsCounter;
import comet.core.mod.HudLayout;
import comet.core.mod.HudMod;
import comet.core.mod.ModRegistry;
import comet.core.mod.ModSettings;
import comet.core.mod.OldAnimations;
import comet.core.mod.ToggleSprint;
import comet.core.ui.HudEditor;
import comet.core.ui.ModMenu;
import comet.core.ui.Screen;

public final class CometClient {
    private final GameHost host;
    private final ModRegistry mods;
    private final ToggleSprint toggleSprint;
    private final OldAnimations oldAnimations = new OldAnimations();
    private boolean shiftDown;
    private boolean menuOpen;

    public CometClient(GameHost host) {
        this.host = host;
        this.mods = new ModRegistry(new ModSettings(host.gameDirectory()));
        this.toggleSprint = new ToggleSprint(host);
        mods.register(new FpsCounter(host));
        mods.register(new CpsCounter());
        mods.register(toggleSprint);
        if (host.version().startsWith("1.8")) {
            mods.register(oldAnimations);
        }
    }

    public GameHost host() {
        return host;
    }

    public ModRegistry mods() {
        return mods;
    }

    public boolean oldSwing(boolean blocking) {
        return mods.isEnabled(oldAnimations) && oldAnimations.swings(blocking);
    }

    public void tick() {
        boolean down = host.keyDown(Keys.RIGHT_SHIFT);
        if (down && !shiftDown && host.inWorld() && !host.screenOpen()) {
            openModMenu();
        }
        shiftDown = down;
        if (host.inWorld()) {
            mods.tick();
        } else {
            mods.reset();
        }
    }

    public void keyState(int key, boolean pressed) {
        if (host.inWorld() && !host.screenOpen()) {
            mods.keyState(key, pressed);
        }
    }

    public void releaseKeys() {
        mods.releaseKeys();
    }

    public boolean holdsSprint() {
        return mods.isEnabled(toggleSprint) && toggleSprint.holdsSprint();
    }

    public boolean holdsSneak() {
        return mods.isEnabled(toggleSprint) && toggleSprint.holdsSneak();
    }

    public void openHudEditor() {
        openMenu(new HudEditor(this));
    }

    public void openModMenu() {
        openMenu(new ModMenu(this));
    }

    private void openMenu(Screen screen) {
        host.openScreen(screen);
        menuOpen = true;
        mods.preview(true);
    }

    public void menuClosed() {
        menuOpen = false;
        mods.preview(false);
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
