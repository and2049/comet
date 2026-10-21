package comet.mc189.mixin;

import comet.core.CometClient;
import comet.core.mod.GlobalSetting;
import comet.core.mod.ModSettings;
import comet.core.mod.PingCounter;
import comet.mc189.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class NametagMixin {
    private static final float SCALE = 0.016666668F * 1.6F;
    private static final float LINE = 0.25F;
    private static final float SCORE_LINE = 9.0F * 1.15F * SCALE;
    private static final int BELOW_NAME = 2;
    private static final int QUADS = 7;

    @Shadow
    protected abstract boolean canRenderName(EntityLivingBase entity);

    @Inject(method = "renderName", at = @At("RETURN"))
    private void comet$pingNametag(EntityLivingBase entity, double x, double y, double z, CallbackInfo info) {
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        if (!(entity instanceof EntityPlayer) || !canRenderName(entity)) {
            return;
        }
        boolean sneaking = entity.isSneaking();
        float range = sneaking ? 32.0F : 64.0F;
        if (renderManager.livingPlayer == null
                || entity.getDistanceSqToEntity(renderManager.livingPlayer) >= (double) (range * range)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        CometClient client = Comet.client();
        PingCounter ping = client.pingCounter();
        if (!client.mods().isEnabled(ping)) {
            return;
        }
        boolean self = entity == mc.thePlayer;
        String line = ping.nametagLine(entity.getUniqueID(), entity.getName(), self,
                entity.getDisplayName().getFormattedText());
        if (line == null) {
            return;
        }
        float nameBase = sneaking ? -LINE : 0.0F;
        float top = sneaking ? nameBase : comet$scored((EntityPlayer) entity) ? SCORE_LINE : 0.0F;
        float offset = ping.nametagAbove() ? top + LINE : nameBase - LINE;
        comet$draw(line, entity.height, x, y, z, offset, sneaking);
    }

    private boolean comet$scored(EntityPlayer player) {
        Scoreboard scoreboard = player.getWorldScoreboard();
        if (scoreboard == null) {
            return false;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(BELOW_NAME);
        return objective != null && scoreboard.getValueFromObjective(player.getName(), objective) != null;
    }

    private void comet$draw(String line, float height, double x, double y, double z, float offset, boolean sneaking) {
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        ModSettings settings = Comet.client().mods().settings();
        boolean background = settings.global(GlobalSetting.HUD_BACKGROUND);
        boolean shadow = settings.global(GlobalSetting.TEXT_SHADOW);
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int width = font.getStringWidth(line);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        FloatBuffer normal = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glGetFloat(GL11.GL_CURRENT_NORMAL, normal);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        boolean quads = false;
        try {
            GlStateManager.translate((float) x, (float) y + height + 0.5F + offset, (float) z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-SCALE, -SCALE, SCALE);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            if (!sneaking) {
                GlStateManager.disableDepth();
            }
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (background) {
                quads = true;
                comet$background(width);
                quads = false;
            }
            if (shadow) {
                font.drawStringWithShadow(line, -width / 2, 0, -1);
            } else {
                font.drawString(line, -width / 2, 0, -1);
            }
        } finally {
            if (quads) {
                try {
                    Tessellator.getInstance().draw();
                } catch (RuntimeException ignored) {
                }
            }
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(mask);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.bindTexture(binding);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            GL11.glNormal3f(normal.get(0), normal.get(1), normal.get(2));
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrix);
        }
    }

    private void comet$background(int width) {
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(-width / 2 - 1, -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        renderer.pos(-width / 2 - 1, 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        renderer.pos(width / 2 + 1, 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        renderer.pos(width / 2 + 1, -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }
}
