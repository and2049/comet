package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.PingBackend;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class PingCounter extends HudMod {
    public static final Option HUD = new Option("hud", "HUD", true);
    public static final Option NAMETAG = new Option("nametag", "Nametag", false);
    public static final Option NAMETAG_ABOVE = new Option("nametagAbove", "Above name", true);
    public static final Option ICON = new Option("icon", "Signal bars", false);
    public static final Option SPIKE_DETECTION = new Option("spikeDetection", "Spike detection", false);
    public static final Option SHOW_MS = new Option("showMs", "Milliseconds", true);
    public static final Option SHOW_PREFIX = new Option("showPrefix", "Prefix", false);
    public static final NumberOption SOURCE = new NumberOption("source", "Source", 0, 0, 1, "", "Player list", "Server status");
    public static final NumberOption MODE = new NumberOption("mode", "Mode", 0, 0, 1, "", "Latest", "Averaged");
    public static final NumberOption SAMPLES = new NumberOption("samples", "Samples", 3, 2, 20, "");
    public static final NumberOption INTERVAL = new NumberOption("interval", "Interval", 5, 1, 120, "s");
    public static final NumberOption MEDIUM_SPIKE = new NumberOption("mediumSpike", "Medium spike", 20, 1, 200, "%");
    public static final NumberOption LARGE_SPIKE = new NumberOption("largeSpike", "Large spike", 50, 1, 200, "%");
    public static final NumberOption SPIKE_EXPIRY = new NumberOption("spikeExpiry", "Spike expiry", 5000, 1000, 60000, " ms");
    public static final TextOption PREFIX = new TextOption("prefix", "Prefix text", "Ping: ", 10);

    private static final int SOURCE_PLAYER_LIST = 0;
    private static final int SOURCE_ACTIVE = 1;
    private static final int MODE_AVERAGED = 1;
    private static final long SECOND = 1000L;
    private static final int PADDING = 3;
    private static final int BARS = 5;
    private static final int BAR_SPACING = 2;
    private static final int BAR_BASE_HEIGHT = 2;
    private static final int ICON_GAP = 3;
    private static final int ICON_BASELINE = 11;
    private static final int ICON_WIDTH = BAR_SPACING * (BARS - 1) + 1;
    private static final int PREVIEW_PING = 42;
    private static final int FILLED = 0xFFFFFFFF;
    private static final int EMPTY = 0x60FFFFFF;
    private static final String OBFUSCATED = "\u00a7k";

    private final GameHost host;
    private final LongSupplier clock;
    private final List<Integer> batch = new ArrayList<Integer>();
    private PingBackend backend;
    private int source = -1;
    private int mode = -1;
    private int samples = -1;
    private int interval = -1;
    private int session = -1;
    private int sequence = -1;
    private long lastSample;
    private long nextSample;
    private boolean due = true;
    private int latest = -1;
    private int average = -1;
    private int spike;
    private long spikeTime = -1L;

    public PingCounter(GameHost host) {
        this(host, () -> System.nanoTime() / 1000000L);
    }

    PingCounter(GameHost host, LongSupplier clock) {
        this.host = host;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "ping";
    }

    @Override
    public String name() {
        return "Ping";
    }

    @Override
    public String description() {
        return "Shows your latency from the player list or a server status ping.";
    }

    @Override
    public float defaultY() {
        return 0.5F;
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(HUD, NAMETAG, NAMETAG_ABOVE, ICON, SPIKE_DETECTION, SHOW_MS, SHOW_PREFIX);
    }

    @Override
    public List<NumberOption> numbers() {
        return Arrays.asList(SOURCE, MODE, SAMPLES, INTERVAL, MEDIUM_SPIKE, LARGE_SPIKE, SPIKE_EXPIRY);
    }

    @Override
    public List<TextOption> texts() {
        return Collections.singletonList(PREFIX);
    }

    @Override
    public boolean visible() {
        return option(HUD);
    }

    public String label() {
        if (preview) {
            return format(PREVIEW_PING, false);
        }
        if (host.singleplayer()) {
            return "Local";
        }
        return format(displayValue(), true);
    }

    public String nametagLine(UUID player, String playerName, boolean self, String nametagText) {
        if (player == null || !option(NAMETAG)) {
            return null;
        }
        int value;
        if (self) {
            if (host.singleplayer()) {
                return null;
            }
            value = displayValue();
        } else {
            if (nametagText != null && nametagText.toLowerCase(Locale.ROOT).contains(OBFUSCATED)) {
                return null;
            }
            value = backend().tabLatency(player, playerName);
        }
        return value < 0 ? null : format(value, self);
    }

    public boolean nametagAbove() {
        return option(NAMETAG_ABOVE);
    }

    int reading() {
        return preview ? PREVIEW_PING : displayValue();
    }

    int spikeDelta() {
        return spikeTime < 0 ? 0 : spike;
    }

    boolean largeSpike() {
        return spikeTime >= 0 && Math.abs(spike) >= number(LARGE_SPIKE);
    }

    static int bars(int ping) {
        if (ping < 0) {
            return 0;
        }
        if (ping < 150) {
            return 5;
        }
        if (ping < 300) {
            return 4;
        }
        if (ping < 600) {
            return 3;
        }
        return ping < 1000 ? 2 : 1;
    }

    @Override
    public int width(Canvas canvas) {
        return canvas.textWidth(label()) + PADDING * 2 + (option(ICON) ? ICON_WIDTH + ICON_GAP : 0);
    }

    @Override
    public int height(Canvas canvas) {
        return canvas.textHeight() + PADDING * 2;
    }

    @Override
    public void render(Canvas canvas) {
        background(canvas, width(canvas), height(canvas));
        int x = PADDING;
        if (option(ICON)) {
            drawIcon(canvas, x);
            x += ICON_WIDTH + ICON_GAP;
        }
        text(canvas, label(), x, PADDING);
    }

    private void drawIcon(Canvas canvas, int x) {
        int filled = bars(reading());
        for (int index = 0; index < BARS; index++) {
            int barHeight = BAR_BASE_HEIGHT + index;
            canvas.fill(x + index * BAR_SPACING, ICON_BASELINE - barHeight, 1, barHeight, index < filled ? FILLED : EMPTY);
        }
    }

    @Override
    public void tick() {
        expire();
        boolean changed = number(MODE) != mode || number(SAMPLES) != samples;
        mode = number(MODE);
        samples = number(SAMPLES);
        if (number(INTERVAL) != interval) {
            interval = number(INTERVAL);
            nextSample = lastSample + interval * SECOND;
        }
        int selected = number(SOURCE);
        if (selected != source) {
            source = selected;
            reset();
            if (selected == SOURCE_ACTIVE) {
                PingBackend active = backend();
                session = active.session();
                sequence = active.sequence();
            }
        }
        if (selected == SOURCE_ACTIVE) {
            PingBackend active = backend();
            active.tick();
            if (active.session() != session) {
                session = active.session();
                changed = true;
            }
        }
        if (changed) {
            clearAggregate();
        }
        long now = clock.getAsLong();
        if (due || now - nextSample >= 0L) {
            due = false;
            lastSample = now;
            nextSample = now + interval * SECOND;
            sample();
        }
    }

    @Override
    public void reset() {
        clearAggregate();
        due = true;
        nextSample = 0L;
        if (backend != null) {
            backend.reset();
        }
    }

    private void clearAggregate() {
        batch.clear();
        latest = -1;
        average = -1;
        spike = 0;
        spikeTime = -1L;
    }

    private void sample() {
        if (number(SOURCE) == SOURCE_ACTIVE) {
            PingBackend active = backend();
            if (!active.available()) {
                return;
            }
            if (active.sequence() != sequence) {
                sequence = active.sequence();
                accept(active.sample());
            }
            active.refresh();
            return;
        }
        accept(host.ping());
    }

    private void accept(int value) {
        if (value < 0) {
            return;
        }
        spike(latest, value);
        latest = value;
        batch.add(value);
        if (batch.size() >= number(SAMPLES)) {
            long sum = 0L;
            for (int sample : batch) {
                sum += sample;
            }
            average = (int) (sum / batch.size());
            batch.clear();
        }
    }

    private void spike(int previous, int next) {
        expire();
        if (previous < 0 || next < 0) {
            return;
        }
        int delta = previous <= 0 ? 0 : (int) ((next - previous) / (float) previous * 100);
        if (Math.abs(delta) >= number(MEDIUM_SPIKE)) {
            spike = delta;
            spikeTime = clock.getAsLong();
        }
    }

    private void expire() {
        if (spikeTime >= 0 && clock.getAsLong() - spikeTime >= number(SPIKE_EXPIRY)) {
            spike = 0;
            spikeTime = -1L;
        }
    }

    private int displayValue() {
        if (number(MODE) == MODE_AVERAGED && average >= 0) {
            return average;
        }
        if (number(SOURCE) == SOURCE_ACTIVE) {
            PingBackend active = backend();
            return active.available() ? active.sample() : -1;
        }
        return host.ping();
    }

    private PingBackend backend() {
        if (backend == null) {
            PingBackend provided = host.pingBackend();
            backend = provided == null ? PingBackend.NONE : provided;
        }
        return backend;
    }

    private String format(int value, boolean withSpike) {
        StringBuilder label = new StringBuilder();
        if (option(SHOW_PREFIX)) {
            String prefix = text(PREFIX);
            if (!prefix.trim().isEmpty()) {
                label.append(prefix);
            }
        }
        label.append(value < 0 ? "--" : Integer.toString(value));
        if (option(SHOW_MS)) {
            label.append(" ms");
        }
        if (withSpike && option(SPIKE_DETECTION) && spikeTime >= 0) {
            label.append(spike >= 0 ? " (+" : " (").append(spike).append(largeSpike() ? "%!)" : "%)");
        }
        return label.toString();
    }
}
