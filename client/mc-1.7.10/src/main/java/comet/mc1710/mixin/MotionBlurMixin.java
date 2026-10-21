package comet.mc1710.mixin;

import comet.mc1710.Comet;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MotionBlurMixin {
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void comet$blurFrame(float partialTicks, CallbackInfo info) {
        Comet.client().beginBlurFrame();
    }

    @Inject(method = "orientCamera", at = @At("RETURN"))
    private void comet$camera(float partialTicks, CallbackInfo info) {
        Comet.client().captureCamera();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void comet$motionBlur(float partialTicks, long nanoTime, CallbackInfo info) {
        Comet.client().motionBlur(false);
    }

    @Inject(method = "updateCameraAndRender", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderEndNanoTime:J", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void comet$postEffectBlur(float partialTicks, CallbackInfo info) {
        Comet.client().motionBlur(true);
    }
}
