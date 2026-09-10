package comet.mc189.mixin;

import comet.core.ui.MainMenuScreen;
import comet.core.ui.PauseScreen;
import comet.core.ui.Screen;
import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.EnumAction;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public WorldClient theWorld;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void comet$tick(CallbackInfo info) {
        Comet.client().tick();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void comet$useSwing(CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.currentScreen != null || !mc.inGameHasFocus
                || !mc.gameSettings.keyBindAttack.isKeyDown() || player.getItemInUseCount() <= 0) {
            return;
        }
        EnumAction action = player.getItemInUse().getItemUseAction();
        if (!Comet.client().oldSwing(action == EnumAction.BLOCK)) {
            return;
        }
        int duration = player.isPotionActive(Potion.digSpeed) ? 6 - (1 + player.getActivePotionEffect(Potion.digSpeed).getAmplifier())
                : player.isPotionActive(Potion.digSlowdown) ? 6 + (1 + player.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2 : 6;
        if (!player.isSwingInProgress || player.swingProgressInt >= duration / 2 || player.swingProgressInt < 0) {
            player.swingProgressInt = -1;
            player.isSwingInProgress = true;
        }
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void comet$swap(GuiScreen screen, CallbackInfo info) {
        Screen replacement = null;
        if (screen instanceof GuiMainMenu || (screen == null && theWorld == null)) {
            replacement = new MainMenuScreen(Comet.client());
        } else if (screen instanceof GuiIngameMenu) {
            replacement = new PauseScreen(Comet.client());
        }
        if (replacement != null) {
            info.cancel();
            Comet.client().host().openScreen(replacement);
        }
    }
}
