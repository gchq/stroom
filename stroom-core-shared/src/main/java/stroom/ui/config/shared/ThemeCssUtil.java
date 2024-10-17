package stroom.ui.config.shared;

import stroom.util.shared.GwtNullSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class ThemeCssUtil {

    private static final Map<String, String> densityMap = new HashMap<>();
    private static final Map<String, String> fontMap = new HashMap<>();
    private static final Map<String, String> fontSizeMap = new HashMap<>();

    static {
        densityMap.put("Comfortable", "stroom-density-comfortable");
        densityMap.put("Compact", "stroom-density-compact");

        fontMap.put("Arial", "stroom-font-arial");
        fontMap.put("Open Sans", "stroom-font-open-sans");
        fontMap.put("Roboto", "stroom-font-roboto");
        fontMap.put("Tahoma", "stroom-font-tahoma");
        fontMap.put("Verdana", "stroom-font-verdana");

        fontSizeMap.put("Small", "stroom-font-size-small");
        fontSizeMap.put("Medium", "stroom-font-size-medium");
        fontSizeMap.put("Large", "stroom-font-size-large");
    }

    private ThemeCssUtil() {
        // Utility class.
    }

    /**
     * @return A space delimited list of css classes for theme, density, font and font size.
     */
    public static String getCurrentPreferenceClasses(final UserPreferences currentUserPreferences) {
        final StringJoiner classJoiner = new StringJoiner(" ")
                .add("stroom");

        if (currentUserPreferences != null) {
            GwtNullSafe.consume(currentUserPreferences.getTheme(), theme ->
                    classJoiner.add(Theme.getClassName(theme)));

            if (GwtNullSafe.requireNonNullElse(currentUserPreferences.getEnableTransparency(), true)) {
                classJoiner.add("transparency");
            }

            Optional.ofNullable(currentUserPreferences.getDensity())
                    .map(densityMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFont())
                    .map(fontMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFontSize())
                    .map(fontSizeMap::get)
                    .ifPresent(classJoiner::add);
        }
        return classJoiner.toString();
    }

    public static List<String> getThemes() {
        return Theme.getThemeNames();
    }

    public static List<String> getFonts() {
        return fontMap.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
