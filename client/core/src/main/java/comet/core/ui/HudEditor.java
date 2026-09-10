package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.mod.HudLayout;
import comet.core.mod.HudMod;
import comet.core.mod.ModSettings;

public final class HudEditor extends Screen {
    private static final int WORDMARK_WIDTH = 104;
    private static final int HANDLE = 6;
    private final CometClient client;
    private final Button mods = new Button("MODS", 150, 28);
    private boolean openingShift;
    private HudMod dragging;
    private HudMod resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeLeft;
    private int resizeWidth;
    private float resizeScale;

    public HudEditor(CometClient client) {
        this.client = client;
        openingShift = client.host().keyDown(Keys.RIGHT_SHIFT);
    }

    @Override
    public void opened() {
        client.host().blur(true);
    }

    @Override
    public void closed() {
        client.host().blur(false);
        client.menuClosed();
    }

    @Override
    public boolean pausesGame() {
        return true;
    }

    @Override
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        if (!client.host().keyDown(Keys.RIGHT_SHIFT)) {
            openingShift = false;
        }
        canvas.fill(0, 0, width, height, 0x50000000);
        for (HudMod mod : client.mods().hud()) {
            drawElement(canvas, mod, mouseX, mouseY);
        }
        int centerX = width / 2;
        int centerY = height / 2;
        Brand.wordmark(canvas, centerX, centerY - Brand.height(WORDMARK_WIDTH) / 2 - 28, WORDMARK_WIDTH);
        mods.place(centerX - mods.width / 2, centerY - mods.height / 2);
        mods.draw(canvas, mouseX, mouseY);
    }

    private void drawElement(Canvas canvas, HudMod mod, int mouseX, int mouseY) {
        HudLayout.Placement placement = client.placement(mod, canvas);
        if (placement.width == 0 || placement.height == 0) {
            return;
        }
        boolean active = mod == dragging || mod == resizing;
        boolean hover = active || (dragging == null && resizing == null && placement.contains(mouseX, mouseY));
        int border = placement.locked ? 0xFFE05555 : hover ? 0xFFFFFFFF : 0x80FFFFFF;
        int x = placement.x;
        int y = placement.y;
        int w = placement.width;
        int h = placement.height;
        canvas.fill(x - 1, y - 1, w + 2, h + 2, 0x30000000);
        client.renderElement(mod, placement, canvas);
        canvas.fill(x - 1, y - 1, w + 2, 1, border);
        canvas.fill(x - 1, y + h, w + 2, 1, border);
        canvas.fill(x - 1, y, 1, h, border);
        canvas.fill(x + w, y, 1, h, border);
        if (placement.locked) {
            canvas.fill(x + w - 6, y + 1, 1, 3, 0xFFE05555);
            canvas.fill(x + w - 3, y + 1, 1, 3, 0xFFE05555);
            canvas.fill(x + w - 6, y + 1, 4, 1, 0xFFE05555);
            canvas.fill(x + w - 7, y + 4, 6, 4, 0xFFE05555);
        } else if (hover) {
            canvas.fill(x + w - HANDLE + 1, y + h - HANDLE + 1, HANDLE, HANDLE, 0xFFFFFFFF);
        }
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        Canvas canvas = client.host().canvas();
        for (HudMod mod : client.mods().hud()) {
            HudLayout.Placement placement = client.placement(mod, canvas);
            if (!placement.contains(mouseX, mouseY)) {
                continue;
            }
            ModSettings.Layout layout = layout(mod);
            if (button == 1) {
                layout.locked = !layout.locked;
                client.mods().settings().setLayout(mod.id(), layout);
            } else if (button == 0 && !placement.locked) {
                boolean handle = mouseX >= placement.x + placement.width - HANDLE && mouseY >= placement.y + placement.height - HANDLE;
                if (handle) {
                    resizing = mod;
                    resizeLeft = placement.x;
                    resizeWidth = Math.max(1, placement.width);
                    resizeScale = placement.scale;
                } else {
                    dragging = mod;
                    dragOffsetX = mouseX - placement.x;
                    dragOffsetY = mouseY - placement.y;
                }
            }
            return;
        }
        if (button == 0 && mods.contains(mouseX, mouseY)) {
            client.openModMenu();
        }
    }

    @Override
    public void mouseDrag(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        Canvas canvas = client.host().canvas();
        if (dragging != null) {
            HudLayout.Placement placement = client.placement(dragging, canvas);
            ModSettings.Layout layout = layout(dragging);
            int expansion = Math.round(dragging.horizontalExpansion(canvas) * placement.scale);
            layout.x = HudLayout.fraction(mouseX - dragOffsetX + expansion, placement.width - 2 * expansion, width);
            layout.y = HudLayout.fraction(mouseY - dragOffsetY, placement.height, height);
        } else if (resizing != null) {
            ModSettings.Layout layout = layout(resizing);
            layout.scale = HudLayout.clampScale(resizeScale * (mouseX - resizeLeft) / (float) resizeWidth);
        }
    }

    @Override
    public void mouseUp(int mouseX, int mouseY, int button) {
        HudMod active = dragging != null ? dragging : resizing;
        if (active != null) {
            client.mods().settings().setLayout(active.id(), layout(active));
        }
        dragging = null;
        resizing = null;
    }

    @Override
    public void scroll(int mouseX, int mouseY, int direction) {
        if (direction == 0) {
            return;
        }
        Canvas canvas = client.host().canvas();
        for (HudMod mod : client.mods().hud()) {
            HudLayout.Placement placement = client.placement(mod, canvas);
            if (placement.contains(mouseX, mouseY) && !placement.locked) {
                ModSettings.Layout layout = layout(mod);
                layout.scale = HudLayout.clampScale(placement.scale + 0.1F * Integer.signum(direction));
                client.mods().settings().setLayout(mod.id(), layout);
                return;
            }
        }
    }

    private ModSettings.Layout layout(HudMod mod) {
        ModSettings.Layout stored = client.mods().settings().layout(mod.id());
        if (stored != null) {
            return stored;
        }
        ModSettings.Layout created = HudLayout.defaults(mod);
        client.mods().settings().setLayout(mod.id(), created);
        return created;
    }

    @Override
    public boolean key(char character, int code) {
        if (code == Keys.RIGHT_SHIFT && openingShift) {
            return true;
        }
        if (code == Keys.ESCAPE || code == Keys.RIGHT_SHIFT) {
            client.host().closeScreen();
            return true;
        }
        return false;
    }
}
