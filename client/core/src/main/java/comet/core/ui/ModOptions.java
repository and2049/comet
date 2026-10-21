package comet.core.ui;

import comet.core.bridge.Canvas;
import comet.core.bridge.GameHost;
import comet.core.bridge.Keys;
import comet.core.mod.ActionOption;
import comet.core.mod.Mod;
import comet.core.mod.NumberOption;
import comet.core.mod.Option;
import comet.core.mod.TextOption;
import comet.core.text.Text;
import java.util.ArrayList;
import java.util.List;

public final class ModOptions {
    private static final int ROW = 28;
    private final Mod mod;
    private final GameHost host;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private NumberOption binding;
    private TextOption editing;
    private String draft = "";
    private boolean replace;
    private String error = "";
    private boolean open = true;

    public ModOptions(Mod mod, GameHost host) {
        this.mod = mod;
        this.host = host;
    }

    public static boolean available(Mod mod) {
        return !mod.options().isEmpty() || !mod.numbers().isEmpty() || !mod.texts().isEmpty() || !mod.actions().isEmpty();
    }

    public boolean isOpen() {
        return open;
    }

    private List<Object> rows() {
        List<Object> rows = new ArrayList<Object>();
        rows.addAll(mod.options());
        rows.addAll(mod.numbers());
        rows.addAll(mod.texts());
        rows.addAll(mod.actions());
        return rows;
    }

    public void draw(Canvas canvas, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        List<Object> rows = rows();
        width = Math.min(320, screenWidth - 20);
        height = Math.min(screenHeight - 30, 50 + rows.size() * ROW);
        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * ROW - (height - 50))));
        canvas.fill(0, 0, screenWidth, screenHeight, 0x60000000);
        Widgets.pill(canvas, x, y, width, height, 0xF4141414);
        Text.draw(canvas, mod.name(), x + 10, y + 10, 9, 0xFFFFFFFF);
        Text.draw(canvas, "x", x + width - 20, y + 10, 8, 0xFFFFFFFF);
        canvas.clip(x + 6, y + 30, width - 12, height - 50);
        int rowY = y + 30 - scroll;
        for (Object row : rows) {
            Widgets.pill(canvas, x + 8, rowY + 2, width - 16, ROW - 4, Widgets.FILL);
            String label;
            if (row instanceof Option) label = ((Option) row).label;
            else if (row instanceof NumberOption) label = ((NumberOption) row).label;
            else if (row instanceof TextOption) label = ((TextOption) row).label;
            else label = ((ActionOption) row).label;
            Text.draw(canvas, label, x + 13, rowY + 9, Text.fit(canvas, label, 7, width - 151, 4), 0xFFDDDDDD);
            int right = x + width - 130;
            if (row instanceof Option) {
                Widgets.toggle(canvas, x + width - 44, rowY + 6, mod.option((Option) row));
            } else if (row instanceof NumberOption) {
                NumberOption option = (NumberOption) row;
                String value = option.keyBinding ? binding == option ? "Press a key" : mod.number(option) == 0 ? "Unbound" : host.keyName(mod.number(option)) : option.display(mod.number(option));
                if (!option.keyBinding) {
                    Text.draw(canvas, "-", right, rowY + 9, 8, 0xFFFFFFFF);
                    Text.draw(canvas, "+", x + width - 23, rowY + 9, 8, 0xFFFFFFFF);
                }
                Text.drawCentered(canvas, value == null ? "Unbound" : value, right + 53, rowY + 9,
                        Text.fit(canvas, value == null ? "Unbound" : value, 7, 88, 4), 0xFFFFFFFF);
            } else if (row instanceof TextOption) {
                TextOption option = (TextOption) row;
                String value = editing == option ? draft + "_" : mod.text(option);
                if (value.isEmpty()) value = "(empty)";
                Text.draw(canvas, value, right, rowY + 9, Text.fit(canvas, value, 7, 114, 4), 0xFFFFFFFF);
            } else {
                Text.draw(canvas, "Run", right + 50, rowY + 9, 7, 0xFFFFFFFF);
            }
            rowY += ROW;
        }
        canvas.unclip();
        int maximum = Math.max(0, rows.size() * ROW - (height - 50));
        if (maximum > 0) {
            int track = height - 50;
            int thumb = Math.max(10, track * track / (track + maximum));
            canvas.roundedFill(x + width - 4, y + 30 + (track - thumb) * scroll / maximum, 2, thumb, 1, 0xA0FFFFFF);
        }
        String footer = !error.isEmpty() ? error : binding != null ? "Escape / Backspace: clear binding" : editing != null ? "Enter: save   Escape: cancel" : "";
        Text.draw(canvas, footer, x + 10, y + height - 13, 6, error.isEmpty() ? 0xFF999999 : 0xFFEBAFA3);
    }

    private void saveText() {
        if (editing != null) {
            mod.setText(editing, draft);
            editing = null;
        }
    }

    public void click(int mouseX, int mouseY, int button) {
        try {
            error = "";
            if (binding != null) {
                mod.setNumber(binding, button - 100);
                binding = null;
                return;
            }
            if (button != 0) return;
            saveText();
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height
                    || mouseX >= x + width - 30 && mouseY < y + 28) {
                open = false;
                return;
            }
            if (mouseY < y + 30 || mouseY >= y + height - 20) return;
            List<Object> rows = rows();
            int index = (mouseY - y - 30 + scroll) / ROW;
            if (index < 0 || index >= rows.size()) return;
            Object row = rows.get(index);
            if (row instanceof Option) mod.setOption((Option) row, !mod.option((Option) row));
            else if (row instanceof NumberOption) {
                NumberOption option = (NumberOption) row;
                if (option.keyBinding) binding = option;
                else if (mouseX >= x + width - 135 && mouseX < x + width - 106) mod.setNumber(option, mod.number(option) - option.step());
                else if (mouseX >= x + width - 38) mod.setNumber(option, mod.number(option) + option.step());
            } else if (row instanceof TextOption) {
                editing = (TextOption) row;
                draft = mod.text(editing);
                replace = true;
            } else ((ActionOption) row).run();
        } catch (IllegalArgumentException | IllegalStateException failure) {
            error = failure.getMessage() == null ? "Could not save settings" : failure.getMessage();
        }
    }

    public void scroll(int direction) {
        if (binding == null) scroll = Math.max(0, Math.min(Math.max(0, rows().size() * ROW - (height - 50)), scroll - Integer.signum(direction) * ROW));
    }

    public void key(char character, int code) {
        try {
            error = "";
            if (binding != null) {
                if (code == 0) return;
                mod.setNumber(binding, code == Keys.ESCAPE || code == Keys.BACKSPACE || code == 211 ? 0 : code);
                binding = null;
            } else if (editing != null) {
                if (code == Keys.ESCAPE) editing = null;
                else if (code == Keys.ENTER) saveText();
                else if (code == Keys.BACKSPACE) {
                    draft = replace || draft.isEmpty() ? "" : draft.substring(0, draft.length() - 1);
                    replace = false;
                } else if (character >= ' ' && !Character.isISOControl(character)) {
                    draft = editing.sanitize((replace ? "" : draft) + character);
                    replace = false;
                }
            } else if (code == Keys.ESCAPE || code == Keys.RIGHT_SHIFT) open = false;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            error = "Could not save settings";
        }
    }
}
