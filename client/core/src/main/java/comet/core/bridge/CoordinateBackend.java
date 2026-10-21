package comet.core.bridge;

public interface CoordinateBackend {
    CoordinateBackend NONE = new None();

    double[] position();

    float cameraYaw();

    float playerYaw();

    int[] renderCounts();

    String biomeName();

    boolean reducedDebugInfo();

    boolean chatOpen();

    void copy(String text);

    void notify(String message);

    static int[] renderCounts(String debug) {
        if (debug == null) {
            return null;
        }
        int colon = debug.indexOf("C:");
        int slash = colon < 0 ? -1 : debug.indexOf('/', colon);
        if (slash < 0) {
            return null;
        }
        int end = slash + 1;
        while (end < debug.length() && Character.isDigit(debug.charAt(end))) {
            end++;
        }
        try {
            return new int[] {Integer.parseInt(debug.substring(colon + 2, slash).trim()),
                    Integer.parseInt(debug.substring(slash + 1, end))};
        } catch (NumberFormatException error) {
            return null;
        }
    }

    final class None implements CoordinateBackend {
        @Override
        public double[] position() {
            return null;
        }

        @Override
        public float cameraYaw() {
            return Float.NaN;
        }

        @Override
        public float playerYaw() {
            return Float.NaN;
        }

        @Override
        public int[] renderCounts() {
            return null;
        }

        @Override
        public String biomeName() {
            return null;
        }

        @Override
        public boolean reducedDebugInfo() {
            return false;
        }

        @Override
        public boolean chatOpen() {
            return false;
        }

        @Override
        public void copy(String text) {
        }

        @Override
        public void notify(String message) {
        }
    }
}
