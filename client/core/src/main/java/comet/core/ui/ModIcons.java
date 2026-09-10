package comet.core.ui;

import comet.core.bridge.Canvas;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

final class ModIcons {
    private static final int SIZE = 96;

    private ModIcons() {
    }

    static void draw(Canvas canvas, String id, int x, int y, int size) {
        String key = "comet:mod/" + id.toLowerCase(java.util.Locale.ROOT);
        int[] dimensions = canvas.textureSize(key);
        if (dimensions == null || dimensions[0] != SIZE || dimensions[1] != SIZE) {
            BufferedImage image = create(id);
            canvas.texture(key, SIZE, SIZE, image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE));
        }
        canvas.draw(key, x, y, size, size, 1);
    }

    private static BufferedImage create(String id) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE7E9E7));
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if ("keystrokes".equals(id)) {
            g.drawRoundRect(37, 13, 22, 22, 5, 5);
            for (int x = 9; x < 80; x += 28) g.drawRoundRect(x, 41, 22, 22, 5, 5);
            g.drawRoundRect(9, 70, 78, 14, 5, 5);
        } else if ("coordinates".equals(id)) {
            g.drawOval(24, 24, 48, 48);
            g.drawLine(48, 8, 48, 33);
            g.drawLine(48, 63, 48, 88);
            g.drawLine(8, 48, 33, 48);
            g.drawLine(63, 48, 88, 48);
        } else if ("ping".equals(id)) {
            for (int index = 0; index < 4; index++) g.fillRoundRect(12 + index * 20, 62 - index * 15, 13, 20 + index * 15, 4, 4);
        } else if ("lighting".equals(id) || "fullbright".equals(id)) {
            g.drawOval(30, 30, 36, 36);
            for (int index = 0; index < 8; index++) {
                g.drawLine(48, 9, 48, 20);
                g.rotate(Math.PI / 4, 48, 48);
            }
        } else if ("cps".equals(id)) {
            g.rotate(Math.PI / 7, 48, 48);
            g.drawRoundRect(25, 16, 46, 66, 38, 38);
            g.drawLine(48, 18, 48, 42);
            g.drawLine(27, 44, 69, 44);
            g.fillRoundRect(44, 25, 8, 13, 6, 6);
        } else if ("fps".equals(id)) {
            g.drawArc(13, 17, 70, 70, 0, 180);
            g.drawLine(13, 52, 13, 67);
            g.drawLine(83, 52, 83, 67);
            g.drawLine(14, 68, 82, 68);
            for (int angle = 30; angle < 180; angle += 30) {
                double radians = Math.toRadians(angle);
                g.drawLine(48 + (int) (26 * Math.cos(radians)), 52 - (int) (26 * Math.sin(radians)),
                        48 + (int) (32 * Math.cos(radians)), 52 - (int) (32 * Math.sin(radians)));
            }
            g.drawLine(48, 55, 63, 32);
            g.fillOval(41, 48, 14, 14);
        } else if ("toggleSprint".equals(id)) {
            g.fillOval(56, 8, 16, 16);
            line(g, 20, 36, 36, 28, 55, 32, 67, 47, 80, 44);
            line(g, 55, 33, 42, 53, 61, 65, 65, 83);
            line(g, 42, 53, 31, 72, 14, 76);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(8, 47, 27, 47);
            g.drawLine(4, 57, 20, 57);
        } else if ("oldAnimations".equals(id)) {
            g.drawArc(12, 12, 72, 72, 35, 135);
            g.drawArc(12, 12, 72, 72, 215, 135);
            line(g, 12, 22, 12, 41, 28, 35);
            line(g, 84, 74, 84, 55, 68, 61);
            Path2D eye = new Path2D.Float();
            eye.moveTo(24, 48);
            eye.quadTo(48, 19, 72, 48);
            eye.quadTo(48, 77, 24, 48);
            g.draw(eye);
            g.fillOval(40, 40, 16, 16);
        } else if ("settings".equals(id)) {
            g.drawOval(25, 25, 46, 46);
            g.drawOval(39, 39, 18, 18);
            for (int index = 0; index < 8; index++) {
                g.drawLine(48, 13, 48, 24);
                g.rotate(Math.PI / 4, 48, 48);
            }
        } else if ("edit".equals(id)) {
            line(g, 24, 59, 64, 19, 77, 32, 37, 72, 18, 78, 24, 59, 37, 72);
            g.drawLine(56, 27, 69, 40);
        } else if ("search".equals(id)) {
            g.drawOval(16, 14, 47, 47);
            g.drawLine(58, 57, 79, 79);
        } else if ("close".equals(id)) {
            g.drawLine(26, 26, 70, 70);
            g.drawLine(70, 26, 26, 70);
        } else if ("plus".equals(id)) {
            g.drawLine(48, 22, 48, 74);
            g.drawLine(22, 48, 74, 48);
        } else if ("label".equals(id) || "showText".equals(id)) {
            line(g, 20, 29, 20, 20, 76, 20, 76, 29);
            g.drawLine(48, 20, 48, 76);
            g.drawLine(35, 76, 61, 76);
        } else if ("blockHit".equals(id)) {
            line(g, 24, 14, 72, 14, 72, 50, 65, 68, 48, 82, 31, 68, 24, 50, 24, 14);
            g.drawLine(48, 25, 48, 65);
        } else if ("useSwing".equals(id)) {
            g.drawArc(16, 18, 64, 64, 10, 265);
            line(g, 64, 12, 82, 26, 66, 35);
        } else if ("toggleSneak".equals(id)) {
            g.fillOval(58, 13, 16, 16);
            line(g, 60, 36, 40, 39, 29, 53, 61, 61, 60, 80, 77, 80);
            line(g, 52, 41, 67, 51, 81, 50);
        } else if ("autoSprint".equals(id)) {
            line(g, 47, 77, 64, 77, 72, 46, 42, 46, 34, 65, 19, 65);
            g.drawLine(12, 33, 39, 33);
            g.drawLine(18, 22, 45, 22);
            g.fillOval(61, 16, 16, 16);
        } else {
            line(g, 22, 48, 40, 66, 75, 27);
        }
        g.dispose();
        return image;
    }

    private static void line(Graphics2D g, int... points) {
        Path2D path = new Path2D.Float();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        g.draw(path);
    }
}
