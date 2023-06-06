package stroom.ui.config.shared;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Themes {

    public static final String THEME_NAME_DARK = "Dark";
    public static final String THEME_NAME_LIGHT = "Light";
    private static final Map<String, String> THEME_TO_CLASS_MAP = new HashMap<>();
    private static final Map<String, ThemeType> THEME_TO_TYPE_MAP = new HashMap<>();

    static {
        putTheme(THEME_NAME_LIGHT, "stroom-theme-light", ThemeType.LIGHT);
        putTheme(THEME_NAME_DARK, "stroom-theme-dark", ThemeType.DARK);
//        themeMap.put("Dark 2", "stroom-theme-dark stroom-theme-dark2");
    }

    private static void putTheme(final String name, final String clazz, final ThemeType themeType) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(clazz);
        THEME_TO_CLASS_MAP.put(name, clazz);
        THEME_TO_TYPE_MAP.put(name, themeType);
    }

    public static List<String> getThemeNames() {
        return THEME_TO_CLASS_MAP.keySet().stream().sorted().collect(Collectors.toList());
    }

    public static ThemeType getThemeType(final String themeName) {
        Objects.requireNonNull(themeName);
        final ThemeType themeType = THEME_TO_TYPE_MAP.get(themeName);
        Objects.requireNonNull(themeType);
        return themeType;
    }

    public static String getClassName(final String theme) {
        final String className = THEME_TO_CLASS_MAP.get(theme);
        if (className == null) {
            return "";
        }
        return className;
    }


    // --------------------------------------------------------------------------------


    public enum ThemeType {
        DARK,
        LIGHT
    }
}
