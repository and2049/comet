package comet.mc189.bridge;

import comet.core.bridge.PingBackend;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.network.OldServerPinger;

public final class Ping189 implements PingBackend {
    private static final long TIMEOUT_MS = 4000L;
    private static final String NAME = "Comet ping";
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ArrayBlockingQueue<Setup> mailbox = new ArrayBlockingQueue<Setup>(1);
    private final AtomicReference<Request> handoff = new AtomicReference<Request>();
    private final AtomicBoolean setting = new AtomicBoolean();
    private volatile int session;
    private int sequence;
    private int sample = -1;
    private Request active;
    private Thread worker;
    private String server;

    @Override
    public void refresh() {
        if (server == null || active != null || handoff.get() != null) {
            return;
        }
        start();
        if (!setting.compareAndSet(false, true)) {
            return;
        }
        if (!mailbox.offer(new Setup(session, server))) {
            setting.set(false);
        }
    }

    @Override
    public void tick() {
        String current = server();
        if (!Objects.equals(current, server)) {
            server = current;
            reset();
        }
        if (server == null) {
            return;
        }
        Request request = handoff.getAndSet(null);
        if (request != null) {
            if (request.session == session) {
                active = request;
            } else {
                close(request);
            }
        }
        if (active == null) {
            return;
        }
        try {
            active.pinger.pingPendingNetworks();
        } catch (RuntimeException error) {
            close(active);
            active = null;
            return;
        }
        long ping = active.data.pingToServer;
        if (ping >= 0) {
            sample = (int) Math.min(Integer.MAX_VALUE, ping);
            sequence++;
            close(active);
            active = null;
        } else if (now() - active.startedAt > TIMEOUT_MS) {
            close(active);
            active = null;
        }
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
    public int sample() {
        return sample;
    }

    @Override
    public int tabLatency(UUID player, String playerName) {
        if (player == null || mc.getNetHandler() == null) {
            return -1;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player);
        return info == null ? -1 : Math.max(0, info.getResponseTime());
    }

    @Override
    public void reset() {
        session++;
        sample = -1;
        close(active);
        active = null;
        close(handoff.getAndSet(null));
    }

    private synchronized void start() {
        if (worker != null) {
            return;
        }
        worker = new Thread(this::setup, NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private void setup() {
        while (true) {
            Setup next;
            try {
                next = mailbox.take();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (next.session != session) {
                    continue;
                }
                OldServerPinger pinger = new OldServerPinger();
                ServerData data = new ServerData(NAME, next.server, false);
                try {
                    pinger.ping(data);
                } catch (Exception error) {
                    close(pinger);
                    continue;
                }
                Request request = new Request(next.session, pinger, data);
                if (next.session != session) {
                    close(pinger);
                    continue;
                }
                if (!handoff.compareAndSet(null, request)) {
                    close(pinger);
                    continue;
                }
                if (next.session != session && handoff.compareAndSet(request, null)) {
                    close(pinger);
                }
            } finally {
                setting.set(false);
            }
        }
    }

    private void close(Request request) {
        if (request != null) {
            close(request.pinger);
        }
    }

    private void close(OldServerPinger pinger) {
        try {
            pinger.clearPendingNetworks();
        } catch (RuntimeException ignored) {
        }
    }

    private String server() {
        if (mc.isIntegratedServerRunning()) {
            return null;
        }
        ServerData data = mc.getCurrentServerData();
        String address = data == null ? null : data.serverIP;
        return address == null || address.trim().isEmpty() ? null : address;
    }

    private static long now() {
        return System.nanoTime() / 1000000L;
    }

    private static final class Setup {
        final int session;
        final String server;

        Setup(int session, String server) {
            this.session = session;
            this.server = server;
        }
    }

    private static final class Request {
        final int session;
        final OldServerPinger pinger;
        final ServerData data;
        final long startedAt;

        Request(int session, OldServerPinger pinger, ServerData data) {
            this.session = session;
            this.pinger = pinger;
            this.data = data;
            this.startedAt = now();
        }
    }
}
