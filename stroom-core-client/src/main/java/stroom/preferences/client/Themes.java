package stroom.preferences.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Themes {

    private static final Map<String, String> themeMap = new HashMap<>();

    static {
        themeMap.put("Light", "stroom-theme-light");
        themeMap.put("Dark", "stroom-theme-dark");
//        themeMap.put("Dark 2", "stroom-theme-dark stroom-theme-dark2");
    }

    public static List<String> getThemeNames() {
        return themeMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    public static String getClassName(final String theme) {
        final String className = themeMap.get(theme);
        if (className == null) {
            return "";
        }
        return className;
    }

}
