package comet.mc1710.mixin;

import comet.mc1710.Comet;
import comet.mc1710.bridge.Canvas1710;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void comet$hud(float partialTicks, boolean hasScreen, int mouseX, int mouseY, CallbackInfo info) {
        Comet.client().renderHud(new Canvas1710());
    }
}
