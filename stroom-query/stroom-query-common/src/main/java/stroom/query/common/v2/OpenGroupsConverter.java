package stroom.query.common.v2;

import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OpenGroupsConverter {

    static Set<Key> convertSet(final Set<String> openGroups) {
        return Metrics.measure("Converting open groups", () -> {
            Set<Key> keys = Collections.emptySet();
            if (openGroups != null) {
                keys = new HashSet<>();
                for (final String encodedGroup : openGroups) {
                    final byte[] bytes = Base64.getDecoder().decode(encodedGroup);
                    keys.add(new Key(bytes));
                }
            }
            return keys;
        });
    }

    public static String encode(final Key key) {
        return Metrics.measure("Encoding groups", () ->
                Base64.getEncoder().encodeToString(key.getBytes()));
    }
}
