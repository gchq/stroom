/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
