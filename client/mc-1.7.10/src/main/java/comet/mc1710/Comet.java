package comet.mc1710;

import comet.core.CometClient;
import comet.mc1710.bridge.Host1710;

public final class Comet {
    private static CometClient client;

    private Comet() {
    }

    public static CometClient client() {
        if (client == null) {
            client = new CometClient(new Host1710());
        }
        return client;
    }
}
