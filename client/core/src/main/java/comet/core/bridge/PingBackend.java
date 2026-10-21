package comet.core.bridge;

import java.util.UUID;

public interface PingBackend {
    PingBackend NONE = new PingBackend() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void tick() {
        }

        @Override
        public int sample() {
            return -1;
        }

        @Override
        public int tabLatency(UUID player, String playerName) {
            return -1;
        }

        @Override
        public void reset() {
        }
    };

    default boolean available() {
        return true;
    }

    default int session() {
        return 0;
    }

    default int sequence() {
        return 0;
    }

    void refresh();

    void tick();

    int sample();

    int tabLatency(UUID player, String playerName);

    void reset();
}
