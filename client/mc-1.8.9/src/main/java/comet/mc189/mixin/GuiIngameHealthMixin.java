package comet.mc189.mixin;

import comet.core.anim.HealthBar;
import comet.core.mod.OldAnimations;
import comet.mc189.Comet;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiIngame.class)
public abstract class GuiIngameHealthMixin {
    @ModifyVariable(method = "renderPlayerStats", at = @At(value = "STORE", ordinal = 0), ordinal = 1)
    private int comet$healthThreshold(int threshold) {
        return HealthBar.threshold(threshold, comet$enabled());
    }

    @Unique
    private static boolean comet$enabled() {
        OldAnimations visuals = Comet.client().oldAnimations();
        return Comet.client().mods().isEnabled(visuals) && visuals.healthAnimation();
    }
}
