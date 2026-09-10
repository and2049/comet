package comet.core.mod;

public enum GlobalSetting {
    HUD_BACKGROUND("hudBackground", "HUD background", "HUD", true),
    TEXT_SHADOW("textShadow", "Text shadow", "HUD", true),
    BORDERLESS_FULLSCREEN("borderlessFullscreen", "Borderless fullscreen", "General", false),
    RAW_MOUSE_INPUT("rawMouseInput", "Raw mouse input", "Controls", false),
    DISABLE_HOTBAR_SCROLLING("disableHotbarScrolling", "Disable hotbar scrolling", "Controls", false);

    public final String id;
    public final String label;
    public final String category;
    public final boolean defaultValue;

    GlobalSetting(String id, String label, String category, boolean defaultValue) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.defaultValue = defaultValue;
    }
}
