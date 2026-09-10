package comet.mc189.bridge;

import comet.core.bridge.Canvas;
import comet.core.bridge.Control;
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
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.network.NetworkPlayerInfo;

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
        if (!Display.isActive()) return false;
        return key < 0 ? Mouse.isCreated() && key + 100 >= 0 && key + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(key + 100)
                : key > 0 && key < Keyboard.KEYBOARD_SIZE && Keyboard.isCreated() && Keyboard.isKeyDown(key);
    }

    public int controlKey(Control control) {
        KeyBinding[] bindings = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindAttack, mc.gameSettings.keyBindUseItem};
        return bindings[control.ordinal()].getKeyCode();
    }

    public String keyName(int key) {
        if (key == -100) return "LMB";
        if (key == -99) return "RMB";
        if (key < 0 && key >= -100) return "M" + (key + 101);
        return key > 0 && key < Keyboard.KEYBOARD_SIZE ? Keyboard.getKeyName(key) : "--";
    }

    public double[] position() {
        return mc.thePlayer == null ? null : new double[] {mc.thePlayer.posX, mc.thePlayer.getEntityBoundingBox().minY, mc.thePlayer.posZ};
    }

    public int ping() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return -1;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
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

    @Override
    public int sprintKey() {
        return mc.gameSettings.keyBindSprint.getKeyCode();
    }

    @Override
    public int sneakKey() {
        return mc.gameSettings.keyBindSneak.getKeyCode();
    }

    @Override
    public boolean sprinting() {
        return mc.thePlayer != null && mc.thePlayer.isSprinting();
    }

    @Override
    public void setSprinting(boolean sprinting) {
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(sprinting);
        }
    }
}
