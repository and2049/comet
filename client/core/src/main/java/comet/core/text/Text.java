package comet.core.text;

import comet.core.bridge.Canvas;

public final class Text {
    private static final float VANILLA = 9;

    private Text() {
    }

    public static String clean(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '§' && index + 1 < value.length()) {
                index++;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static SmoothFont font(String text) {
        SmoothFont font = SmoothFont.get();
        return font != null && font.supports(text) ? font : null;
    }

    public static float width(Canvas canvas, String value, float size) {
        String text = clean(value);
        SmoothFont font = font(text);
        return font == null ? canvas.textWidth(text) * size / VANILLA : font.width(text, size);
    }

    public static float height(Canvas canvas, String value, float size) {
        SmoothFont font = font(clean(value));
        return font == null ? (canvas.textHeight() - 2) * size / VANILLA : font.height(size);
    }

    public static void draw(Canvas canvas, String value, float x, float y, float size, int argb) {
        String text = clean(value);
        SmoothFont font = font(text);
        if (font != null) {
            font.draw(canvas, text, x, y, size, argb);
            return;
        }
        float scale = size / VANILLA;
        canvas.push(Math.round(x), Math.round(y), scale);
        canvas.text(text, 0, 0, argb, true);
        canvas.pop();
    }

    public static void drawCentered(Canvas canvas, String value, float centerX, float y, float size, int argb) {
        draw(canvas, value, centerX - width(canvas, value, size) / 2, y, size, argb);
    }

    public static float fit(Canvas canvas, String value, float size, float available, float minimum) {
        float width = width(canvas, value, size);
        return width > available ? Math.max(minimum, size * available / width) : size;
    }
}
