package stroom.search.coprocessor;

import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Coprocessors {
    private final Set<NewCoprocessor> set;

    Coprocessors(final Set<NewCoprocessor> set) {
        this.set = set;
    }

    public Map<CoprocessorKey, Payload> createPayloads() {
        // Produce payloads for each coprocessor.
        Map<CoprocessorKey, Payload> payloadMap = null;
        if (set != null && set.size() > 0) {
            for (final NewCoprocessor coprocessor : set) {
                final Payload payload = coprocessor.createPayload();
                if (payload != null) {
                    if (payloadMap == null) {
                        payloadMap = new HashMap<>();
                    }

                    payloadMap.put(coprocessor.getKey(), payload);
                }
            }
        }
        return payloadMap;
    }

    public int size() {
        return set.size();
    }

    public Set<NewCoprocessor> getSet() {
        return set;
    }
}
