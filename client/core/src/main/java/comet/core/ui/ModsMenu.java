package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.mod.HudLayout;
import comet.core.mod.HudMod;
import comet.core.mod.Mod;
import comet.core.mod.ModSettings;
import comet.core.text.Text;
import java.util.List;

public final class ModsMenu extends Screen {
    private static final int WORDMARK_WIDTH = 104;
    private static final int ROW = 30;
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_RADIUS = 8;
    private static final int HANDLE = 6;
    private static final int TOGGLE_WIDTH = 26;
    private static final int TOGGLE_HEIGHT = 12;
    private final CometClient client;
    private final Button mods = new Button("MODS", 150, 28);
    private final Button back = new Button("BACK", 90, 22);
    private boolean list;
    private int swallowShift = 5;
    private HudMod dragging;
    private HudMod resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeLeft;
    private int resizeWidth;
    private float resizeScale;

    public ModsMenu(CometClient client) {
        this.client = client;
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
        if (swallowShift > 0) {
            swallowShift--;
        }
        canvas.fill(0, 0, width, height, 0x50000000);
        if (list) {
            drawList(canvas, mouseX, mouseY);
        } else {
            drawHome(canvas, mouseX, mouseY);
        }
    }

    private void drawHome(Canvas canvas, int mouseX, int mouseY) {
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

    private void drawList(Canvas canvas, int mouseX, int mouseY) {
        List<Mod> all = client.mods().all();
        int panelHeight = panelHeight(all);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight) / 2;
        canvas.roundedFill(left - 1, top - 1, PANEL_WIDTH + 2, panelHeight + 2, PANEL_RADIUS + 1, 0x50FFFFFF);
        canvas.roundedFill(left, top, PANEL_WIDTH, panelHeight, PANEL_RADIUS, 0xD8121212);
        Text.draw(canvas, "Mods", left + 14, top + 11, 10, 0xFFFFFFFF);
        String count = client.mods().enabledCount() + " / " + all.size() + " enabled";
        Text.draw(canvas, count, left + PANEL_WIDTH - 14 - Text.width(canvas, count, 7), top + 13, 7, 0xFF9A9A9A);
        int y = top + 34;
        if (all.isEmpty()) {
            canvas.text("No mods yet.", left + 14, y + 10, 0xFFAAAAAA, false);
        }
        for (Mod mod : all) {
            boolean hover = mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= y && mouseY < y + ROW;
            if (hover) {
                canvas.fill(left + 4, y, PANEL_WIDTH - 8, ROW, 0x22FFFFFF);
            }
            Text.draw(canvas, mod.name(), left + 14, y + 6, 9, 0xFFFFFFFF);
            Text.draw(canvas, mod.description(), left + 14, y + 18, 7, 0xFF9A9A9A);
            drawToggle(canvas, left + PANEL_WIDTH - 14 - TOGGLE_WIDTH, y + (ROW - TOGGLE_HEIGHT) / 2, client.mods().isEnabled(mod));
            y += ROW;
        }
        back.place(left + (PANEL_WIDTH - back.width) / 2, top + panelHeight - back.height - 12);
        back.draw(canvas, mouseX, mouseY);
    }

    private int panelHeight(List<Mod> all) {
        return 34 + Math.max(1, all.size()) * ROW + back.height + 24;
    }

    private void drawToggle(Canvas canvas, int x, int y, boolean enabled) {
        canvas.roundedFill(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2, enabled ? 0xFF3FB950 : 0xFF3A3A3A);
        int knob = TOGGLE_HEIGHT - 4;
        canvas.roundedFill(enabled ? x + TOGGLE_WIDTH - knob - 2 : x + 2, y + 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (list) {
            if (button == 0) {
                clickList(mouseX, mouseY);
            }
            return;
        }
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
            list = true;
        }
    }

    @Override
    public void mouseDrag(int mouseX, int mouseY, int button) {
        if (list || button != 0) {
            return;
        }
        Canvas canvas = client.host().canvas();
        if (dragging != null) {
            HudLayout.Placement placement = client.placement(dragging, canvas);
            ModSettings.Layout layout = layout(dragging);
            layout.x = HudLayout.fraction(mouseX - dragOffsetX, placement.width, width);
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
        if (list || direction == 0) {
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

    private void clickList(int mouseX, int mouseY) {
        if (back.contains(mouseX, mouseY)) {
            list = false;
            return;
        }
        List<Mod> all = client.mods().all();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight(all)) / 2;
        int y = top + 34;
        for (Mod mod : all) {
            if (mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= y && mouseY < y + ROW) {
                client.mods().toggle(mod);
                return;
            }
            y += ROW;
        }
    }

    @Override
    public boolean key(char character, int code) {
        if (code == Keys.RIGHT_SHIFT && swallowShift > 0) {
            swallowShift = 0;
            return true;
        }
        if (code == Keys.ESCAPE || code == Keys.RIGHT_SHIFT) {
            if (list) {
                list = false;
            } else {
                client.host().closeScreen();
            }
            return true;
        }
        return false;
    }
}
