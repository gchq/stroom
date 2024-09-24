package stroom.ui.config.shared;

public enum ThemeType {
    DARK,
    LIGHT;

    public boolean isDark() {
        return DARK.equals(this);
    }

    public boolean isLight() {
        return LIGHT.equals(this);
    }
}
