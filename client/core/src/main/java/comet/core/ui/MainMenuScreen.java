package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.VanillaScreen;
import comet.core.text.Text;

public final class MainMenuScreen extends Screen {
    private static final int WORDMARK_WIDTH = 120;
    private static final int COLUMN = 200;
    private static final int ROW = 24;
    private static final int GAP = 6;
    private final CometClient client;
    private final Button singleplayer = new Button("Singleplayer", COLUMN, ROW);
    private final Button multiplayer = new Button("Multiplayer", COLUMN, ROW);
    private final Button options = new Button("Options", (COLUMN - GAP) / 2, ROW);
    private final Button quit = new Button("Quit", (COLUMN - GAP) / 2, ROW);
    private final Button[] buttons = {singleplayer, multiplayer, options, quit};

    public MainMenuScreen(CometClient client) {
        this.client = client;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        int left = (width - COLUMN) / 2;
        int top = (int) (height * 0.30F) + Brand.height(WORDMARK_WIDTH) / 2 + 16;
        singleplayer.place(left, top);
        multiplayer.place(left, top + ROW + GAP);
        options.place(left, top + (ROW + GAP) * 2);
        quit.place(left + COLUMN - quit.width, top + (ROW + GAP) * 2);
    }

    @Override
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        Backdrop.get().draw(canvas);
        Brand.wordmark(canvas, width / 2, (int) (height * 0.30F), WORDMARK_WIDTH);
        for (Button button : buttons) {
            button.draw(canvas, mouseX, mouseY);
        }
        float footerY = height - 6 - Text.height(canvas, "Comet", 7);
        Text.draw(canvas, "Comet", 6, footerY, 7, 0x80FFFFFF);
        String version = "Minecraft " + client.host().version();
        Text.draw(canvas, version, width - 6 - Text.width(canvas, version, 7), footerY, 7, 0x80FFFFFF);
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (singleplayer.contains(mouseX, mouseY)) {
            client.host().open(VanillaScreen.WORLDS);
        } else if (multiplayer.contains(mouseX, mouseY)) {
            client.host().open(VanillaScreen.SERVERS);
        } else if (options.contains(mouseX, mouseY)) {
            client.host().open(VanillaScreen.OPTIONS);
        } else if (quit.contains(mouseX, mouseY)) {
            client.host().quitGame();
        }
    }
}
