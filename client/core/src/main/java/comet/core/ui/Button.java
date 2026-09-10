package comet.core.ui;

import comet.core.bridge.Canvas;

final class Button {
    int x;
    int y;
    final int width;
    final int height;
    final String label;

    Button(String label, int width, int height) {
        this.label = label;
        this.width = width;
        this.height = height;
    }

    void place(int x, int y) {
        this.x = x;
        this.y = y;
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    void draw(Canvas canvas, int mouseX, int mouseY) {
        Widgets.button(canvas, x, y, width, height, label, contains(mouseX, mouseY), true);
    }
}
