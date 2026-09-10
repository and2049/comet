package comet.core.bridge;

import comet.core.ui.Screen;
import java.io.File;

public interface GameHost {
    boolean inWorld();

    boolean screenOpen();

    void openScreen(Screen screen);

    void closeScreen();

    boolean keyDown(int key);

    int controlKey(Control control);

    String keyName(int key);

    double[] position();

    int ping();

    int fps();

    File gameDirectory();

    void blur(boolean enabled);

    Canvas canvas();

    String version();

    void open(VanillaScreen kind);

    void quitGame();

    boolean singleplayer();

    void leaveWorld();

    int sprintKey();

    int sneakKey();

    boolean sprinting();

    void setSprinting(boolean sprinting);
}
