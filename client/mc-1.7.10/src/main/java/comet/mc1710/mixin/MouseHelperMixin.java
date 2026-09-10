package comet.mc1710.mixin;

import comet.core.mod.GlobalSetting;
import comet.mc1710.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHelper.class)
public abstract class MouseHelperMixin {
    @Shadow public int deltaX;
    @Shadow public int deltaY;

    @Inject(method = "mouseXYChange", at = @At("TAIL"))
    private void comet$rawMouse(CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        int[] movement = Comet.client().rawMouse().read(Comet.client().mods().settings().global(GlobalSetting.RAW_MOUSE_INPUT),
                Display.isActive() && Mouse.isGrabbed() && mc.inGameHasFocus && mc.currentScreen == null);
        if (movement != null) {
            deltaX = movement[0];
            deltaY = movement[1];
        }
    }

    @Inject(method = {"grabMouseCursor", "ungrabMouseCursor"}, at = @At("HEAD"))
    private void comet$resetMouse(CallbackInfo info) {
        Comet.client().rawMouse().reset();
    }
}
