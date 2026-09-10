package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.mod.ModSettings;
import comet.core.text.Text;
import java.util.List;

final class PresetPanel {
    private static final int ROW = 20;
    private static final int DIALOG_WIDTH = 240;
    private final CometClient client;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private Mode mode;
    private String editing;
    private String name = "";
    private String error = "";

    private enum Mode { CREATE, EDIT, ERROR }

    PresetPanel(CometClient client) {
        this.client = client;
    }

    void resize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    boolean modal() {
        return mode != null;
    }

    private ModSettings settings() {
        return client.mods().settings();
    }

    private int listHeight() {
        return Math.max(ROW, height - 80);
    }

    private int maxScroll() {
        return Math.max(0, settings().presetNames().size() * ROW - listHeight());
    }

    void draw(Canvas canvas, int mouseX, int mouseY) {
        Text.draw(canvas, "Presets", x, y, 8, 0xFFDDDDDD);
        List<String> presets = settings().presetNames();
        scroll = Math.min(scroll, maxScroll());
        canvas.clip(x - 1, y + 17, width + 2, listHeight());
        for (int index = 0; index < presets.size(); index++) {
            String preset = presets.get(index);
            int rowY = y + 18 + index * ROW - scroll;
            boolean active = preset.equals(settings().activePreset());
            boolean hover = !modal() && inside(mouseX, mouseY, x, rowY, width, ROW - 3);
            Widgets.pill(canvas, x, rowY, width, ROW - 3, active ? 0xB82C3825 : hover ? Widgets.HOVER : Widgets.FILL);
            if (active) {
                canvas.roundedFill(x + 5, rowY + 7, 3, 3, 1, 0xFFACE77A);
            }
            String shown = preset;
            while (Text.width(canvas, shown, 6) > width - 29 && shown.length() > 1) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(preset)) {
                shown = shown.substring(0, Math.max(0, shown.length() - 2)) + "..";
            }
            Text.draw(canvas, shown, x + 12, rowY + 5, 6, active ? 0xFFE8F4DE : 0xFFC6C6C6);
            ModIcons.draw(canvas, "edit", x + width - 14, rowY + 3, 11);
        }
        canvas.unclip();
        if (maxScroll() > 0) {
            int thumb = Math.max(8, listHeight() * listHeight() / (presets.size() * ROW));
            canvas.roundedFill(x + width + 3, y + 17 + (listHeight() - thumb) * scroll / maxScroll(), 1, thumb, 0, 0x80FFFFFF);
        }
        boolean dirty = settings().presetModified();
        Text.draw(canvas, dirty ? "Unsaved changes" : "Saved", x + 1, y + height - 54, 5, dirty ? 0xFFC8D8B8 : 0xFF808780);
        button(canvas, "Save changes", y + height - 42, dirty, mouseX, mouseY);
        button(canvas, "New preset", y + height - 20, true, mouseX, mouseY);
    }

    private void button(Canvas canvas, String label, int top, boolean enabled, int mouseX, int mouseY) {
        Widgets.button(canvas, x, top, width, 18, label, !modal() && inside(mouseX, mouseY, x, top, width, 18), enabled);
    }

    boolean mouseDown(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        try {
            if (inside(mouseX, mouseY, x, y + height - 20, width, 18)) {
                mode = Mode.CREATE;
                name = "";
                error = "";
            } else if (inside(mouseX, mouseY, x, y + height - 42, width, 18) && settings().presetModified()) {
                settings().savePreset();
            } else if (inside(mouseX, mouseY, x, y + 17, width, listHeight())) {
                List<String> presets = settings().presetNames();
                int offset = mouseY - y - 18 + scroll;
                if (offset < 0 || offset % ROW >= ROW - 3) {
                    return true;
                }
                int index = offset / ROW;
                if (index >= 0 && index < presets.size()) {
                    String preset = presets.get(index);
                    if (mouseX >= x + width - 17) {
                        editing = preset;
                        name = preset;
                        error = "";
                        mode = Mode.EDIT;
                    } else {
                        client.mods().loadPreset(preset);
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            error = failure.getMessage();
            mode = Mode.ERROR;
        }
        return true;
    }

    boolean scroll(int mouseX, int mouseY, int direction) {
        if (!inside(mouseX, mouseY, x - 1, y + 17, width + 5, listHeight())) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - direction * ROW));
        return true;
    }

    private int dialogHeight() {
        return mode == Mode.EDIT ? 138 : 110;
    }

    void drawDialog(Canvas canvas, int mouseX, int mouseY) {
        int left = (canvas.width() - DIALOG_WIDTH) / 2;
        int top = (canvas.height() - dialogHeight()) / 2;
        canvas.fill(0, 0, canvas.width(), canvas.height(), 0x80000000);
        Widgets.pill(canvas, left, top, DIALOG_WIDTH, dialogHeight(), 0xF4141414);
        ModIcons.draw(canvas, mode == Mode.CREATE ? "plus" : "edit", left + 10, top + 10, 13);
        Text.draw(canvas, mode == Mode.CREATE ? "New preset" : mode == Mode.EDIT ? "Edit preset" : "Preset error", left + 29, top + 12, 9, 0xFFFFFFFF);
        if (mode != Mode.ERROR) {
            Widgets.pill(canvas, left + 10, top + 34, DIALOG_WIDTH - 20, 22, Widgets.FILL);
            Text.draw(canvas, name.isEmpty() ? "Preset name" : name + "_", left + 18, top + 42, 7, name.isEmpty() ? 0xFF828282 : 0xFFFFFFFF);
        }
        if (!error.isEmpty()) {
            float size = Text.fit(canvas, error, 6, DIALOG_WIDTH - 20, 5);
            Text.draw(canvas, error, left + 10, top + 64, size, 0xFFEBAFA3);
        }
        Widgets.button(canvas, left + 10, top + 82, 106, 18, mode == Mode.ERROR ? "Close" : "Cancel",
                inside(mouseX, mouseY, left + 10, top + 82, 106, 18), true);
        if (mode != Mode.ERROR) {
            Widgets.button(canvas, left + 124, top + 82, 106, 18, mode == Mode.CREATE ? "Create" : "Rename",
                    inside(mouseX, mouseY, left + 124, top + 82, 106, 18), !name.trim().isEmpty());
        }
        if (mode == Mode.EDIT) {
            Widgets.button(canvas, left + 10, top + 110, DIALOG_WIDTH - 20, 18, "Delete preset",
                    inside(mouseX, mouseY, left + 10, top + 110, DIALOG_WIDTH - 20, 18), settings().presetNames().size() > 1);
        }
    }

    void clickDialog(Canvas canvas, int mouseX, int mouseY) {
        int left = (canvas.width() - DIALOG_WIDTH) / 2;
        int top = (canvas.height() - dialogHeight()) / 2;
        if (inside(mouseX, mouseY, left + 10, top + 82, 106, 18)) {
            mode = null;
        } else if (mode != Mode.ERROR && inside(mouseX, mouseY, left + 124, top + 82, 106, 18)) {
            submit();
        } else if (mode == Mode.EDIT && settings().presetNames().size() > 1
                && inside(mouseX, mouseY, left + 10, top + 110, DIALOG_WIDTH - 20, 18)) {
            try {
                client.mods().deletePreset(editing);
                mode = null;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                error = failure.getMessage();
            }
        }
    }

    private void submit() {
        try {
            if (mode == Mode.CREATE) {
                settings().createPreset(name);
                scroll = maxScroll();
            } else if (mode == Mode.EDIT) {
                settings().renamePreset(editing, name);
            }
            mode = null;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            error = failure.getMessage();
        }
    }

    void key(char character, int code) {
        if (code == Keys.ESCAPE || code == Keys.RIGHT_SHIFT) {
            mode = null;
        } else if (code == Keys.ENTER) {
            submit();
        } else if (code == Keys.BACKSPACE && !name.isEmpty()) {
            name = name.substring(0, name.length() - 1);
            error = "";
        } else if (character >= ' ' && character < 127 && name.length() < ModSettings.MAX_PRESET_NAME) {
            name += character;
            error = "";
        }
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
