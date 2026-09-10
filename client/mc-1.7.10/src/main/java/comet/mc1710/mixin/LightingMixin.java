package comet.mc1710.mixin;

import comet.mc1710.Comet;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class LightingMixin {
    @Shadow private int[] lightmapColors;

    @Inject(method = "updateLightmap", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;updateDynamicTexture()V"))
    private void comet$lightmap(float partialTicks, CallbackInfo info) {
        Comet.client().lightmap(lightmapColors);
    }
}
