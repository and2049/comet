package comet.mc189.bridge;

import comet.core.bridge.Canvas;
import comet.core.bridge.Images;
import comet.core.bridge.Shapes;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class Canvas189 implements Canvas {
    private static final int SEGMENTS = 6;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ScaledResolution resolution = new ScaledResolution(mc);
    private WorldRenderer glyphs;

    @Override
    public int width() {
        return resolution.getScaledWidth();
    }

    @Override
    public int height() {
        return resolution.getScaledHeight();
    }

    @Override
    public void fill(int x, int y, int width, int height, int argb) {
        Gui.drawRect(x, y, x + width, y + height, argb);
    }

    private static void color(int argb) {
        GlStateManager.color(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    @Override
    public void roundedFill(int x, int y, int width, int height, int radius, int argb) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        color(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(6, DefaultVertexFormats.POSITION);
        renderer.pos(x + width / 2.0D, y + height / 2.0D, 0.0D).endVertex();
        double[] points = Shapes.roundedOutline(x, y, width, height, radius, SEGMENTS);
        for (int index = 0; index < points.length; index += 2) {
            renderer.pos(points[index], points[index + 1], 0.0D).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void gradient(int x, int y, int width, int height, int topArgb, int bottomArgb) {
        float topAlpha = (topArgb >>> 24) / 255.0F;
        float topRed = ((topArgb >> 16) & 0xFF) / 255.0F;
        float topGreen = ((topArgb >> 8) & 0xFF) / 255.0F;
        float topBlue = (topArgb & 0xFF) / 255.0F;
        float bottomAlpha = (bottomArgb >>> 24) / 255.0F;
        float bottomRed = ((bottomArgb >> 16) & 0xFF) / 255.0F;
        float bottomGreen = ((bottomArgb >> 8) & 0xFF) / 255.0F;
        float bottomBlue = (bottomArgb & 0xFF) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(x, y + height, 0.0D).color(bottomRed, bottomGreen, bottomBlue, bottomAlpha).endVertex();
        renderer.pos(x + width, y + height, 0.0D).color(bottomRed, bottomGreen, bottomBlue, bottomAlpha).endVertex();
        renderer.pos(x + width, y, 0.0D).color(topRed, topGreen, topBlue, topAlpha).endVertex();
        renderer.pos(x, y, 0.0D).color(topRed, topGreen, topBlue, topAlpha).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    @Override
    public void text(String value, int x, int y, int argb, boolean shadow) {
        if (shadow) {
            mc.fontRendererObj.drawStringWithShadow(value, x, y, argb);
        } else {
            mc.fontRendererObj.drawString(value, x, y, argb);
        }
    }

    @Override
    public int textWidth(String value) {
        return mc.fontRendererObj.getStringWidth(value);
    }

    @Override
    public int textHeight() {
        return mc.fontRendererObj.FONT_HEIGHT;
    }


    private static final Map<String, DynamicTexture> STREAMS = new HashMap<String, DynamicTexture>();
    private static final Map<String, int[]> SIZES = new HashMap<String, int[]>();

    @Override
    public void texture(String key, int width, int height, int[] argb) {
        DynamicTexture texture = STREAMS.get(key);
        if (texture == null) {
            texture = new DynamicTexture(width, height);
            mc.getTextureManager().loadTexture(new ResourceLocation(key), texture);
            STREAMS.put(key, texture);
            SIZES.put(key, new int[] {width, height});
            TEXTURES.put(key, new ResourceLocation(key));
        }
        System.arraycopy(argb, 0, texture.getTextureData(), 0, Math.min(argb.length, texture.getTextureData().length));
        texture.updateDynamicTexture();
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    @Override
    public int[] textureSize(String key) {
        int[] size = SIZES.get(key);
        return size == null ? new int[] {16, 9} : size;
    }

    @Override
    public void draw(String key, int x, int y, int width, int height, float alpha) {
        ResourceLocation location = TEXTURES.get(key);
        if (location != null) {
            quad(location, x, y, width, height, alpha);
        }
    }

    @Override
    public void image(String namespace, String path, int x, int y, int width, int height) {
        image(namespace, path, x, y, width, height, 1.0F);
    }

    @Override
    public void image(String namespace, String path, int x, int y, int width, int height, float alpha) {
        ResourceLocation location = texture(namespace, path);
        if (location != null) {
            quad(location, x, y, width, height, alpha);
        }
    }

    private void quad(ResourceLocation location, int x, int y, int width, int height, float alpha) {
        mc.getTextureManager().bindTexture(location);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        renderer.pos(x, y + height, 0.0D).tex(0.0D, 1.0D).endVertex();
        renderer.pos(x + width, y + height, 0.0D).tex(1.0D, 1.0D).endVertex();
        renderer.pos(x + width, y, 0.0D).tex(1.0D, 0.0D).endVertex();
        renderer.pos(x, y, 0.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void beginGlyphs(String key, BufferedImage atlas, int argb) {
        ResourceLocation location = TEXTURES.containsKey(key) ? TEXTURES.get(key) : register(key, atlas);
        mc.getTextureManager().bindTexture(location);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        color(argb);
        glyphs = Tessellator.getInstance().getWorldRenderer();
        glyphs.begin(7, DefaultVertexFormats.POSITION_TEX);
    }

    @Override
    public void glyph(float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
        glyphs.pos(x, y + height, 0.0D).tex(u0, v1).endVertex();
        glyphs.pos(x + width, y + height, 0.0D).tex(u1, v1).endVertex();
        glyphs.pos(x + width, y, 0.0D).tex(u1, v0).endVertex();
        glyphs.pos(x, y, 0.0D).tex(u0, v0).endVertex();
    }

    @Override
    public void endGlyphs() {
        Tessellator.getInstance().draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        glyphs = null;
    }

    private ResourceLocation texture(String namespace, String path) {
        String key = namespace + ":" + path;
        if (TEXTURES.containsKey(key)) {
            return TEXTURES.get(key);
        }
        BufferedImage image = Images.read(namespace, path);
        if (image == null) {
            System.err.println("[Comet] Missing image " + key);
            TEXTURES.put(key, null);
            return null;
        }
        return register(key, image);
    }

    private ResourceLocation register(String key, BufferedImage image) {
        ResourceLocation location = new ResourceLocation(key);
        mc.getTextureManager().loadTexture(location, new DynamicTexture(image));
        mc.getTextureManager().bindTexture(location);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        TEXTURES.put(key, location);
        return location;
    }

    @Override
    public void push(int x, int y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
    }
}
