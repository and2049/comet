package comet.mc189.bridge;

import comet.core.bridge.MovementBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class Movement189 implements MovementBackend {
    public static final Movement189 INSTANCE = new Movement189();
    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public boolean creative() {
        return mc.thePlayer != null && mc.thePlayer.capabilities.isCreativeMode;
    }

    @Override
    public boolean spectator() {
        return mc.thePlayer != null && mc.thePlayer.isSpectator();
    }

    @Override
    public boolean flying() {
        return mc.thePlayer != null && mc.thePlayer.capabilities.isFlying;
    }

    @Override
    public boolean riding() {
        return mc.thePlayer != null && mc.thePlayer.isRiding();
    }

    @Override
    public boolean descending() {
        EntityPlayerSP player = mc.thePlayer;
        return player != null && !player.onGround && player.motionY < 0.0D;
    }

    @Override
    public boolean sneaking() {
        return mc.thePlayer != null && mc.thePlayer.isSneaking();
    }

    @Override
    public boolean sprintKeyHeld() {
        return mc.gameSettings != null && held(mc.gameSettings.keyBindSprint.getKeyCode());
    }

    @Override
    public boolean sneakInputHeld() {
        return mc.thePlayer != null && mc.thePlayer.movementInput.sneak;
    }

    @Override
    public boolean jumpInputHeld() {
        return mc.thePlayer != null && mc.thePlayer.movementInput.jump;
    }

    @Override
    public float flySpeed() {
        return mc.thePlayer == null ? 0F : mc.thePlayer.capabilities.getFlySpeed();
    }

    @Override
    public void setFlySpeed(float speed) {
        if (mc.thePlayer != null) {
            mc.thePlayer.capabilities.setFlySpeed(speed);
        }
    }

    @Override
    public void addMotionY(double amount) {
        if (mc.thePlayer != null) {
            mc.thePlayer.motionY += amount;
        }
    }

    @Override
    public boolean containerOpen() {
        return mc.currentScreen instanceof GuiContainer;
    }

    @Override
    public String serverAddress() {
        ServerData data = mc.getCurrentServerData();
        return data == null ? null : data.serverIP;
    }

    private static boolean held(int key) {
        if (!Display.isActive()) {
            return false;
        }
        if (key < 0) {
            return Mouse.isCreated() && key + 100 >= 0 && key + 100 < Mouse.getButtonCount()
                    && Mouse.isButtonDown(key + 100);
        }
        return key > 0 && key < Keyboard.KEYBOARD_SIZE && Keyboard.isCreated() && Keyboard.isKeyDown(key);
    }
}
