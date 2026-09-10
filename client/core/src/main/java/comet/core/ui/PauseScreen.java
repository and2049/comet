package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.bridge.VanillaScreen;

public final class PauseScreen extends Screen {
    private static final int WORDMARK_WIDTH = 92;
    private static final int COLUMN = 200;
    private static final int ROW = 24;
    private static final int GAP = 6;
    private final CometClient client;
    private final Button back = new Button("Back to game", COLUMN, ROW);
    private final Button mods = new Button("Mods", COLUMN, ROW);
    private final Button options = new Button("Options", COLUMN, ROW);
    private final Button leave;
    private final Button[] buttons;

    public PauseScreen(CometClient client) {
        this.client = client;
        leave = new Button(client.host().singleplayer() ? "Save and quit to title" : "Disconnect", COLUMN, ROW);
        buttons = new Button[] {back, mods, options, leave};
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        int left = (width - COLUMN) / 2;
        int top = (int) (height * 0.24F) + Brand.height(WORDMARK_WIDTH) / 2 + 10;
        for (int index = 0; index < buttons.length; index++) {
            buttons[index].place(left, top + (ROW + GAP) * index);
        }
    }

    @Override
    public void opened() {
        client.host().blur(true);
    }

    @Override
    public void closed() {
        client.host().blur(false);
    }

    @Override
    public boolean pausesGame() {
        return true;
    }

    @Override
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(0, 0, width, height, 0x60000000);
        Brand.wordmark(canvas, width / 2, (int) (height * 0.24F), WORDMARK_WIDTH);
        for (Button button : buttons) {
            button.draw(canvas, mouseX, mouseY);
        }
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (back.contains(mouseX, mouseY)) {
            client.host().closeScreen();
        } else if (mods.contains(mouseX, mouseY)) {
            client.openMods();
        } else if (options.contains(mouseX, mouseY)) {
            client.host().open(VanillaScreen.OPTIONS);
        } else if (leave.contains(mouseX, mouseY)) {
            client.host().leaveWorld();
        }
    }

    @Override
    public boolean key(char character, int code) {
        if (code == Keys.ESCAPE) {
            client.host().closeScreen();
            return true;
        }
        return false;
    }
}
