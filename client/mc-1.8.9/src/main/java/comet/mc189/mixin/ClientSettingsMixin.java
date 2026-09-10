package comet.mc189.mixin;

import comet.core.mod.GlobalSetting;
import comet.core.platform.BorderlessWindow;
import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientSettingsMixin {
    @Shadow private boolean fullscreen;
    @Shadow private int tempDisplayWidth;
    @Shadow private int tempDisplayHeight;
    @Shadow protected abstract void resize(int width, int height);
    @Unique private boolean comet$requested;
    @Unique private boolean comet$failed;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void comet$windowSetting(CallbackInfo info) {
        boolean requested = Comet.client().mods().settings().global(GlobalSetting.BORDERLESS_FULLSCREEN);
        if (requested != comet$requested) {
            comet$requested = requested;
            comet$failed = false;
        }
        if (fullscreen && requested != Comet.client().borderless().active() && !comet$failed) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.toggleFullscreen();
            mc.toggleFullscreen();
        }
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void comet$fullscreen(CallbackInfo info) {
        BorderlessWindow window = Comet.client().borderless();
        if (!window.active() && (fullscreen || comet$failed || !Comet.client().mods().settings().global(GlobalSetting.BORDERLESS_FULLSCREEN))) {
            return;
        }
        if (!fullscreen) {
            tempDisplayWidth = Display.getWidth();
            tempDisplayHeight = Display.getHeight();
        }
        if (!window.transition()) {
            comet$failed = true;
            if (window.active()) {
                info.cancel();
            }
            return;
        }
        fullscreen = window.active();
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.fullScreen = fullscreen;
        resize(Display.getWidth(), Display.getHeight());
        Display.setVSyncEnabled(mc.gameSettings.enableVsync);
        Display.update();
        Comet.client().rawMouse().reset();
        info.cancel();
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    private void comet$hotbarScroll(InventoryPlayer inventory, int direction) {
        if (!Comet.client().mods().settings().global(GlobalSetting.DISABLE_HOTBAR_SCROLLING)) {
            inventory.changeCurrentItem(direction);
        }
    }
}
