package comet.mc189.mixin;

import comet.mc189.Comet;
import comet.mc189.bridge.Canvas189;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void comet$hud(float partialTicks, CallbackInfo info) {
        Comet.client().renderHud(new Canvas189());
    }
}
