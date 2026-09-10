package comet.mc1710.bridge;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.VanillaScreen;
import comet.core.ui.Screen;
import comet.mc1710.mixin.MinecraftAccessor;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

public final class Host1710 implements GameHost {
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
        return MinecraftAccessor.comet$debugFPS();
    }

    @Override
    public File gameDirectory() {
        return mc.mcDataDir;
    }

    @Override
    public void blur(boolean enabled) {
        if (!OpenGlHelper.shadersSupported) {
            return;
        }
        try {
            if (enabled) {
                mc.entityRenderer.deactivateShader();
                ShaderGroup group = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), BLUR);
                group.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                mc.entityRenderer.theShaderGroup = group;
            } else if (mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.deactivateShader();
            }
        } catch (Exception error) {
            System.err.println("[Comet] Blur unavailable: " + error.getMessage());
        }
    }

    @Override
    public Canvas canvas() {
        return new Canvas1710();
    }

    @Override
    public String version() {
        return "1.7.10";
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
