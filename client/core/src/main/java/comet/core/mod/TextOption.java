package comet.core.mod;

public final class TextOption {
    public final String id;
    public final String label;
    public final String defaultValue;
    public final int maxLength;

    public TextOption(String id, String label, String defaultValue, int maxLength) {
        this.id = id;
        this.label = label;
        this.maxLength = maxLength;
        this.defaultValue = sanitize(defaultValue);
    }

    public String sanitize(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length() && result.length() < maxLength; index++) {
            char character = value.charAt(index);
            if (!Character.isISOControl(character) && character != '\u00a7') result.append(character);
        }
        return result.toString();
    }
}
