package comet.core.ui;

import comet.core.CometClient;
import comet.core.bridge.Canvas;
import comet.core.bridge.Keys;
import comet.core.mod.Category;
import comet.core.mod.Mod;
import comet.core.mod.Option;
import comet.core.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModMenu extends Screen {
    private static final int MARGIN = 10;
    private static final int HEADER = 26;
    private static final int SIDEBAR = 88;
    private static final int PAD = 8;
    private static final int CHIP = 14;
    private static final int GAP = 8;
    private static final int CARD_MIN = 88;
    private static final int CARD_HEIGHT = 104;
    private static final int ICON_AREA = 44;
    private static final int ROW = 18;
    private static final int SEARCH_WIDTH = 84;
    private static final int OPTIONS_WIDTH = 250;
    private static final int OPTION_ROW = 28;
    private static final int MAX_QUERY = 24;
    private static final float TRACKING = 0.9F;
    private static final int ACCENT = 0xFFACE77A;
    private static final int CHIP_FILL = Widgets.FILL;
    private final CometClient client;
    private final Button edit = new Button("Edit HUD layout", SIDEBAR - 2 * PAD, 18);
    private final PresetPanel presets;
    private Category filter;
    private String query = "";
    private boolean searching;
    private int scroll;
    private Mod options;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean openingShift;

    public ModMenu(CometClient client) {
        this.client = client;
        presets = new PresetPanel(client);
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
    public void resize(int width, int height) {
        super.resize(width, height);
        panelWidth = Math.min(600, width - 2 * MARGIN);
        panelHeight = Math.min(370, height - 2 * MARGIN);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        edit.place(panelX + PAD, panelY + panelHeight - PAD - edit.height);
        presets.resize(panelX + PAD, panelY + HEADER + PAD, SIDEBAR - 2 * PAD,
                panelHeight - HEADER - 3 * PAD - edit.height - 4);
    }

    private List<Mod> visible() {
        List<Mod> result = new ArrayList<Mod>();
        String needle = query.toLowerCase(Locale.ROOT);
        for (Mod mod : client.mods().all()) {
            boolean category = filter == null || mod.category() == filter;
            if (category && mod.name().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(mod);
            }
        }
        return result;
    }

    private int contentX() {
        return panelX + SIDEBAR + PAD;
    }

    private int contentWidth() {
        return panelWidth - SIDEBAR - 2 * PAD - 6;
    }

    private boolean stackedSearch() {
        return contentWidth() < 300;
    }

    private int searchX() {
        return stackedSearch() ? contentX() : contentX() + contentWidth() - SEARCH_WIDTH;
    }

    private int searchY() {
        return panelY + HEADER + PAD + (stackedSearch() ? CHIP + 4 : 0);
    }

    private int searchWidth() {
        return stackedSearch() ? contentWidth() : SEARCH_WIDTH;
    }

    private int gridTop() {
        return panelY + HEADER + PAD + CHIP + GAP + (stackedSearch() ? CHIP + 4 : 0);
    }

    private int gridBottom() {
        return panelY + panelHeight - PAD;
    }

    private int columns() {
        return Math.max(1, Math.min(3, (contentWidth() + GAP) / (CARD_MIN + GAP)));
    }

    private int cardWidth() {
        return (contentWidth() - GAP * (columns() - 1)) / columns();
    }

    private int cardX(int index) {
        return contentX() + (cardWidth() + GAP) * (index % columns());
    }

    private int cardY(int index) {
        return gridTop() + (CARD_HEIGHT + GAP) * (index / columns()) - scroll;
    }

    private int maxScroll(int count) {
        int rows = (count + columns() - 1) / columns();
        return Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP - (gridBottom() - gridTop()));
    }

    @Override
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        if (!client.host().keyDown(Keys.RIGHT_SHIFT)) {
            openingShift = false;
        }
        canvas.fill(0, 0, width, height, 0x50000000);
        Widgets.pill(canvas, panelX, panelY, panelWidth, panelHeight, 0xCA101010);
        drawHeader(canvas, mouseX, mouseY);
        drawSidebar(canvas, mouseX, mouseY);
        drawFilters(canvas, mouseX, mouseY);
        List<Mod> mods = visible();
        scroll = Math.min(scroll, maxScroll(mods.size()));
        canvas.clip(contentX(), gridTop(), contentWidth(), gridBottom() - gridTop());
        for (int index = 0; index < mods.size(); index++) {
            drawCard(canvas, mods.get(index), cardX(index), cardY(index), options == null && !presets.modal() ? mouseX : -1, mouseY);
        }
        canvas.unclip();
        int maxScroll = maxScroll(mods.size());
        if (maxScroll > 0) {
            int track = gridBottom() - gridTop();
            int thumb = Math.max(12, track * track / (track + maxScroll));
            int x = contentX() + contentWidth() + 4;
            canvas.roundedFill(x, gridTop(), 2, track, 1, 0x25FFFFFF);
            canvas.roundedFill(x, gridTop() + (track - thumb) * scroll / maxScroll, 2, thumb, 1, 0xA0FFFFFF);
        }
        if (mods.isEmpty()) {
            Text.drawCentered(canvas, "No mods match.", contentX() + contentWidth() / 2.0F, gridTop() + 20, 8, 0xFF8A8A8A);
        }
        if (options != null) {
            drawOptions(canvas, mouseX, mouseY);
        }
        if (presets.modal()) {
            presets.drawDialog(canvas, mouseX, mouseY);
        }
    }

    private void drawHeader(Canvas canvas, int mouseX, int mouseY) {
        int centerY = panelY + HEADER / 2;
        canvas.image("comet", "mark.png", panelX + PAD, centerY - 8, 16, 16);
        Text.drawSpaced(canvas, "COMET", panelX + PAD + 20, centerY - 5, 9, 0xFFFFFFFF, 1.4F);
        Text.draw(canvas, "Mods", contentX(), centerY - 4, 9, 0xFFFFFFFF);
        Text.draw(canvas, client.host().version(), contentX() + 30, centerY - 2, 6, 0xFF9C9C9C);
        canvas.fill(panelX, panelY + HEADER, panelWidth, 1, 0x30FFFFFF);
        int closeX = panelX + panelWidth - PAD - CHIP;
        boolean hover = inside(mouseX, mouseY, closeX, centerY - CHIP / 2, CHIP, CHIP);
        Widgets.pill(canvas, closeX, centerY - CHIP / 2, CHIP, CHIP, hover ? Widgets.HOVER : CHIP_FILL);
        ModIcons.draw(canvas, "close", closeX + 2, centerY - 5, 10);
    }

    private void drawSidebar(Canvas canvas, int mouseX, int mouseY) {
        int top = panelY + HEADER;
        canvas.fill(panelX + SIDEBAR, top, 1, panelHeight - HEADER, 0x30FFFFFF);
        presets.draw(canvas, options == null ? mouseX : -1, mouseY);
        edit.draw(canvas, options == null && !presets.modal() ? mouseX : -1, mouseY);
    }

    private void drawFilters(Canvas canvas, int mouseX, int mouseY) {
        int y = panelY + HEADER + PAD;
        int x = contentX();
        boolean hover = options == null && !presets.modal();
        x += chip(canvas, x, y, "ALL", filter == null, hover && inside(mouseX, mouseY, x, y, chipWidth(canvas, "ALL"), CHIP)) + 4;
        for (Category category : Category.values()) {
            int chipWidth = chipWidth(canvas, category.label);
            x += chip(canvas, x, y, category.label, filter == category, hover && inside(mouseX, mouseY, x, y, chipWidth, CHIP)) + 4;
        }
        int searchX = searchX();
        y = searchY();
        Widgets.pill(canvas, searchX, y, searchWidth(), CHIP, searching ? Widgets.HOVER : CHIP_FILL);
        ModIcons.draw(canvas, "search", searchX + 4, y + 2, 10);
        String shown = query.isEmpty() && !searching ? "Search" : query + (searching ? "_" : "");
        while (Text.width(canvas, shown, 6) > searchWidth() - 23) {
            shown = shown.substring(1);
        }
        Text.draw(canvas, shown, searchX + 18, y + 4, 6, query.isEmpty() && !searching ? 0xFF7A7A7A : 0xFFFFFFFF);
    }

    private int chipWidth(Canvas canvas, String label) {
        return Math.round(Text.spacedWidth(canvas, label, 6, TRACKING)) + 12;
    }

    private int chip(Canvas canvas, int x, int y, String label, boolean selected, boolean hover) {
        int chipWidth = chipWidth(canvas, label);
        Widgets.pill(canvas, x, y, chipWidth, CHIP, selected ? 0xC02C3825 : hover ? Widgets.HOVER : CHIP_FILL);
        Text.drawSpaced(canvas, label, x + 6, y + 4, 6, selected ? ACCENT : 0xFFD7D7D7, TRACKING);
        return chipWidth;
    }

    private void drawCard(Canvas canvas, Mod mod, int x, int y, int mouseX, int mouseY) {
        int cardWidth = cardWidth();
        boolean enabled = client.mods().isEnabled(mod);
        Widgets.pill(canvas, x, y, cardWidth, CARD_HEIGHT, Widgets.FILL);
        ModIcons.draw(canvas, mod.id(), x + (cardWidth - 30) / 2, y + (ICON_AREA - 30) / 2, 30);
        float nameSize = Text.fit(canvas, mod.name(), 8, cardWidth - 8, 5);
        Text.drawCentered(canvas, mod.name(), x + cardWidth / 2.0F, y + ICON_AREA + 2, nameSize, 0xFFDDDDDD);
        int optionsY = y + CARD_HEIGHT - 2 * ROW - 12;
        boolean hasOptions = !mod.options().isEmpty();
        boolean hoverOptions = hasOptions && inside(mouseX, mouseY, x + 6, optionsY, cardWidth - 12, ROW);
        Widgets.pill(canvas, x + 6, optionsY, cardWidth - 12, ROW, !hasOptions ? Widgets.DISABLED : hoverOptions ? Widgets.HOVER : Widgets.FILL);
        float optionsWidth = Text.width(canvas, "Options", 7) + 15;
        int optionsX = Math.round(x + (cardWidth - optionsWidth) / 2);
        ModIcons.draw(canvas, "settings", optionsX, optionsY + 3, 11);
        Text.draw(canvas, "Options", optionsX + 15, optionsY + 5, 7, hasOptions ? 0xFFE0E0E0 : 0xFF666666);
        int toggleY = y + CARD_HEIGHT - ROW - 6;
        boolean hoverToggle = inside(mouseX, mouseY, x + 6, toggleY, cardWidth - 12, ROW);
        Widgets.pill(canvas, x + 6, toggleY, cardWidth - 12, ROW, hoverToggle ? 0xD0343E2D : enabled ? 0xB8253020 : Widgets.FILL);
        String state = enabled ? "Enabled" : "Disabled";
        float stateWidth = Text.width(canvas, state, 7) + 10;
        int stateX = Math.round(x + (cardWidth - stateWidth) / 2);
        canvas.roundedFill(stateX, toggleY + 7, 3, 3, 1, enabled ? ACCENT : 0xFF777777);
        Text.draw(canvas, state, stateX + 10, toggleY + 5, 7, enabled ? 0xFFDAEACA : 0xFF999999);
    }

    private int optionsHeight() {
        return 30 + options.options().size() * OPTION_ROW + PAD;
    }

    private int optionsX() {
        return (width - OPTIONS_WIDTH) / 2;
    }

    private int optionsY() {
        return (height - optionsHeight()) / 2;
    }

    private void drawOptions(Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(0, 0, width, height, 0x60000000);
        int x = optionsX();
        int y = optionsY();
        Widgets.pill(canvas, x, y, OPTIONS_WIDTH, optionsHeight(), 0xF4141414);
        ModIcons.draw(canvas, options.id(), x + PAD, y + PAD - 2, 14);
        Text.draw(canvas, options.name(), x + PAD + 20, y + PAD, 9, 0xFFFFFFFF);
        int closeX = x + OPTIONS_WIDTH - PAD - CHIP;
        boolean hover = inside(mouseX, mouseY, closeX, y + PAD - 2, CHIP, CHIP);
        Widgets.pill(canvas, closeX, y + PAD - 2, CHIP, CHIP, hover ? Widgets.HOVER : CHIP_FILL);
        ModIcons.draw(canvas, "close", closeX + 2, y + PAD, 10);
        int rowY = y + 30;
        for (Option option : options.options()) {
            Widgets.pill(canvas, x + PAD, rowY + 2, OPTIONS_WIDTH - 2 * PAD, OPTION_ROW - 6,
                    inside(mouseX, mouseY, x + PAD, rowY + 2, OPTIONS_WIDTH - 2 * PAD, OPTION_ROW - 6) ? Widgets.HOVER : Widgets.FILL);
            ModIcons.draw(canvas, option.id, x + PAD + 5, rowY + 6, 13);
            float labelSize = Text.fit(canvas, option.label, 7, OPTIONS_WIDTH - 2 * PAD - Widgets.TOGGLE_WIDTH - 38, 5);
            Text.draw(canvas, option.label, x + PAD + 24, rowY + (OPTION_ROW - Text.height(canvas, option.label, labelSize)) / 2 - 1, labelSize, 0xFFDDDDDD);
            Widgets.toggle(canvas, x + OPTIONS_WIDTH - PAD - Widgets.TOGGLE_WIDTH - 5, rowY + (OPTION_ROW - Widgets.TOGGLE_HEIGHT) / 2 - 1, options.option(option));
            rowY += OPTION_ROW;
        }
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (presets.modal()) {
            presets.clickDialog(client.host().canvas(), mouseX, mouseY);
            return;
        }
        if (options != null) {
            clickOptions(mouseX, mouseY);
            return;
        }
        int centerY = panelY + HEADER / 2;
        if (inside(mouseX, mouseY, panelX + panelWidth - PAD - CHIP, centerY - CHIP / 2, CHIP, CHIP)) {
            client.host().closeScreen();
            return;
        }
        if (edit.contains(mouseX, mouseY)) {
            client.openHudEditor();
            return;
        }
        if (presets.mouseDown(mouseX, mouseY)) {
            searching = false;
            return;
        }
        int filterY = panelY + HEADER + PAD;
        searching = inside(mouseX, mouseY, searchX(), searchY(), searchWidth(), CHIP);
        if (searching) {
            return;
        }
        if (inside(mouseX, mouseY, contentX(), filterY, stackedSearch() ? contentWidth() : contentWidth() - SEARCH_WIDTH, CHIP)) {
            clickFilter(mouseX);
            return;
        }
        if (!inside(mouseX, mouseY, contentX(), gridTop(), contentWidth(), gridBottom() - gridTop())) {
            return;
        }
        List<Mod> mods = visible();
        for (int index = 0; index < mods.size(); index++) {
            int x = cardX(index);
            int y = cardY(index);
            if (!inside(mouseX, mouseY, x, y, cardWidth(), CARD_HEIGHT)) {
                continue;
            }
            Mod mod = mods.get(index);
            if (inside(mouseX, mouseY, x + 6, y + CARD_HEIGHT - ROW - 6, cardWidth() - 12, ROW)) {
                client.mods().toggle(mod);
            } else if (inside(mouseX, mouseY, x + 6, y + CARD_HEIGHT - 2 * ROW - 12, cardWidth() - 12, ROW) && !mod.options().isEmpty()) {
                options = mod;
            }
            return;
        }
    }

    private void clickFilter(int mouseX) {
        Canvas canvas = client.host().canvas();
        int x = contentX();
        int allWidth = chipWidth(canvas, "ALL");
        if (mouseX < x + allWidth) {
            filter = null;
            scroll = 0;
            return;
        }
        x += allWidth + 4;
        for (Category category : Category.values()) {
            int chipWidth = chipWidth(canvas, category.label);
            if (mouseX >= x && mouseX < x + chipWidth) {
                filter = category;
                scroll = 0;
                return;
            }
            x += chipWidth + 4;
        }
    }

    private void clickOptions(int mouseX, int mouseY) {
        int x = optionsX();
        int y = optionsY();
        if (inside(mouseX, mouseY, x + OPTIONS_WIDTH - PAD - CHIP, y + PAD - 2, CHIP, CHIP) || !inside(mouseX, mouseY, x, y, OPTIONS_WIDTH, optionsHeight())) {
            options = null;
            return;
        }
        int rowY = y + 30;
        for (Option option : options.options()) {
            if (inside(mouseX, mouseY, x + PAD, rowY + 2, OPTIONS_WIDTH - 2 * PAD, OPTION_ROW - 6)) {
                options.setOption(option, !options.option(option));
                return;
            }
            rowY += OPTION_ROW;
        }
    }

    @Override
    public void scroll(int mouseX, int mouseY, int direction) {
        if (options == null && !presets.modal() && direction != 0) {
            if (presets.scroll(mouseX, mouseY, direction)) {
                return;
            }
            if (!inside(mouseX, mouseY, contentX(), gridTop(), contentWidth() + 6, gridBottom() - gridTop())) {
                return;
            }
            scroll = Math.max(0, Math.min(maxScroll(visible().size()), scroll - direction * 24));
        }
    }

    @Override
    public boolean key(char character, int code) {
        if (code == Keys.RIGHT_SHIFT && openingShift) {
            return true;
        }
        if (presets.modal()) {
            presets.key(character, code);
            return true;
        }
        if (code == Keys.ESCAPE) {
            if (options != null) {
                options = null;
            } else if (searching) {
                searching = false;
            } else {
                client.host().closeScreen();
            }
            return true;
        }
        if (searching) {
            if (code == Keys.BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            } else if (character >= ' ' && character < 127 && query.length() < MAX_QUERY) {
                query += character;
            }
            scroll = 0;
            return true;
        }
        if (code == Keys.RIGHT_SHIFT) {
            client.host().closeScreen();
            return true;
        }
        return false;
    }
}
