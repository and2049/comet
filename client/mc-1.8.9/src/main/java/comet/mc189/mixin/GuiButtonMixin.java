package comet.mc189.mixin;

import comet.core.ui.Widgets;
import comet.mc189.bridge.Canvas189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiButton.class)
public abstract class GuiButtonMixin {
    @Shadow
    public boolean visible;

    @Shadow
    public boolean enabled;

    @Shadow
    protected boolean hovered;

    @Shadow
    public int xPosition;

    @Shadow
    public int yPosition;

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    public String displayString;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void comet$draw(Minecraft mc, int mouseX, int mouseY, CallbackInfo info) {
        info.cancel();
        if (!visible) {
            return;
        }
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        Widgets.button(new Canvas189(), xPosition, yPosition, width, height, displayString, hovered, enabled);
        mouseDragged(mc, mouseX, mouseY);
    }
}
