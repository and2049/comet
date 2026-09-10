package comet.mc1710.bridge;

import comet.core.bridge.Canvas;
import comet.core.bridge.Images;
import comet.core.bridge.Shapes;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class Canvas1710 implements Canvas {
    private static final int SEGMENTS = 6;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ScaledResolution resolution;
    private int glyphArgb;

    public Canvas1710() {
        resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
    }

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
        GL11.glColor4f(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    @Override
    public void roundedFill(int x, int y, int width, int height, int radius, int argb) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        color(argb);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_TRIANGLE_FAN);
        tessellator.setColorRGBA_I(argb & 0xFFFFFF, argb >>> 24);
        tessellator.addVertex(x + width / 2.0D, y + height / 2.0D, 0.0D);
        double[] points = Shapes.roundedOutline(x, y, width, height, radius, SEGMENTS);
        for (int index = 0; index < points.length; index += 2) {
            tessellator.addVertex(points[index], points[index + 1], 0.0D);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void gradient(int x, int y, int width, int height, int topArgb, int bottomArgb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(bottomArgb & 0xFFFFFF, bottomArgb >>> 24);
        tessellator.addVertex(x, y + height, 0.0D);
        tessellator.addVertex(x + width, y + height, 0.0D);
        tessellator.setColorRGBA_I(topArgb & 0xFFFFFF, topArgb >>> 24);
        tessellator.addVertex(x + width, y, 0.0D);
        tessellator.addVertex(x, y, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
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
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
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
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(0xFFFFFF, Math.round(alpha * 255));
        tessellator.addVertexWithUV(x, y + height, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void beginGlyphs(String key, BufferedImage atlas, int argb) {
        ResourceLocation location = TEXTURES.containsKey(key) ? TEXTURES.get(key) : register(key, atlas);
        mc.getTextureManager().bindTexture(location);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        color(argb);
        glyphArgb = argb;
        Tessellator.instance.startDrawingQuads();
    }

    @Override
    public void glyph(float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.setColorRGBA_I(glyphArgb & 0xFFFFFF, glyphArgb >>> 24);
        tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
    }

    @Override
    public void endGlyphs() {
        Tessellator.instance.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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
    public void clip(int x, int y, int width, int height) {
        int factor = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * factor, mc.displayHeight - (y + height) * factor, Math.max(0, width * factor), Math.max(0, height * factor));
    }

    @Override
    public void unclip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void push(int x, int y, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
    }

    @Override
    public void pop() {
        GL11.glPopMatrix();
    }
}
