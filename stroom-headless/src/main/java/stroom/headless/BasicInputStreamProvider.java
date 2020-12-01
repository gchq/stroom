package stroom.headless;

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class BasicInputStreamProvider implements InputStreamProvider {
    private final Map<String, SegmentInputStream> inputStreamMap = new HashMap<>();

    @Override
    public SegmentInputStream get() {
        return inputStreamMap.get(null);
    }

    @Override
    public SegmentInputStream get(final String streamType) {
        return inputStreamMap.get(streamType);
    }

    @Override
    public Set<String> getChildTypes() {
        return inputStreamMap.keySet();
    }

    public void put(final String streamType, final InputStream inputStream, final int size) {
        inputStreamMap.put(streamType, new SingleSegmentInputStreamImpl(inputStream, size));
    }

    @Override
    public void close() {
        inputStreamMap.forEach((k, v) -> {
            try {
                v.close();
            } catch (final IOException e) {
                // Ignore.
            }
        });
    }
}