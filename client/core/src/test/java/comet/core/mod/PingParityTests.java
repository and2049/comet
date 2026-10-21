package comet.core.mod;

import comet.core.bridge.GameHost;
import comet.core.bridge.PingBackend;
import comet.core.ui.TestCanvas;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PingParityTests {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final long SECOND = 1000L;
    private static int assertions;
    private static int cases;

    private PingParityTests() {
    }

    public static void run(Path root) throws Exception {
        Files.createDirectories(root);
        defaults(root.resolve("defaults"));
        timing(root.resolve("timing"));
        batches(root.resolve("batches"));
        dedupe(root.resolve("dedupe"));
        spikes(root.resolve("spikes"));
        formats(root.resolve("formats"));
        icons(root.resolve("icons"));
        nametags(root.resolve("nametags"));
        lifecycle(root.resolve("lifecycle"));
        switches(root.resolve("switches"));
        noFallback(root.resolve("fallback"));
        System.out.println("Ping parity: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Path next(Path root) {
        return root.resolve("case-" + cases++);
    }

    private static void ticks(PingCounter ping, int count) {
        for (int index = 0; index < count; index++) {
            ping.tick();
        }
    }

    private static void elapse(Fixture fixture) {
        fixture.clock[0] += SECOND;
        fixture.ping.tick();
    }

    private static void beat(Fixture fixture, int value) {
        fixture.pinger.complete(value);
        elapse(fixture);
    }

    private static void defaults(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        check(ping.label().equals("-- ms"), "Unknown player-list latency keeps -- ms");
        fixture.host.ping = 183;
        check(ping.label().equals("183 ms"), "Player-list latency is shown in milliseconds");
        fixture.host.ping = 0;
        check(ping.label().equals("0 ms"), "A zero reading is real latency, not a missing one");
        fixture.host.local = true;
        check(ping.label().equals("Local"), "Singleplayer keeps the Local label");
        fixture.host.local = false;
        ping.preview(true);
        check(ping.label().equals("42 ms"), "The editor preview keeps its sample value");
        ping.preview(false);
        check(ping.number(PingCounter.SOURCE) == 0 && PingCounter.SOURCE.display(0).equals("Player list"), "Player-list latency is the default source");
        check(PingCounter.SOURCE.display(1).equals("Server status"), "The active server status source is selectable");
        check(ping.number(PingCounter.MODE) == 0 && PingCounter.MODE.display(0).equals("Latest") && PingCounter.MODE.display(1).equals("Averaged"),
                "Latest is the default mode and Averaged is selectable");
        check(ping.number(PingCounter.SAMPLES) == 3 && PingCounter.SAMPLES.display(3).equals("3"), "Three samples is the default batch");
        check(ping.number(PingCounter.INTERVAL) == 5 && PingCounter.INTERVAL.display(5).equals("5s"), "The default interval is five seconds");
        check(ping.number(PingCounter.MEDIUM_SPIKE) == 20 && PingCounter.MEDIUM_SPIKE.display(20).equals("20%"), "The medium spike threshold defaults to 20 percent");
        check(ping.number(PingCounter.LARGE_SPIKE) == 50 && ping.number(PingCounter.SPIKE_EXPIRY) == 5000, "Large spike and expiry defaults match the parent mod");
        check(ping.option(PingCounter.SHOW_MS) && !ping.option(PingCounter.SHOW_PREFIX) && ping.option(PingCounter.HUD), "Units and the HUD are on while the prefix stays off");
        check(!ping.option(PingCounter.NAMETAG) && ping.option(PingCounter.NAMETAG_ABOVE) && !ping.option(PingCounter.ICON), "The nametag is opt-in and the icon is optional");
        check(PingCounter.PREFIX.maxLength == 10 && ping.text(PingCounter.PREFIX).equals("Ping: "), "The custom prefix is limited to ten characters");
        check(ping.visible(), "The HUD element is visible by default");
        ping.setOption(PingCounter.HUD, false);
        check(!ping.visible(), "Disabling the HUD hides the element");
        ping.setOption(PingCounter.HUD, true);
        check(PingCounter.SAMPLES.clamp(1) == 2 && PingCounter.SAMPLES.clamp(99) == 20, "The sample count is bounded to 2-20");
        check(PingCounter.INTERVAL.clamp(0) == 1 && PingCounter.INTERVAL.clamp(999) == 120, "The interval is bounded to 1-120");
        check(PingCounter.MEDIUM_SPIKE.clamp(0) == 1 && PingCounter.LARGE_SPIKE.clamp(999) == 200, "Spike thresholds are bounded to 1-200");
        check(PingCounter.SPIKE_EXPIRY.clamp(0) == 1000 && PingCounter.SPIKE_EXPIRY.clamp(999999) == 60000, "Spike expiry is bounded");
    }

    private static void timing(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.INTERVAL, 5);
        fixture.host.ping = 100;
        ping.tick();
        fixture.host.ping = 130;
        ticks(ping, 200);
        check(ping.spikeDelta() == 0, "Ticks alone never advance the sampling deadline");
        fixture.clock[0] += 4999;
        ping.tick();
        check(ping.spikeDelta() == 0, "The deadline is not reached early");
        fixture.clock[0] += 1;
        ping.tick();
        check(ping.spikeDelta() == 30, "The sample lands exactly one interval later");
        fixture.host.ping = 260;
        fixture.clock[0] += 5000;
        ping.tick();
        check(ping.spikeDelta() == 100, "The next deadline is measured from the previous sample");
        ping.setNumber(PingCounter.INTERVAL, 1);
        fixture.host.ping = 650;
        fixture.clock[0] += 999;
        ping.tick();
        check(ping.spikeDelta() == 100, "A one second interval waits a full second");
        fixture.clock[0] += 1;
        ping.tick();
        check(ping.spikeDelta() == 150, "A one second interval samples when the second elapses");

        fixture = new Fixture(next(root));
        ping = fixture.ping;
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.setNumber(PingCounter.INTERVAL, 5);
        ping.tick();
        check(fixture.pinger.refreshes == 1, "Selecting the active source requests one sample immediately");
        fixture.pinger.complete(120);
        ping.tick();
        check(ping.label().equals("120 ms"), "A completed reading is shown without waiting for the interval");
        ticks(ping, 200);
        check(fixture.pinger.refreshes == 1, "Requests are bounded by the interval, not by ticks");
        fixture.clock[0] += 5000;
        ping.tick();
        check(fixture.pinger.refreshes == 2, "The interval requests exactly one more sample");
    }

    private static void batches(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.setNumber(PingCounter.MODE, 1);
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.setNumber(PingCounter.SAMPLES, 3);
        ping.tick();
        beat(fixture, 100);
        check(ping.label().equals("100 ms"), "An incomplete batch falls back to the latest reading");
        beat(fixture, 200);
        beat(fixture, 300);
        check(ping.label().equals("200 ms"), "Three samples complete a disjoint batch average");
        beat(fixture, 100);
        check(ping.label().equals("200 ms"), "The last completed average stays visible while the next batch fills");
        beat(fixture, 300);
        beat(fixture, 500);
        check(ping.label().equals("300 ms"), "The next batch replaces the average");

        ping.reset();
        ping.tick();
        ping.setNumber(PingCounter.SAMPLES, 2);
        beat(fixture, 100);
        beat(fixture, 101);
        check(ping.label().equals("100 ms"), "Averages truncate instead of rounding");

        ping.reset();
        ping.tick();
        beat(fixture, -1);
        check(ping.label().equals("-- ms"), "Negative active readings are ignored");

        ping.reset();
        ping.tick();
        ping.setNumber(PingCounter.SAMPLES, 4);
        beat(fixture, 10);
        beat(fixture, 20);
        ping.setNumber(PingCounter.SAMPLES, 2);
        beat(fixture, 30);
        check(ping.label().equals("30 ms"), "A shrinking sample count starts a fresh aggregate");

        ping.reset();
        ping.tick();
        ping.setNumber(PingCounter.SAMPLES, 3);
        beat(fixture, 100);
        beat(fixture, 200);
        check(ping.label().equals("200 ms"), "An incomplete batch shows the latest reading");
        ping.setNumber(PingCounter.SAMPLES, 5);
        beat(fixture, 300);
        check(ping.label().equals("300 ms"), "A changed sample count discards the running batch");
        beat(fixture, 400);
        beat(fixture, 500);
        check(ping.label().equals("500 ms"), "The new batch still needs its full sample count");
        beat(fixture, 600);
        beat(fixture, 700);
        check(ping.label().equals("500 ms"), "The new batch averages only its own samples");

        ping.reset();
        ping.tick();
        ping.setNumber(PingCounter.SAMPLES, 2);
        beat(fixture, 1500000000);
        beat(fixture, 1500000000);
        check(ping.label().equals("1500000000 ms"), "Batch sums use long arithmetic");
    }

    private static void dedupe(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.setNumber(PingCounter.MODE, 1);
        ping.setNumber(PingCounter.SAMPLES, 3);
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.tick();
        beat(fixture, 100);
        beat(fixture, 400);
        ticks(ping, 4);
        for (int index = 0; index < 4; index++) {
            elapse(fixture);
        }
        check(ping.label().equals("400 ms"), "Repeated deadlines never re-count one completed reading");
        fixture.pinger.complete(700);
        elapse(fixture);
        check(ping.label().equals("400 ms"), "A new completion is counted exactly once");
    }

    private static void spikes(Path root) {
        check(spike(root, 20, 50, 100, 120).spikeDelta() == 20, "Exactly +20 percent triggers the medium threshold");
        check(spike(root, 20, 50, 100, 119).spikeDelta() == 0, "+19 percent stays below the medium threshold");
        check(spike(root, 20, 50, 100, 80).spikeDelta() == -20, "Exactly -20 percent triggers the medium threshold");
        check(spike(root, 20, 50, 100, 81).spikeDelta() == 0, "-19 percent stays below the medium threshold");
        check(spike(root, 20, 50, 100, 150).largeSpike(), "Exactly +50 percent is a large spike");
        check(spike(root, 20, 50, 100, 149).spikeDelta() == 49 && !spike(root, 20, 50, 100, 149).largeSpike(),
                "Below the large threshold stays medium");
        check(spike(root, 50, 200, 100, 130).spikeDelta() == 0, "A raised medium threshold suppresses smaller spikes");
        check(spike(root, 50, 200, 100, 150).spikeDelta() == 50, "A raised medium threshold still catches larger spikes");
        check(spike(root, 20, 50, 0, 500).spikeDelta() == 0, "A rise from zero is not a percentage spike");
        check(spike(root, 20, 50, 100, 149).label().endsWith("(+49%)"), "A medium spike shows its percentage");
        check(spike(root, 20, 50, 100, 150).label().endsWith("(+50%!)"), "A large spike is marked textually");
        check(spike(root, 20, 50, 100, 80).label().endsWith("(-20%)"), "Falls are negative percentages");
        check(spike(root, 20, 50, 100, 40).label().endsWith("(-60%!)"), "A large fall is marked textually");

        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.setOption(PingCounter.SPIKE_DETECTION, true);
        fixture.host.ping = 100;
        ping.tick();
        fixture.clock[0] = 1000;
        fixture.host.ping = 150;
        ping.tick();
        check(ping.label().equals("150 ms (+50%!)"), "The spike marker is appended to the reading");
        fixture.host.ping = 160;
        fixture.clock[0] = 2000;
        ping.tick();
        check(ping.spikeDelta() == 50, "A below-threshold change keeps the active spike");
        fixture.host.ping = 180;
        fixture.clock[0] = 3000;
        ping.tick();
        fixture.host.ping = 90;
        fixture.clock[0] = 4000;
        ping.tick();
        check(ping.spikeDelta() == -50 && ping.label().equals("90 ms (-50%!)"), "Falls are recorded as negative spikes");
        fixture.clock[0] = 4000 + 4999;
        ping.tick();
        check(ping.spikeDelta() == -50, "A spike survives just under the expiry");
        fixture.clock[0] = 4000 + 5000;
        ping.tick();
        check(ping.spikeDelta() == 0 && ping.label().equals("90 ms"), "A spike expires at the configured age");
        ping.setNumber(PingCounter.SPIKE_EXPIRY, 1000);
        fixture.host.ping = 180;
        fixture.clock[0] = 20000;
        ping.tick();
        check(ping.spikeDelta() == 100, "A new spike is recorded after the previous one expired");
        fixture.clock[0] = 20000 + 999;
        ping.tick();
        check(ping.spikeDelta() == 100, "The configured expiry is not reached early");
        fixture.clock[0] = 20000 + 1000;
        ping.tick();
        check(ping.spikeDelta() == 0, "The configured expiry clears the spike");
        ping.setOption(PingCounter.SPIKE_DETECTION, false);
        fixture.host.ping = 360;
        fixture.clock[0] = 30000;
        ping.tick();
        check(ping.spikeDelta() == 100 && !ping.label().contains("%"), "Detection tracks spikes while the suffix is disabled");
    }

    private static void formats(Path root) {
        Path directory = next(root);
        Fixture fixture = new Fixture(directory);
        PingCounter ping = fixture.ping;
        fixture.host.ping = 183;
        check(ping.label().equals("183 ms"), "Milliseconds are shown by default");
        ping.setOption(PingCounter.SHOW_MS, false);
        check(ping.label().equals("183"), "The unit suffix can be removed");
        fixture.host.ping = -1;
        check(ping.label().equals("--"), "Unknown latency drops the suffix too");
        ping.setOption(PingCounter.SHOW_MS, true);
        ping.setOption(PingCounter.SHOW_PREFIX, true);
        check(ping.label().equals("Ping: -- ms"), "The default prefix is applied to unknown values");
        fixture.host.ping = 183;
        check(ping.label().equals("Ping: 183 ms"), "The prefix is applied to readings");
        ping.setText(PingCounter.PREFIX, "Latency: ");
        check(ping.label().equals("Latency: 183 ms"), "A custom prefix is used");
        ping.setText(PingCounter.PREFIX, "   ");
        check(ping.label().equals("183 ms"), "A blank prefix is ignored");
        check(PingCounter.PREFIX.sanitize("1234567890123").equals("1234567890"), "Prefixes are truncated to ten characters");
        check(PingCounter.PREFIX.sanitize("a\u00a7k\u0000c").equals("akc"), "Prefixes drop formatting and control characters");
        ping.setText(PingCounter.PREFIX, "Ping");
        ping.setNumber(PingCounter.INTERVAL, 30);
        ping.setOption(PingCounter.ICON, true);
        ModSettings reloaded = new ModSettings(directory.toFile());
        check(reloaded.number("ping", "interval") == 30 && Boolean.TRUE.equals(reloaded.option("ping", "icon"))
                && "Ping".equals(reloaded.text("ping", "prefix")), "Ping options persist in the existing settings file");
    }

    private static void icons(Path root) {
        check(PingCounter.bars(-1) == 0, "An unknown reading fills no bars");
        check(PingCounter.bars(0) == 5, "A zero reading fills every bar");
        check(PingCounter.bars(149) == 5 && PingCounter.bars(150) == 4, "150 ms drops to four bars");
        check(PingCounter.bars(299) == 4 && PingCounter.bars(300) == 3, "300 ms drops to three bars");
        check(PingCounter.bars(599) == 3 && PingCounter.bars(600) == 2, "600 ms drops to two bars");
        check(PingCounter.bars(999) == 2 && PingCounter.bars(1000) == 1, "1000 ms drops to one bar");
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        fixture.host.ping = 42;
        TestCanvas plain = new TestCanvas(320, 240);
        int width = ping.width(plain);
        int height = ping.height(plain);
        ping.setOption(PingCounter.ICON, true);
        TestCanvas icon = new TestCanvas(320, 240);
        check(ping.width(icon) == width + 12, "The signal bars add their own width");
        check(ping.height(icon) == height, "The signal bars do not change the element height");
        ping.render(icon);
        check(icon.texts.contains("42 ms"), "The numeric value is still drawn next to the bars");
        check(icon.pixel(3, 10) == 0xFFFFFFFF, "A fast reading fills the first bar");
        fixture.host.ping = 1000;
        TestCanvas slow = new TestCanvas(320, 240);
        ping.render(slow);
        check(slow.pixel(3, 10) == 0xFFFFFFFF, "A slow reading keeps the shortest bar");
        check(slow.pixel(11, 10) != 0xFFFFFFFF, "A slow reading leaves the tallest bar empty");
    }

    private static void nametags(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        fixture.pinger.latency.put("Alex", 78);
        fixture.host.ping = 120;
        check(ping.nametagLine(OTHER, "Alex", false, "Alex") == null, "The nametag line is opt-in");
        ping.setOption(PingCounter.NAMETAG, true);
        check(ping.nametagLine(OTHER, "Alex", false, "Alex").equals("78 ms"), "Other players use their tab-list latency");
        check(ping.nametagLine(OTHER, "Missing", false, "Missing") == null, "Players without tab info add no line");
        check(ping.nametagLine(OTHER, "Alex", false, "\u00a7kAlex") == null, "Obfuscated names hide the other-player line");
        check(ping.nametagLine(OTHER, "Alex", false, "\u00a7KAlex") == null, "Upper-case obfuscation is hidden too");
        check(ping.nametagLine(OTHER, "Alex", false, "Alex \u00a7k") == null, "Obfuscation anywhere in the name hides the line");
        check(ping.nametagLine(SELF, "You", true, "You").equals("120 ms"), "The local player follows the selected source");
        fixture.host.local = true;
        check(ping.nametagLine(SELF, "You", true, "You") == null, "Singleplayer has no nametag reading");
        fixture.host.local = false;
        fixture.host.ping = -1;
        check(ping.nametagLine(SELF, "You", true, "You") == null, "Unknown latency adds no nametag line");
        fixture.host.ping = 120;
        check(ping.nametagAbove(), "The ping line defaults above the name");
        ping.setOption(PingCounter.NAMETAG_ABOVE, false);
        check(!ping.nametagAbove(), "The ping line can be placed below the name");
        ping.setOption(PingCounter.NAMETAG_ABOVE, true);
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.setOption(PingCounter.SPIKE_DETECTION, true);
        fixture.host.ping = 100;
        ping.tick();
        fixture.host.ping = 150;
        fixture.clock[0] += SECOND;
        ping.tick();
        check(ping.nametagLine(SELF, "You", true, "You").equals("150 ms (+50%!)"), "The local nametag shows spikes");
        check(ping.nametagLine(OTHER, "Alex", false, "Alex").equals("78 ms"), "Other players never inherit the local spike");
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.tick();
        fixture.pinger.complete(90);
        fixture.clock[0] += SECOND;
        ping.tick();
        check(ping.nametagLine(SELF, "You", true, "You").equals("90 ms"), "The nametag follows the active source");
        check(ping.nametagLine(OTHER, "Alex", false, "Alex").equals("78 ms"), "Other players stay on the tab list in the active source");
        check(PingBackend.NONE.tabLatency(OTHER, "Alex") == -1 && PingBackend.NONE.sample() == -1
                && PingBackend.NONE.session() == 0 && PingBackend.NONE.sequence() == 0, "The empty backend never invents data");
    }

    private static void lifecycle(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.setNumber(PingCounter.MODE, 1);
        ping.setNumber(PingCounter.SAMPLES, 2);
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.tick();
        beat(fixture, 100);
        beat(fixture, 200);
        check(ping.label().equals("150 ms"), "A batch average is shown before disconnecting");
        int resets = fixture.pinger.resets;
        fixture.registry.reset();
        check(fixture.pinger.resets == resets + 1, "Leaving a world resets the active backend");
        check(ping.label().equals("-- ms"), "Leaving a world clears the reading");
        check(ping.spikeDelta() == 0, "Leaving a world clears spike state");
        ping.tick();
        check(ping.label().equals("-- ms"), "A reset backend does not resurrect the previous reading");
        check(fixture.pinger.refreshes >= 2, "Sampling resumes after a reset");
        fixture.host.ping = 250;
        ping.setNumber(PingCounter.SOURCE, 0);
        ping.tick();
        check(ping.label().equals("250 ms"), "The player-list source takes over");
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.tick();
        check(ping.label().equals("-- ms"), "Switching back to the active source starts clean");
        check(fixture.pinger.resets >= resets + 2, "A source switch resets the active backend");
    }

    private static void switches(Path root) {
        Fixture fixture = new Fixture(next(root));
        PingCounter ping = fixture.ping;
        ping.setNumber(PingCounter.SOURCE, 1);
        ping.setNumber(PingCounter.MODE, 1);
        ping.setNumber(PingCounter.SAMPLES, 2);
        ping.setNumber(PingCounter.INTERVAL, 1);
        ping.tick();
        beat(fixture, 100);
        beat(fixture, 300);
        check(ping.label().equals("200 ms"), "A two-sample average is visible before the switch");
        fixture.pinger.switchServer();
        beat(fixture, 1000);
        check(ping.label().equals("1000 ms"), "A server switch discards the previous average");
        check(ping.spikeDelta() == 0, "A server switch clears spike state");
        beat(fixture, 600);
        check(ping.label().equals("800 ms"), "The new session averages only its own samples");
        ping.setNumber(PingCounter.MODE, 0);
        ping.tick();
        check(ping.label().equals("600 ms"), "Changing the mode discards the average");
        ping.setNumber(PingCounter.MODE, 1);
        ping.tick();
        beat(fixture, 400);
        beat(fixture, 500);
        check(ping.label().equals("450 ms"), "A fresh average builds after the mode change");
        ping.setNumber(PingCounter.SAMPLES, 4);
        ping.tick();
        check(ping.label().equals("500 ms"), "Changing the sample count discards the average");
    }

    private static void noFallback(Path root) {
        Fixture unavailable = new Fixture(next(root));
        unavailable.pinger.available = false;
        unavailable.ping.setNumber(PingCounter.SOURCE, 1);
        unavailable.ping.setNumber(PingCounter.INTERVAL, 1);
        unavailable.ping.setOption(PingCounter.SPIKE_DETECTION, true);
        unavailable.host.ping = 100;
        unavailable.ping.tick();
        unavailable.host.ping = 400;
        unavailable.clock[0] += 5000;
        unavailable.ping.tick();
        check(unavailable.ping.label().equals("-- ms"), "A requested active source shows -- when its backend is unavailable");
        check(unavailable.ping.spikeDelta() == 0, "An unavailable backend never samples the tab list");
        Fixture empty = new Fixture(next(root), PingBackend.NONE);
        empty.ping.setNumber(PingCounter.SOURCE, 1);
        empty.host.ping = 77;
        empty.ping.tick();
        check(empty.ping.label().equals("-- ms"), "The empty backend never falls back to the tab list");
        empty.ping.setOption(PingCounter.NAMETAG, true);
        check(empty.ping.nametagLine(SELF, "You", true, "You") == null, "The empty backend adds no self nametag line");
    }

    private static Fixture spike(Path root, int medium, int large, int... values) {
        Fixture fixture = new Fixture(next(root));
        fixture.ping.setNumber(PingCounter.INTERVAL, 1);
        fixture.ping.setNumber(PingCounter.MEDIUM_SPIKE, medium);
        fixture.ping.setNumber(PingCounter.LARGE_SPIKE, large);
        fixture.ping.setOption(PingCounter.SPIKE_DETECTION, true);
        for (int value : values) {
            fixture.host.ping = value;
            elapse(fixture);
        }
        return fixture;
    }

    private static final class Fixture {
        final FakePinger pinger = new FakePinger();
        final long[] clock = {0L};
        final Host host;
        final ModRegistry registry;
        final PingCounter ping;

        Fixture(Path root) {
            this(root, null);
        }

        Fixture(Path root, PingBackend backend) {
            host = new Host(backend == null ? pinger : backend);
            registry = new ModRegistry(new ModSettings(root.toFile()));
            ping = new PingCounter(host.bridge, () -> clock[0]);
            registry.register(ping);
        }

        int spikeDelta() {
            return ping.spikeDelta();
        }

        boolean largeSpike() {
            return ping.largeSpike();
        }

        String label() {
            return ping.label();
        }
    }

    private static final class FakePinger implements PingBackend {
        final Map<String, Integer> latency = new HashMap<String, Integer>();
        int sample = -1;
        int session;
        int sequence;
        int refreshes;
        int resets;
        boolean available = true;
        boolean ticked;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public void refresh() {
            check(ticked, "Active backend is initialized before the first refresh");
            refreshes++;
        }

        @Override
        public void tick() {
            ticked = true;
        }

        @Override
        public int sample() {
            return sample;
        }

        @Override
        public int session() {
            return session;
        }

        @Override
        public int sequence() {
            return sequence;
        }

        @Override
        public int tabLatency(UUID player, String playerName) {
            Integer value = latency.get(playerName);
            return value == null ? -1 : value;
        }

        @Override
        public void reset() {
            resets++;
            session++;
            sample = -1;
        }

        void complete(int value) {
            sample = value;
            sequence++;
        }

        void switchServer() {
            session++;
            sample = -1;
        }
    }

    private static final class Host {
        final GameHost bridge;
        final TestCanvas canvas = new TestCanvas(320, 240);
        int ping = -1;
        boolean local;

        Host(PingBackend backend) {
            bridge = (GameHost) Proxy.newProxyInstance(GameHost.class.getClassLoader(), new Class<?>[] {GameHost.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "gameDirectory": return new File(".");
                            case "version": return "1.8.9";
                            case "inWorld": return true;
                            case "screenOpen": return false;
                            case "ping": return ping;
                            case "singleplayer": return local;
                            case "pingBackend": return backend;
                            case "canvas": return canvas;
                            default: throw new AssertionError("Unexpected host call " + method.getName());
                        }
                    });
        }
    }
}
