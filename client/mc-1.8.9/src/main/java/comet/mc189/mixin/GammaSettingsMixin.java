package comet.mc189.mixin;

import comet.core.bridge.LightingBackend;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameSettings.class)
public abstract class GammaSettingsMixin implements LightingBackend {
    @Shadow
    public float gammaSetting;
    @Unique
    private boolean comet$gammaOverridden;
    @Unique
    private float comet$savedGamma;
    @Unique
    private boolean comet$gammaEdited;

    @Override
    public float gamma() {
        return gammaSetting;
    }

    @Override
    public void setGammaOverride(float gamma) {
        if (!comet$gammaOverridden) {
            comet$gammaOverridden = true;
            comet$savedGamma = gammaSetting;
        }
        gammaSetting = gamma;
    }

    @Override
    public void removeGammaOverride() {
        if (comet$gammaOverridden) {
            comet$gammaOverridden = false;
            gammaSetting = comet$savedGamma;
        }
    }

    @Override
    public boolean consumeUserGammaEdit() {
        boolean edited = comet$gammaEdited;
        comet$gammaEdited = false;
        return edited;
    }

    @Inject(method = "setOptionFloatValue", at = @At("HEAD"))
    private void comet$gammaOption(GameSettings.Options option, float value, CallbackInfo info) {
        if (option == GameSettings.Options.GAMMA && comet$gammaOverridden) {
            comet$savedGamma = value;
            comet$gammaEdited = true;
        }
    }

    @Inject(method = "saveOptions", at = @At("HEAD"))
    private void comet$beforeSave(CallbackInfo info) {
        comet$swapSavedGamma();
    }

    @Inject(method = "saveOptions", at = @At("RETURN"))
    private void comet$afterSave(CallbackInfo info) {
        comet$swapSavedGamma();
    }

    @Inject(method = "loadOptions", at = @At("HEAD"))
    private void comet$beforeLoad(CallbackInfo info) {
        comet$swapSavedGamma();
    }

    @Inject(method = "loadOptions", at = @At("RETURN"))
    private void comet$afterLoad(CallbackInfo info) {
        comet$swapSavedGamma();
    }

    @Unique
    private void comet$swapSavedGamma() {
        if (!comet$gammaOverridden) {
            return;
        }
        float active = gammaSetting;
        gammaSetting = comet$savedGamma;
        comet$savedGamma = active;
    }
}
