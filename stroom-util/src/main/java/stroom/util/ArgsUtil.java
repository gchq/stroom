package stroom.util;

import java.util.Map;

public final class ArgsUtil {
    private ArgsUtil() {
        // Utility class.
    }

    public static Map<String, String> parse(final String[] args) {
        final CIStringHashMap map = new CIStringHashMap();
        for (final String arg : args) {
            final String[] split = arg.split("=");
            if (split.length > 1) {
                map.put(split[0], split[1]);
            } else {
                map.put(split[0], "");
            }
        }
        return map;
    }
}
