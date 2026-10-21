package comet.core.mod;

import comet.core.bridge.Canvas;
import comet.core.bridge.Control;
import comet.core.bridge.GameHost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Keystrokes extends HudMod {
    public static final Option MOVEMENT = new Option("keyStrokesMovement", "Movement row", true);
    public static final Option MOUSE = new Option("mouseButtons", "Mouse buttons", true);
    public static final Option JUMP = new Option("jumpKey", "Jump key", true);
    public static final Option LEFT_CPS = new Option("leftCPS", "Left click CPS", false);
    public static final Option RIGHT_CPS = new Option("rightCPS", "Right click CPS", false);
    public static final Option ARROWS = new Option("useArrows", "Arrow labels", false);
    public static final Option JUMP_BAR = new Option("jumpBar", "Jump bar", false);

    public static final NumberOption MODE = new NumberOption("keystrokesMode", "Mode", 0, 0, 1, "", "Grouped", "Individual");
    public static final NumberOption BOX_SIZE = new NumberOption("boxSize", "Box size", 22, 10, 32, "px");
    public static final NumberOption JUMP_THICKNESS = NumberOption.decimal("spacebarThickness", "Jump bar thickness", 3, 1, 4.25, 2);

    private static final TextOption CUSTOM_IDS = new TextOption("customKeys", "Custom keys", "", 240);
    private static final NumberOption NEXT_CUSTOM = new NumberOption("nextCustomKey", "Next custom key", 1, 1, 1000, "");
    private static final Control[] MOVEMENT_KEYS = {Control.FORWARD, Control.LEFT, Control.BACK, Control.RIGHT};
    private static final Control[] MOUSE_KEYS = {Control.ATTACK, Control.USE};
    private static final String[] ARROW_LABELS = {"\u2191", "\u2190", "\u2193", "\u2192"};
    private static final int INDIVIDUAL = 1;
    private static final int GAP = 2;
    private static final int MAX_CUSTOM = 16;
    private static final int MAX_CUSTOM_NUMBER = 999;
    private static final int MAX_CUSTOM_DIGITS = 3;
    private static final int SHORT_LABEL = 14;
    private static final int PRESSED_COLOR = 0x907CA85A;
    private static final int BAR_COLOR = 0xFFFFFFFF;
    private static final float SUBTITLE_SCALE = 0.6F;

    private final GameHost host;
    private final ClickStats stats;
    private final List<Stock> stock = new ArrayList<Stock>();
    private final List<Custom> customs = new ArrayList<Custom>();
    private String customSource;

    public Keystrokes(GameHost host) {
        this(host, new ClickStats());
    }

    public Keystrokes(GameHost host, ClickStats stats) {
        this.host = host;
        this.stats = stats;
        for (Control control : MOVEMENT_KEYS) {
            stock.add(bindElement(new Stock(stock.size(), control)));
        }
        stock.add(bindElement(new Stock(stock.size(), Control.JUMP)));
        for (Control control : MOUSE_KEYS) {
            stock.add(bindElement(new Stock(stock.size(), control)));
        }
    }

    @Override
    public String id() {
        return "keystrokes";
    }

    @Override
    public String name() {
        return "Keystrokes";
    }

    @Override
    public String description() {
        return "Shows physical movement and mouse inputs.";
    }

    @Override
    public float defaultX() {
        return 0.85F;
    }

    @Override
    public float defaultY() {
        return 0.02F;
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(MOVEMENT, MOUSE, JUMP, LEFT_CPS, RIGHT_CPS, ARROWS, JUMP_BAR);
    }

    @Override
    public List<NumberOption> numbers() {
        sync();
        List<NumberOption> options = new ArrayList<NumberOption>();
        options.add(MODE);
        options.add(BOX_SIZE);
        options.add(JUMP_THICKNESS);
        for (Custom custom : customs) {
            options.add(custom.keyOption);
        }
        return options;
    }

    @Override
    public List<TextOption> texts() {
        sync();
        List<TextOption> options = new ArrayList<TextOption>();
        for (Custom custom : customs) {
            options.add(custom.textOption);
        }
        return options;
    }

    @Override
    public List<ActionOption> actions() {
        sync();
        List<ActionOption> actions = new ArrayList<ActionOption>();
        actions.add(new ActionOption("addCustomKey", "Add key", () -> addCustom()));
        for (Custom custom : customs) {
            actions.add(new ActionOption("removeCustomKey." + custom.number, "Remove key " + custom.number, () -> removeCustom(custom)));
        }
        return actions;
    }

    @Override
    public List<HudMod> elements() {
        sync();
        if (!individual()) {
            return super.elements();
        }
        List<HudMod> elements = new ArrayList<HudMod>(stock.size() + customs.size());
        elements.addAll(stock);
        elements.addAll(customs);
        return elements;
    }

    @Override
    public void preview(boolean enabled) {
        super.preview(enabled);
        sync();
        for (Stock key : stock) {
            key.preview(enabled);
        }
        for (Custom custom : customs) {
            custom.preview(enabled);
        }
    }

    @Override
    public void reset() {
        sync();
    }

    @Override
    public int number(NumberOption option) {
        Mod owner = owner(option);
        return owner == null ? super.number(option) : owner.number(option);
    }

    @Override
    public void setNumber(NumberOption option, int value) {
        Mod owner = owner(option);
        if (owner == null) {
            super.setNumber(option, value);
        } else {
            owner.setNumber(option, value);
        }
    }

    @Override
    public String text(TextOption option) {
        Mod owner = owner(option);
        return owner == null ? super.text(option) : owner.text(option);
    }

    @Override
    public void setText(TextOption option, String value) {
        Mod owner = owner(option);
        if (owner == null) {
            super.setText(option, value);
        } else {
            owner.setText(option, value);
        }
    }

    public boolean pressed(Control control) {
        return active(host.controlKey(control));
    }

    @Override
    public int width(Canvas canvas) {
        if (individual()) {
            return 0;
        }
        int width = 0;
        if (option(MOVEMENT) || option(JUMP)) {
            width = movementWidth();
        }
        if (option(MOUSE)) {
            width = Math.max(width, 2 * mouseWidth() + GAP);
        }
        return width;
    }

    @Override
    public int height(Canvas canvas) {
        if (individual()) {
            return 0;
        }
        int[] rows = {option(MOVEMENT) ? 2 * box() + GAP : 0, option(JUMP) ? jumpHeight() : 0, option(MOUSE) ? box() : 0};
        int offset = 0;
        int height = 0;
        for (int row : rows) {
            if (row > 0) {
                height = offset + row;
            }
            offset += row + GAP;
        }
        return height;
    }

    @Override
    public void render(Canvas canvas) {
        if (individual()) {
            return;
        }
        int size = box();
        int offset = 0;
        if (option(MOVEMENT)) {
            drawControl(canvas, Control.FORWARD, size + GAP, 0);
            drawControl(canvas, Control.LEFT, 0, size + GAP);
            drawControl(canvas, Control.BACK, size + GAP, size + GAP);
            drawControl(canvas, Control.RIGHT, 2 * (size + GAP), size + GAP);
            offset += 2 * size + GAP;
        }
        offset += GAP;
        if (option(JUMP)) {
            drawControl(canvas, Control.JUMP, 0, offset);
            offset += jumpHeight();
        }
        offset += GAP;
        if (option(MOUSE)) {
            drawControl(canvas, Control.ATTACK, 0, offset);
            drawControl(canvas, Control.USE, mouseWidth() + GAP, offset);
        }
    }

    private void sync() {
        for (Stock key : stock) {
            bindElement(key);
        }
        String raw = text(CUSTOM_IDS);
        if (raw.equals(customSource)) {
            return;
        }
        customSource = raw;
        customs.clear();
        for (int number : parseCustoms(raw)) {
            customs.add(bindElement(new Custom(number, stock.size() + customs.size())));
        }
    }

    private void addCustom() {
        sync();
        if (customs.size() >= MAX_CUSTOM) {
            throw new IllegalArgumentException("Custom key limit reached (" + MAX_CUSTOM + ").");
        }
        int number = number(NEXT_CUSTOM);
        for (Custom custom : customs) {
            number = Math.max(number, custom.number + 1);
        }
        if (number > MAX_CUSTOM_NUMBER) {
            throw new IllegalArgumentException("Custom key numbers are exhausted.");
        }
        List<Integer> numbers = customNumbers();
        numbers.add(number);
        String value = CUSTOM_IDS.sanitize(format(numbers));
        setNumber(NEXT_CUSTOM, number + 1);
        setText(CUSTOM_IDS, value);
        customSource = value;
        customs.add(bindElement(new Custom(number, stock.size() + customs.size())));
    }

    private void removeCustom(Custom custom) {
        List<Integer> numbers = customNumbers();
        numbers.remove(Integer.valueOf(custom.number));
        String value = CUSTOM_IDS.sanitize(format(numbers));
        setText(CUSTOM_IDS, value);
        customSource = value;
        customs.remove(custom);
    }

    private List<Integer> customNumbers() {
        List<Integer> numbers = new ArrayList<Integer>();
        for (Custom custom : customs) {
            numbers.add(custom.number);
        }
        return numbers;
    }

    private static String format(List<Integer> numbers) {
        StringBuilder value = new StringBuilder();
        for (int number : numbers) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(number);
        }
        return value.toString();
    }

    private static List<Integer> parseCustoms(String value) {
        List<Integer> numbers = new ArrayList<Integer>();
        if (value == null) {
            return numbers;
        }
        for (String token : value.split(",")) {
            String digits = token.trim();
            boolean valid = !digits.isEmpty() && digits.length() <= MAX_CUSTOM_DIGITS;
            for (int index = 0; valid && index < digits.length(); index++) {
                valid = digits.charAt(index) >= '0' && digits.charAt(index) <= '9';
            }
            if (!valid) {
                if (!digits.isEmpty()) {
                    System.err.println("[Comet] Ignoring invalid keystroke key id: " + digits);
                }
                continue;
            }
            int number = Integer.parseInt(digits);
            if (number < 1 || numbers.contains(number) || numbers.size() >= MAX_CUSTOM) {
                continue;
            }
            numbers.add(number);
        }
        return numbers;
    }

    private Mod owner(NumberOption option) {
        for (Custom custom : customs) {
            if (custom.keyOption == option) {
                return custom;
            }
        }
        return null;
    }

    private Mod owner(TextOption option) {
        for (Custom custom : customs) {
            if (custom.textOption == option) {
                return custom;
            }
        }
        return null;
    }

    private boolean individual() {
        return number(MODE) == INDIVIDUAL;
    }

    private int box() {
        return number(BOX_SIZE);
    }

    private int movementWidth() {
        return 3 * box() + 2 * GAP;
    }

    private int mouseWidth() {
        return (3 * box() + GAP) / 2;
    }

    private int jumpHeight() {
        return Math.max(8, box() * 3 / 4);
    }

    private int cellWidth(Control control) {
        if (control == Control.JUMP) {
            return movementWidth();
        }
        if (control == Control.ATTACK || control == Control.USE) {
            return mouseWidth();
        }
        return box();
    }

    private int cellHeight(Control control) {
        return control == Control.JUMP ? jumpHeight() : box();
    }

    private int customWidth(Canvas canvas, String caption) {
        int width = canvas.textWidth(caption);
        return width + 4 < box() ? box() : width + box();
    }

    private boolean active(int key) {
        return !preview && host.inWorld() && !host.screenOpen() && key != 0 && host.keyDown(key);
    }

    private void drawControl(Canvas canvas, Control control, int x, int y) {
        boolean bar = control == Control.JUMP && option(JUMP_BAR);
        drawCell(canvas, x, y, cellWidth(control), cellHeight(control), bar ? null : label(control), pressed(control), cps(control));
    }

    private void drawCell(Canvas canvas, int x, int y, int width, int height, String caption, boolean down, String cps) {
        canvas.push(x, y, 1);
        background(canvas, width, height);
        if (down) {
            canvas.roundedFill(0, 0, width, height, 3, PRESSED_COLOR);
        }
        if (caption == null) {
            float thickness = (float) decimal(JUMP_THICKNESS);
            int length = Math.max(1, width / 3);
            canvas.push((width - length) / 2, Math.round((height - thickness) / 2), 0.01F);
            canvas.fill(0, 0, length * 100, number(JUMP_THICKNESS), BAR_COLOR);
            canvas.pop();
        } else {
            drawCaption(canvas, width, height, caption, cps);
        }
        canvas.pop();
    }

    private void drawCaption(Canvas canvas, int width, int height, String caption, String cps) {
        if (!caption.isEmpty()) {
            float scale = Math.min(1F, (width - 4F) / Math.max(1, canvas.textWidth(caption)));
            int y = Math.round((height - canvas.textHeight() * scale) / 2F);
            if (cps != null) {
                y = Math.max(0, y - 3);
            }
            canvas.push(Math.round((width - canvas.textWidth(caption) * scale) / 2F), y, scale);
            text(canvas, caption, 0, 0);
            canvas.pop();
        }
        if (cps != null) {
            int subWidth = Math.round(canvas.textWidth(cps) * SUBTITLE_SCALE);
            int subY = Math.max(0, height - Math.round(canvas.textHeight() * SUBTITLE_SCALE) - 1);
            canvas.push(Math.max(0, (width - subWidth) / 2), subY, SUBTITLE_SCALE);
            text(canvas, cps, 0, 0);
            canvas.pop();
        }
    }

    private String label(Control control) {
        if (movement(control) && option(ARROWS)) {
            return ARROW_LABELS[control.ordinal()];
        }
        String name = host.keyName(host.controlKey(control));
        if (name == null || name.isEmpty()) {
            name = "--";
        }
        return box() < SHORT_LABEL && name.length() > 1 ? name.substring(0, 1) : name;
    }

    private String cps(Control control) {
        if (control == Control.ATTACK) {
            return option(LEFT_CPS) ? Integer.toString(stats.left(false)) : null;
        }
        if (control == Control.USE) {
            return option(RIGHT_CPS) ? Integer.toString(stats.right()) : null;
        }
        return null;
    }

    private static boolean movement(Control control) {
        return control == Control.FORWARD || control == Control.LEFT || control == Control.BACK || control == Control.RIGHT;
    }

    private abstract class Cell extends HudMod {
        private final int index;

        Cell(int index) {
            this.index = index;
        }

        abstract boolean rowEnabled();

        @Override
        public boolean visible() {
            return individual() && rowEnabled();
        }

        @Override
        public String description() {
            return Keystrokes.this.description();
        }

        @Override
        public float defaultX() {
            return Keystrokes.this.defaultX() + 0.04F * (index % 3);
        }

        @Override
        public float defaultY() {
            return Keystrokes.this.defaultY() + 0.07F * (index / 3);
        }
    }

    private final class Stock extends Cell {
        private final Control control;

        Stock(int index, Control control) {
            super(index);
            this.control = control;
        }

        @Override
        public String id() {
            return "keystrokes." + control.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String name() {
            return control.name();
        }

        @Override
        boolean rowEnabled() {
            if (control == Control.JUMP) {
                return Keystrokes.this.option(JUMP);
            }
            if (control == Control.ATTACK || control == Control.USE) {
                return Keystrokes.this.option(MOUSE);
            }
            return Keystrokes.this.option(MOVEMENT);
        }

        @Override
        public int width(Canvas canvas) {
            return cellWidth(control);
        }

        @Override
        public int height(Canvas canvas) {
            return cellHeight(control);
        }

        @Override
        public void render(Canvas canvas) {
            drawControl(canvas, control, 0, 0);
        }
    }

    private final class Custom extends Cell {
        private final int number;
        private final NumberOption keyOption;
        private final TextOption textOption;

        Custom(int number, int index) {
            super(index);
            this.number = number;
            this.keyOption = NumberOption.keybind("customKey", "Key " + number);
            this.textOption = new TextOption("customText", "Text " + number, "", 16);
        }

        @Override
        public String id() {
            return "keystrokes.custom." + number;
        }

        @Override
        public String name() {
            return "Custom key " + number;
        }

        @Override
        boolean rowEnabled() {
            return true;
        }

        @Override
        public int width(Canvas canvas) {
            return customWidth(canvas, customLabel());
        }

        @Override
        public int height(Canvas canvas) {
            return box();
        }

        @Override
        public void render(Canvas canvas) {
            String caption = customLabel();
            drawCell(canvas, 0, 0, customWidth(canvas, caption), box(), caption, active(number(keyOption)), null);
        }

        private String customLabel() {
            String caption = text(textOption).trim();
            if (!caption.isEmpty()) {
                return caption;
            }
            int key = number(keyOption);
            if (key == 0) {
                return "NONE";
            }
            String name = host.keyName(key);
            return name == null || name.isEmpty() ? "NONE" : name.toUpperCase(Locale.ROOT);
        }
    }
}
