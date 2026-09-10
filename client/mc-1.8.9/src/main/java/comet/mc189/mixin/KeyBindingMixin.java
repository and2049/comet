package comet.mc189.mixin;

import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "setKeyBindState", at = @At("HEAD"))
    private static void comet$state(int keyCode, boolean pressed, CallbackInfo info) {
        Comet.client().keyState(keyCode, pressed);
    }

    @Inject(method = "unPressAllKeys", at = @At("HEAD"))
    private static void comet$release(CallbackInfo info) {
        Comet.client().releaseKeys();
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private void comet$held(CallbackInfoReturnable<Boolean> info) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        if (settings == null) {
            return;
        }
        Object self = this;
        boolean held = self == settings.keyBindSprint ? Comet.client().holdsSprint() : self == settings.keyBindSneak && Comet.client().holdsSneak();
        if (held) {
            info.setReturnValue(true);
        }
    }
}
