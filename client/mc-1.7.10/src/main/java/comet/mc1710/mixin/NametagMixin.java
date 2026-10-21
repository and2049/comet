package comet.mc1710.mixin;

import comet.core.CometClient;
import comet.core.mod.GlobalSetting;
import comet.core.mod.ModSettings;
import comet.core.mod.PingCounter;
import comet.mc1710.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.opengl.GL11;
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
    private static final float SLEEPING = 1.5F;
    private static final int BELOW_NAME = 2;

    @Shadow
    protected abstract boolean canRenderName(EntityLivingBase entity);

    @Inject(method = "passSpecialRender", at = @At("RETURN"))
    private void comet$pingNametag(EntityLivingBase entity, double x, double y, double z, CallbackInfo info) {
        RenderManager renderManager = RenderManager.instance;
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
        String line = ping.nametagLine(entity.getUniqueID(), entity.getCommandSenderName(), self,
                entity.getFormattedCommandSenderName().getFormattedText());
        if (line == null) {
            return;
        }
        float shift = entity.isPlayerSleeping() ? -SLEEPING : 0.0F;
        float nameBase = shift + (sneaking ? -LINE : 0.0F);
        float top = shift + (sneaking ? -LINE : comet$scored((EntityPlayer) entity) ? SCORE_LINE : 0.0F);
        float offset = ping.nametagAbove() ? top + LINE : nameBase - LINE;
        comet$draw(line, entity.height, x, y, z, offset, sneaking);
    }

    private boolean comet$scored(EntityPlayer player) {
        Scoreboard scoreboard = player.getWorldScoreboard();
        if (scoreboard == null) {
            return false;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(BELOW_NAME);
        return objective != null && scoreboard.getValueFromObjective(player.getCommandSenderName(), objective) != null;
    }

    private void comet$draw(String line, float height, double x, double y, double z, float offset, boolean sneaking) {
        RenderManager renderManager = RenderManager.instance;
        ModSettings settings = Comet.client().mods().settings();
        boolean background = settings.global(GlobalSetting.HUD_BACKGROUND);
        boolean shadow = settings.global(GlobalSetting.TEXT_SHADOW);
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int width = font.getStringWidth(line);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        boolean quads = false;
        try {
            GL11.glTranslatef((float) x, (float) y + height + 0.5F + offset, (float) z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GL11.glScalef(-SCALE, -SCALE, SCALE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDepthMask(false);
            if (!sneaking) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
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
                    Tessellator.instance.draw();
                } catch (RuntimeException ignored) {
                }
            }
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void comet$background(int width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.25F);
        tessellator.addVertex(-width / 2 - 1, -1.0D, 0.0D);
        tessellator.addVertex(-width / 2 - 1, 8.0D, 0.0D);
        tessellator.addVertex(width / 2 + 1, 8.0D, 0.0D);
        tessellator.addVertex(width / 2 + 1, -1.0D, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
