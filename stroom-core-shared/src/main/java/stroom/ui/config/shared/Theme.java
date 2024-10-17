package stroom.ui.config.shared;

import stroom.util.shared.GwtNullSafe;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stroom UI themes
 */
public enum Theme {

    LIGHT("Light", "stroom-theme-light", ThemeType.LIGHT),
    DARK("Dark", "stroom-theme-dark", ThemeType.DARK),
    ;

    static final Theme DEFAULT_THEME = DARK;
    static final ThemeType DEFAULT_THEME_TYPE;

    private static final Map<String, Theme> THEME_NAME_TO_THEME_MAP;

    static {
        THEME_NAME_TO_THEME_MAP = Arrays.stream(Theme.values())
                .collect(Collectors.toMap(
                        Theme::getThemeName,
                        Function.identity()));

        DEFAULT_THEME_TYPE = THEME_NAME_TO_THEME_MAP.get(DEFAULT_THEME.getThemeName()).getThemeType();

        for (final ThemeType themeType : ThemeType.values()) {
            final boolean isFound = Arrays.stream(Theme.values())
                    .anyMatch(theme -> theme.getThemeType().equals(themeType));
            if (!isFound) {
                throw new RuntimeException("No Theme found with themeType: " + themeType);
            }
        }
    }

    private final String themeName;
    private final String cssClass;
    private final ThemeType themeType;

    Theme(final String themeName, final String cssClass, final ThemeType themeType) {
        this.themeName = themeName;
        this.cssClass = cssClass;
        this.themeType = themeType;
    }

    public String getThemeName() {
        return themeName;
    }

    public ThemeType getThemeType() {
        return themeType;
    }

    public String getCssClass() {
        return cssClass;
    }

    public static Theme fromName(final String themeName) {
        final Theme theme = THEME_NAME_TO_THEME_MAP.get(themeName);
        return GwtNullSafe.requireNonNullElse(theme, DEFAULT_THEME);
    }

    public static List<String> getThemeNames() {
        return THEME_NAME_TO_THEME_MAP.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public static ThemeType getThemeType(final String themeName) {
        final ThemeType themeType = GwtNullSafe.get(
                themeName,
                THEME_NAME_TO_THEME_MAP::get,
                Theme::getThemeType);
        Objects.requireNonNull(themeType);
        return themeType;
    }

    public static String getClassName(final String theme) {
        return GwtNullSafe.getOrElse(
                theme,
                THEME_NAME_TO_THEME_MAP::get,
                Theme::getCssClass,
                "");
    }

    public static boolean isValidTheme(final String themeName) {
        return themeName != null && THEME_NAME_TO_THEME_MAP.containsKey(themeName);
    }
}
