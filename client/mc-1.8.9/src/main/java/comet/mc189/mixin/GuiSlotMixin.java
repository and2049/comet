package comet.mc189.mixin;

import comet.mc189.bridge.Canvas189;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSlot.class)
public abstract class GuiSlotMixin {
    @Shadow
    protected int left;

    @Shadow
    protected int width;

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void comet$overlay(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo info) {
        info.cancel();
        new Canvas189().fill(left, startY, width, endY - startY, 0xCC101010);
    }
}
