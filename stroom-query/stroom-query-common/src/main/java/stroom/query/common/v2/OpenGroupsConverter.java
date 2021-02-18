package stroom.query.common.v2;

import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OpenGroupsConverter {

    static Set<RawKey> convertSet(final Set<String> openGroups) {
        return Metrics.measure("Converting open groups", () -> {
            Set<RawKey> rawKeys = Collections.emptySet();
            if (openGroups != null) {
                rawKeys = new HashSet<>();
                for (final String encodedGroup : openGroups) {
                    final byte[] bytes = Base64.getDecoder().decode(encodedGroup);
                    rawKeys.add(new RawKey(bytes));
                }
            }
            return rawKeys;
        });
    }

    public static String encode(final RawKey rawKey) {
        return Metrics.measure("Encoding groups", () -> {
            return Base64.getEncoder().encodeToString(rawKey.getBytes());
        });
    }
}
