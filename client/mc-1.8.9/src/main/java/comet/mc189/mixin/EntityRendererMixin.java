package comet.mc189.mixin;

import comet.core.anim.HurtShake;
import comet.core.mod.OldAnimations;
import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Unique
    private float comet$partialTicks;

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void comet$remember(float partialTicks, CallbackInfo info) {
        comet$partialTicks = partialTicks;
    }

    @ModifyArg(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 4), index = 1)
    private float comet$eyeHeight(float y) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        OldAnimations visuals = Comet.client().oldAnimations();
        if (view == null || view != mc.thePlayer || !Comet.client().mods().isEnabled(visuals)) {
            return y;
        }
        float eye = visuals.eyeHeight(view.getEyeHeight(), comet$partialTicks,
                mc.gameSettings.keyBindSneak.isKeyDown(), view.isSneaking());
        if (((EntityLivingBase) view).isPlayerSleeping()) {
            eye += 1.0F;
        }
        return -eye;
    }

    @ModifyArg(method = "hurtCameraEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;sin(F)F"))
    private float comet$hurtCameraShake(float angle) {
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals) || !visuals.hurtCameraShake()) {
            return angle;
        }
        return HurtShake.remap(angle);
    }
}
