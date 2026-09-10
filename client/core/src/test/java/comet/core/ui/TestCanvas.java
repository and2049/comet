package comet.core.ui;

import comet.core.bridge.Canvas;
import comet.core.bridge.Images;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class TestCanvas implements Canvas {
    public final List<String> texts = new ArrayList<String>();
    public final List<String> drawnTextures = new ArrayList<String>();
    private final BufferedImage image;
    private final Graphics2D g;
    private final Map<String, BufferedImage> textures = new HashMap<String, BufferedImage>();
    private final Deque<AffineTransform> transforms = new ArrayDeque<AffineTransform>();
    private BufferedImage atlas;
    private int clips;

    public TestCanvas(int width, int height) {
        image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        g.scale(2, 2);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        gradient(0, 0, width, height, 0xFF465B65, 0xFF233C2A);
    }

    public void save(String name) throws IOException {
        String directory = System.getProperty("comet.previewDir");
        if (directory != null) {
            File folder = new File(directory);
            folder.mkdirs();
            ImageIO.write(image, "png", new File(folder, name + ".png"));
        }
    }

    public boolean clipsBalanced() { return clips == 0; }
    public int width() { return image.getWidth() / 2; }
    public int height() { return image.getHeight() / 2; }

    public void fill(int x, int y, int width, int height, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(x, y, width, height);
    }

    public void roundedFill(int x, int y, int width, int height, int radius, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
    }

    public void gradient(int x, int y, int width, int height, int top, int bottom) {
        g.setPaint(new java.awt.GradientPaint(x, y, new Color(top, true), x, y + height, new Color(bottom, true)));
        g.fillRect(x, y, width, height);
    }

    public void text(String value, int x, int y, int argb, boolean shadow) {
        texts.add(value);
        g.setColor(new Color(argb, true));
        g.drawString(value, x, y + 8);
    }

    public int textWidth(String value) {
        int width = 0;
        for (char c : value.toCharArray()) width += c == '1' ? 3 : c == ' ' ? 4 : 6;
        return width;
    }

    public int textHeight() { return 9; }

    public void image(String namespace, String path, int x, int y, int width, int height) {
        image(namespace, path, x, y, width, height, 1);
    }

    public void image(String namespace, String path, int x, int y, int width, int height, float alpha) {
        BufferedImage source = Images.read(namespace, path);
        if (source != null) g.drawImage(source, x, y, width, height, null);
    }

    public void texture(String key, int width, int height, int[] argb) {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, width, height, argb, 0, width);
        textures.put(key, source);
    }

    public int[] textureSize(String key) {
        BufferedImage source = textures.get(key);
        return source == null ? new int[] {16, 9} : new int[] {source.getWidth(), source.getHeight()};
    }

    public void draw(String key, int x, int y, int width, int height, float alpha) {
        if (!textures.containsKey(key)) throw new AssertionError("Texture was not uploaded: " + key);
        drawnTextures.add(key);
        g.drawImage(textures.get(key), x, y, width, height, null);
    }

    public void beginGlyphs(String key, BufferedImage source, int argb) {
        atlas = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D tint = atlas.createGraphics();
        tint.drawImage(source, 0, 0, null);
        tint.setComposite(AlphaComposite.SrcIn);
        tint.setColor(new Color(argb, true));
        tint.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        tint.dispose();
    }

    public void glyph(float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
        g.drawImage(atlas, Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height),
                Math.round(u0 * atlas.getWidth()), Math.round(v0 * atlas.getHeight()),
                Math.round(u1 * atlas.getWidth()), Math.round(v1 * atlas.getHeight()), null);
    }

    public void endGlyphs() { atlas = null; }

    public void clip(int x, int y, int width, int height) {
        if (width < 0 || height < 0 || clips != 0) throw new AssertionError("Invalid clip");
        clips++;
        g.setClip(x, y, width, height);
    }

    public void unclip() {
        clips--;
        g.setClip(null);
    }

    public void push(int x, int y, float scale) {
        transforms.push(g.getTransform());
        g.translate(x, y);
        g.scale(scale, scale);
    }

    public void pop() { g.setTransform(transforms.pop()); }
}
