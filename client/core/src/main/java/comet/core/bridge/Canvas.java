package comet.core.bridge;

import java.awt.image.BufferedImage;

public interface Canvas {
    int width();

    int height();

    void fill(int x, int y, int width, int height, int argb);

    void roundedFill(int x, int y, int width, int height, int radius, int argb);

    void gradient(int x, int y, int width, int height, int topArgb, int bottomArgb);

    void text(String value, int x, int y, int argb, boolean shadow);

    int textWidth(String value);

    int textHeight();

    void image(String namespace, String path, int x, int y, int width, int height);

    void image(String namespace, String path, int x, int y, int width, int height, float alpha);

    void texture(String key, int width, int height, int[] argb);

    int[] textureSize(String key);

    void draw(String key, int x, int y, int width, int height, float alpha);

    void beginGlyphs(String key, BufferedImage atlas, int argb);

    void glyph(float x, float y, float width, float height, float u0, float v0, float u1, float v1);

    void endGlyphs();

    void push(int x, int y, float scale);

    void pop();
}
