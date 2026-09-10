package comet.core.ui;

import comet.core.bridge.Canvas;

public abstract class Screen {
    protected int width;
    protected int height;

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void opened() {
    }

    public void closed() {
    }

    public abstract void draw(Canvas canvas, int mouseX, int mouseY);

    public void mouseDown(int mouseX, int mouseY, int button) {
    }

    public void mouseUp(int mouseX, int mouseY, int button) {
    }

    public void mouseDrag(int mouseX, int mouseY, int button) {
    }

    public void scroll(int mouseX, int mouseY, int direction) {
    }

    public boolean key(char character, int code) {
        return false;
    }

    public boolean pausesGame() {
        return false;
    }
}
