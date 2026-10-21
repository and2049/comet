package comet.mc189.bridge;

import comet.core.bridge.CoordinateBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.biome.BiomeGenBase;

public final class Coordinates189 implements CoordinateBackend {
    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public double[] position() {
        if (mc.thePlayer == null) {
            return null;
        }
        return new double[] {mc.thePlayer.posX, mc.thePlayer.getEntityBoundingBox().minY, mc.thePlayer.posZ};
    }

    @Override
    public float cameraYaw() {
        Entity view = mc.getRenderViewEntity();
        return view == null ? playerYaw() : view.rotationYaw;
    }

    @Override
    public float playerYaw() {
        return mc.thePlayer == null ? Float.NaN : mc.thePlayer.rotationYaw;
    }

    @Override
    public int[] renderCounts() {
        return mc.renderGlobal == null ? null : CoordinateBackend.renderCounts(mc.renderGlobal.getDebugInfoRenders());
    }

    @Override
    public String biomeName() {
        BiomeGenBase biome = biome();
        return biome == null ? null : biome.biomeName;
    }

    @Override
    public boolean reducedDebugInfo() {
        if (mc.thePlayer != null && mc.thePlayer.hasReducedDebug()) {
            return true;
        }
        return mc.gameSettings != null && mc.gameSettings.reducedDebugInfo;
    }

    @Override
    public boolean chatOpen() {
        GuiNewChat chat = chat();
        return chat != null && chat.getChatOpen();
    }

    @Override
    public void copy(String text) {
        GuiScreen.setClipboardString(text);
    }

    @Override
    public void notify(String message) {
        GuiNewChat chat = chat();
        if (chat != null) {
            chat.printChatMessage(new ChatComponentText(message));
        }
    }

    private GuiNewChat chat() {
        return mc.ingameGUI == null ? null : mc.ingameGUI.getChatGUI();
    }

    private BiomeGenBase biome() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        return mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer.posX, 0.0D, mc.thePlayer.posZ));
    }
}
