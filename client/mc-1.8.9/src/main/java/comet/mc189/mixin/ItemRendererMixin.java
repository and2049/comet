package comet.mc189.mixin;

import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private Minecraft mc;

    @Shadow
    private ItemStack itemToRender;

    @Unique
    private float comet$partialTicks;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    private void comet$remember(float partialTicks, CallbackInfo info) {
        comet$partialTicks = partialTicks;
    }

    @ModifyVariable(method = "transformFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float comet$oldSwing(float swingProgress) {
        EntityPlayerSP player = mc.thePlayer;
        if (swingProgress != 0.0F || player == null || itemToRender == null || player.getItemInUseCount() <= 0) {
            return swingProgress;
        }
        if (!Comet.client().oldSwing(itemToRender.getItemUseAction() == EnumAction.BLOCK)) {
            return swingProgress;
        }
        return player.getSwingProgress(comet$partialTicks);
    }
}
