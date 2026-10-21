package comet.mc1710.mixin;

import comet.core.CometClient;
import comet.core.mod.ToggleSprint;
import comet.mc1710.Comet;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public abstract class MovementMixin {
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void comet$movement(CallbackInfo info) {
        CometClient client = Comet.client();
        ToggleSprint toggle = client.toggleSprint();
        if (client.mods().isEnabled(toggle)) {
            toggle.movement();
        }
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;setSprinting(Z)V", ordinal = 1))
    private static void comet$sprint(EntityPlayerSP player, boolean sprinting) {
        CometClient client = Comet.client();
        ToggleSprint toggle = client.toggleSprint();
        if (!client.mods().isEnabled(toggle) || toggle.allowsSprint(sprinting)) {
            player.setSprinting(sprinting);
        }
    }
}
