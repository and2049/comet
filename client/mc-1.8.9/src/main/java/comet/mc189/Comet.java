package comet.mc189;

import comet.core.CometClient;
import comet.mc189.bridge.Host189;

public final class Comet {
    private static CometClient client;

    private Comet() {
    }

    public static CometClient client() {
        if (client == null) {
            client = new CometClient(new Host189());
        }
        return client;
    }
}
