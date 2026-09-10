package comet.mc1710.bridge;

import comet.core.ui.Screen;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

public final class CometGuiScreen extends GuiScreen {
    private final Screen screen;

    public CometGuiScreen(Screen screen) {
        this.screen = screen;
    }

    @Override
    public void initGui() {
        screen.resize(width, height);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        screen.draw(new Canvas1710(), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        screen.mouseDown(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        screen.mouseUp(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long duration) {
        screen.mouseDrag(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            screen.scroll(mouseX, mouseY, Integer.signum(wheel));
        }
    }

    @Override
    protected void keyTyped(char character, int code) {
        screen.key(character, code);
    }

    @Override
    public void onGuiClosed() {
        screen.closed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return screen.pausesGame();
    }
}
