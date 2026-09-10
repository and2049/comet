package comet.mc1710.mixin;

import comet.core.ui.Backdrop;
import comet.mc1710.bridge.Canvas1710;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class GuiScreenMixin {
    @Shadow
    public Minecraft mc;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void comet$background(int tint, CallbackInfo info) {
        info.cancel();
        Canvas1710 canvas = new Canvas1710();
        if (mc.theWorld == null) {
            Backdrop.get().draw(canvas);
        } else {
            canvas.fill(0, 0, width, height, 0xA0101010);
        }
    }
}
