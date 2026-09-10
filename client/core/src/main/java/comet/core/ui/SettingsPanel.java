package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.mod.GlobalSetting;
import comet.core.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SettingsPanel {
    private static final int ROW = 26;
    private final CometClient client;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private String query = "";
    private String error = "";
    private boolean searching;

    SettingsPanel(CometClient client) {
        this.client = client;
    }

    void resize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private List<GlobalSetting> visible() {
        List<GlobalSetting> result = new ArrayList<GlobalSetting>();
        for (GlobalSetting setting : GlobalSetting.values()) {
            if ((setting.label + " " + setting.category).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                result.add(setting);
            }
        }
        return result;
    }

    private int maxScroll() {
        return Math.max(0, visible().size() * ROW - (height - 50));
    }

    void draw(Canvas canvas, int mouseX, int mouseY) {
        Widgets.pill(canvas, x, y, width, 16, searching ? Widgets.HOVER : Widgets.FILL);
        ModIcons.draw(canvas, "search", x + 4, y + 3, 10);
        Text.draw(canvas, query.isEmpty() && !searching ? "Search settings" : query + (searching ? "_" : ""),
                x + 19, y + 5, 6, 0xFFCCCCCC);
        scroll = Math.min(scroll, maxScroll());
        canvas.clip(x - 1, y + 25, width + 2, height - 50);
        int top = y + 26 - scroll;
        for (GlobalSetting setting : visible()) {
            boolean enabled = client.mods().settings().global(setting);
            Widgets.pill(canvas, x, top, width, ROW - 5, inside(mouseX, mouseY, x, top, width, ROW - 5) ? Widgets.HOVER : Widgets.FILL);
            Widgets.toggle(canvas, x + 6, top + 4, enabled);
            float size = Text.fit(canvas, setting.label, 7, width - 44, 5);
            Text.draw(canvas, setting.label, x + 39, top + (ROW - 5 - Text.height(canvas, setting.label, size)) / 2, size, 0xFFE0E0E0);
            top += ROW;
        }
        canvas.unclip();
        if (maxScroll() > 0) {
            int track = height - 50;
            int thumb = Math.max(10, track * track / (track + maxScroll()));
            canvas.roundedFill(x + width + 4, y + 25 + (track - thumb) * scroll / maxScroll(), 2, thumb, 1, 0x90FFFFFF);
        }
        String rawStatus = client.mods().settings().global(GlobalSetting.RAW_MOUSE_INPUT) ? client.rawMouse().status() : "";
        String windowStatus = client.borderless().status();
        String status = !error.isEmpty() ? error : !windowStatus.isEmpty() ? windowStatus : !rawStatus.isEmpty() ? rawStatus : "Shared across all mod presets";
        Text.draw(canvas, status, x, y + height - 13, Text.fit(canvas, status, 6, width, 4), error.isEmpty() ? 0xFF91998B : 0xFFEBAFA3);
    }

    void click(int mouseX, int mouseY) {
        searching = inside(mouseX, mouseY, x, y, width, 16);
        if (!inside(mouseX, mouseY, x, y + 25, width, height - 50)) {
            return;
        }
        int offset = mouseY - y - 26 + scroll;
        List<GlobalSetting> settings = visible();
        if (offset < 0 || offset % ROW >= ROW - 5 || offset / ROW >= settings.size()) {
            return;
        }
        GlobalSetting setting = settings.get(offset / ROW);
        try {
            client.mods().settings().setGlobal(setting, !client.mods().settings().global(setting));
            if (setting == GlobalSetting.RAW_MOUSE_INPUT && client.mods().settings().global(setting)) {
                client.rawMouse().prepare();
            }
            error = "";
        } catch (IllegalStateException failure) {
            error = "Could not save settings";
        }
    }

    void scroll(int mouseX, int mouseY, int direction) {
        if (inside(mouseX, mouseY, x, y + 25, width + 6, height - 50)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - direction * ROW));
        }
    }

    boolean key(char character, int code) {
        if (!searching) {
            return false;
        }
        if (code == Keys.ESCAPE) {
            searching = false;
        } else if (code == Keys.BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
        } else if (character >= ' ' && character < 127 && query.length() < 24) {
            query += character;
        }
        scroll = 0;
        return true;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
