package comet.mc189.mixin;

import comet.core.anim.Glint;
import comet.core.mod.OldAnimations;
import comet.mc189.Comet;
import comet.mc189.render.FirstPersonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class RenderItemMixin {
    private static final int GL_EQUAL = 514;
    private static final int GL_SRC_COLOR = 768;
    private static final int GL_ONE = 1;
    private static final int GL_ZERO = 0;
    private static final int GL_TEXTURE = 5890;

    @Shadow
    @Final
    private static ResourceLocation RES_ITEM_GLINT;

    @Shadow
    private TextureManager textureManager;

    @Shadow
    private void renderModel(IBakedModel model, int color) {
    }

    @Unique
    private boolean comet$gui;

    @Redirect(method = "renderItemAndEffectIntoGUI", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItemIntoGUI(Lnet/minecraft/item/ItemStack;II)V"))
    private void comet$guiContext(RenderItem renderer, ItemStack stack, int x, int y) {
        boolean previous = comet$gui;
        comet$gui = true;
        try {
            renderer.renderItemIntoGUI(stack, x, y);
        } finally {
            comet$gui = previous;
        }
    }

    @Inject(method = "renderEffect", at = @At("HEAD"), cancellable = true)
    private void comet$oldGlint(IBakedModel model, CallbackInfo info) {
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals) || !visuals.glintAnimation()) {
            return;
        }
        info.cancel();
        boolean gui = comet$gui;
        float scale = Glint.scale(gui);
        int color = Glint.color(gui);
        long millis = Minecraft.getSystemTime();
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean previousTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int previousTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousBlendSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(GL_SRC_COLOR, GL_ONE);
        textureManager.bindTexture(RES_ITEM_GLINT);
        GlStateManager.matrixMode(GL_TEXTURE);
        GlStateManager.tryBlendFuncSeparate(GL_SRC_COLOR, GL_ONE, GL_ONE, GL_ZERO);
        GlStateManager.enableTexture2D();
        try {
            comet$glintPass(model, millis, gui, scale, color, true);
            comet$glintPass(model, millis, gui, scale, color, false);
        } finally {
            GlStateManager.matrixMode(previousMatrixMode);
            GlStateManager.depthFunc(previousDepthFunc);
            GlStateManager.depthMask(previousDepthMask);
            if (previousLighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (previousTexture) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.tryBlendFuncSeparate(previousBlendSrc, previousBlendDst, previousBlendSrcAlpha,
                    previousBlendDstAlpha);
            GlStateManager.bindTexture(previousTextureId);
        }
    }

    @Unique
    private void comet$glintPass(IBakedModel model, long millis, boolean gui, float scale, int color, boolean first) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(scale, scale, scale);
            if (first) {
                GlStateManager.translate(Glint.firstScroll(millis, gui), 0.0F, 0.0F);
                GlStateManager.rotate(Glint.FIRST_ANGLE, 0.0F, 0.0F, 1.0F);
            } else {
                float scroll = Glint.secondScroll(millis, gui);
                GlStateManager.translate(Glint.secondOffset(scroll, gui), 0.0F, 0.0F);
                GlStateManager.rotate(Glint.secondAngle(gui), 0.0F, 0.0F, 1.0F);
            }
            renderModel(model, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Redirect(method = "renderItemModelTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms;applyTransform(Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"))
    private void comet$suppressModelTransform(ItemCameraTransforms transforms,
            ItemCameraTransforms.TransformType type) {
        if (FirstPersonState.suppressed()) {
            return;
        }
        transforms.applyTransform(type);
    }
}
