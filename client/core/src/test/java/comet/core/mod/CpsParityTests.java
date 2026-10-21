package comet.core.mod;

import comet.core.CometClient;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class CpsParityTests {
    public static void run(Path root) {
        AtomicLong clock = new AtomicLong(1000);
        ClickStats stats = new ClickStats(clock::get);
        stats.record(Keys.MOUSE_LEFT, true, true);
        stats.record(Keys.MOUSE_LEFT, true, false);
        stats.record(Keys.MOUSE_LEFT, false, false);
        stats.record(Keys.MOUSE_LEFT, true, false);
        stats.record(Keys.MOUSE_RIGHT, true, true);
        check(stats.left(false) == 2 && stats.left(true) == 1 && stats.right() == 1, "Physical edges and item-use filter");
        CpsCounter cps = new CpsCounter(stats);
        ModRegistry registry = new ModRegistry(new ModSettings(root.toFile()), stats::reset);
        registry.register(cps);
        check(cps.label().equals("[ 2 | 1 ]"), "Existing format");
        cps.setOption(CpsCounter.FILTER, true);
        cps.setOption(CpsCounter.BRACKETS, false);
        cps.setOption(CpsCounter.LABEL, true);
        cps.setNumber(CpsCounter.ORDER, 1);
        cps.setNumber(CpsCounter.BUTTONS, 1);
        check(cps.label().equals("1 CPS"), "Filtered left suffix");
        cps.setNumber(CpsCounter.BUTTONS, 2);
        cps.setNumber(CpsCounter.ORDER, 0);
        check(cps.label().equals("CPS 1"), "Right prefix");
        registry.toggle(cps);
        check(stats.left(false) == 2, "Disabling HUD leaves shared statistics available");
        clock.set(1999);
        check(stats.left(false) == 2, "999ms retains clicks");
        clock.set(2000);
        check(stats.left(false) == 0 && stats.left(true) == 0 && stats.right() == 0, "Exact expiry of every history");
        stats.reset();
        stats.record(Keys.MOUSE_LEFT, true, false);
        registry.loadPreset("Default");
        check(stats.left(false) == 0, "Presets reset shared statistics");

        boolean[] state = {true, false, false};
        GameHost host = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "gameDirectory": return root.resolve("client").toFile();
                case "version": return "1.8.9";
                case "inWorld": return state[0];
                case "screenOpen": return state[1];
                case "usingItem": return state[2];
                case "keyDown": return false;
                case "sprintKey": return 29;
                case "sneakKey": return 42;
                default:
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
            }
        });
        CometClient client = new CometClient(host);
        for (Mod mod : client.mods().all()) if (mod.id().equals("cps")) client.mods().toggle(mod);
        client.keyState(Keys.MOUSE_LEFT, true);
        client.keyState(Keys.MOUSE_LEFT, false);
        check(client.clickStats().left(false) == 1, "Statistics update with CPS disabled");
        state[1] = true;
        client.keyState(Keys.MOUSE_RIGHT, true);
        check(client.clickStats().right() == 0, "GUI clicks excluded");
        client.tick();
        check(client.clickStats().left(false) == 0, "Menus clear histories");
        state[1] = false;
        state[2] = true;
        client.keyState(Keys.MOUSE_LEFT, true);
        check(client.clickStats().left(false) == 1 && client.clickStats().left(true) == 0, "Host item use filters physical click");
        client.releaseKeys();
        check(client.clickStats().left(false) == 0, "Focus release clears histories");
        client.keyState(Keys.MOUSE_RIGHT, true);
        state[0] = false;
        client.tick();
        check(client.clickStats().right() == 0, "World exit clears histories");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
