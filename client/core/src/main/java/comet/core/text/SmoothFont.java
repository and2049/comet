package comet.core.text;

import comet.core.bridge.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class SmoothFont {
    public static final String KEY = "comet:font";
    private static final int SIZE = 40;
    private static final int ATLAS = 512;
    private static final int PADDING = 2;
    private static final char DEGREE = '°';
    private static SmoothFont instance;
    private static boolean failed;
    private final BufferedImage atlas = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
    private final Glyph[] glyphs = new Glyph[128];
    private final float capHeight;

    private static final class Glyph {
        float u0;
        float v0;
        float u1;
        float v1;
        float width;
        float height;
        float advance;
        float bearingX;
        float bearingY;
    }

    public static SmoothFont get() {
        if (instance == null && !failed) {
            try {
                instance = new SmoothFont();
            } catch (Exception error) {
                failed = true;
                System.err.println("[Comet] Smooth font unavailable, using the vanilla font: " + error);
            } catch (LinkageError error) {
                failed = true;
                System.err.println("[Comet] Smooth font unavailable, using the vanilla font: " + error);
            }
        }
        return instance;
    }

    private SmoothFont() throws Exception {
        Font font;
        try (InputStream in = SmoothFont.class.getResourceAsStream("/assets/comet/fonts/Inter-SemiBold.ttf")) {
            if (in == null) {
                throw new IOException("Inter-SemiBold.ttf is missing");
            }
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) SIZE);
        }
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        FontRenderContext context = graphics.getFontRenderContext();
        int penX = PADDING;
        int penY = PADDING;
        int rowHeight = 0;
        for (int code = 32; code < 128; code++) {
            char character = code == 127 ? DEGREE : (char) code;
            GlyphVector vector = font.createGlyphVector(context, String.valueOf(character));
            Rectangle bounds = vector.getPixelBounds(context, 0, 0);
            int width = Math.max(1, bounds.width);
            int height = Math.max(1, bounds.height);
            if (penX + width + PADDING > ATLAS) {
                penX = PADDING;
                penY += rowHeight + PADDING;
                rowHeight = 0;
            }
            if (penY + height + PADDING > ATLAS) {
                throw new IOException("Font atlas overflow");
            }
            graphics.drawGlyphVector(vector, penX - bounds.x, penY - bounds.y);
            Glyph glyph = new Glyph();
            glyph.u0 = penX / (float) ATLAS;
            glyph.v0 = penY / (float) ATLAS;
            glyph.u1 = (penX + width) / (float) ATLAS;
            glyph.v1 = (penY + height) / (float) ATLAS;
            glyph.width = bounds.width;
            glyph.height = bounds.height;
            glyph.advance = vector.getGlyphMetrics(0).getAdvance();
            glyph.bearingX = bounds.x;
            glyph.bearingY = bounds.y;
            glyphs[code] = glyph;
            penX += width + PADDING;
            rowHeight = Math.max(rowHeight, height);
        }
        graphics.dispose();
        capHeight = glyphs['H'].height;
    }

    private Glyph glyph(char character) {
        if (character == DEGREE) {
            return glyphs[127];
        }
        return character >= 32 && character < 127 ? glyphs[character] : null;
    }

    public boolean supports(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (glyph(text.charAt(index)) == null) {
                return false;
            }
        }
        return true;
    }

    public float width(String text, float size) {
        float scale = size / SIZE;
        float width = 0;
        for (int index = 0; index < text.length(); index++) {
            Glyph glyph = glyph(text.charAt(index));
            width += (glyph == null ? glyphs['?'] : glyph).advance * scale;
        }
        return width;
    }

    public float height(float size) {
        return capHeight * size / SIZE;
    }

    public void draw(Canvas canvas, String text, float x, float y, float size, int argb) {
        float scale = size / SIZE;
        float baseline = y + capHeight * scale;
        float pen = x;
        canvas.beginGlyphs(KEY, atlas, argb);
        for (int index = 0; index < text.length(); index++) {
            Glyph glyph = glyph(text.charAt(index));
            if (glyph == null) {
                glyph = glyphs['?'];
            }
            if (glyph.width > 0) {
                canvas.glyph(
                    pen + glyph.bearingX * scale,
                    baseline + glyph.bearingY * scale,
                    glyph.width * scale,
                    glyph.height * scale,
                    glyph.u0,
                    glyph.v0,
                    glyph.u1,
                    glyph.v1);
            }
            pen += glyph.advance * scale;
        }
        canvas.endGlyphs();
    }
}
