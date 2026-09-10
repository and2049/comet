package comet.mc189.bridge;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.VanillaScreen;
import comet.core.ui.Screen;
import comet.mc189.mixin.EntityRendererAccessor;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

public final class Host189 implements GameHost {
    private static final ResourceLocation BLUR = new ResourceLocation("shaders/post/blur.json");
    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public boolean inWorld() {
        return mc.theWorld != null;
    }

    @Override
    public boolean screenOpen() {
        return mc.currentScreen != null;
    }

    @Override
    public void openScreen(Screen screen) {
        mc.displayGuiScreen(new CometGuiScreen(screen));
        screen.opened();
    }

    @Override
    public void closeScreen() {
        mc.displayGuiScreen(null);
    }

    @Override
    public boolean keyDown(int key) {
        return Keyboard.isCreated() && Keyboard.isKeyDown(key);
    }

    @Override
    public int fps() {
        return Minecraft.getDebugFPS();
    }

    @Override
    public File gameDirectory() {
        return mc.mcDataDir;
    }

    @Override
    public void blur(boolean enabled) {
        try {
            if (enabled) {
                ((EntityRendererAccessor) mc.entityRenderer).comet$loadShader(BLUR);
            } else if (mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.stopUseShader();
            }
        } catch (RuntimeException error) {
            System.err.println("[Comet] Blur unavailable: " + error.getMessage());
        }
    }

    @Override
    public Canvas canvas() {
        return new Canvas189();
    }

    @Override
    public String version() {
        return mc.getVersion();
    }

    @Override
    public void open(VanillaScreen kind) {
        GuiScreen parent = mc.currentScreen;
        if (kind == VanillaScreen.WORLDS) {
            mc.displayGuiScreen(new GuiSelectWorld(parent));
        } else if (kind == VanillaScreen.SERVERS) {
            mc.displayGuiScreen(new GuiMultiplayer(parent));
        } else {
            mc.displayGuiScreen(new GuiOptions(parent, mc.gameSettings));
        }
    }

    @Override
    public void quitGame() {
        mc.shutdown();
    }

    @Override
    public boolean singleplayer() {
        return mc.isIntegratedServerRunning();
    }

    @Override
    public void leaveWorld() {
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
        }
        mc.loadWorld((WorldClient) null);
        mc.displayGuiScreen(new GuiMainMenu());
    }
}
