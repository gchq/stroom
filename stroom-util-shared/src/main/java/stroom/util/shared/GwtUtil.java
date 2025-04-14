package stroom.util.shared;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Util class for doing various things not emulated in GWT
 */
public class GwtUtil {

    private GwtUtil() {
    }

    public static <T> Set<T> toSet(final T... items) {
        // No Set.of() in GWT land :-(
        return NullSafe.stream(items)
                .collect(Collectors.toSet());
    }

    public static <T> List<T> toList(final T... items) {
        // No List.of() in GWT land :-(
        return NullSafe.stream(items)
                .collect(Collectors.toList());
    }

    public static <K, V> Map<K, V> toMap(final Object... items) {
        if (items == null || items.length == 0) {
            return Collections.emptyMap();
        } else {
            if (items.length % 2 != 0) {
                throw new RuntimeException("Odd number of items");
            }
            final Map<K, V> map = new HashMap<>(items.length / 2);
            for (int i = 0; i < items.length; i++) {
                final K key = (K) items[i++];
                final V val = (V) items[i];
                map.put(key, val);
            }
            return map;
        }
    }

    public static String appendStyles(final String existing, final String... styles) {
        String str = NullSafe.string(existing).trim();
        if (styles != null && styles.length > 0) {
            if (!str.isEmpty()) {
                str += " ";
            }
            str = str + NullSafe.stream(styles)
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
        }
        return str;
    }
}
