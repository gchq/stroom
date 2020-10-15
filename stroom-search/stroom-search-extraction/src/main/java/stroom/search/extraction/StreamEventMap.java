package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

class StreamEventMap {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private final ConcurrentHashMap<Long, List<Event>> storedDataMap;
    private final Semaphore available;

    StreamEventMap(final int capacity) {
        storedDataMap = new ConcurrentHashMap<>();
        available = new Semaphore(capacity);
    }

    void add(final Event event) {
        try {
            available.acquire();
            storedDataMap.compute(event.getStreamId(), (k, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.add(event);
                return v;
            });
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            // Keep interrupting.
            Thread.currentThread().interrupt();
        }
    }

    Optional<Entry<Long, List<Event>>> get() {
        if (!Thread.currentThread().isInterrupted()) {
            for (final long streamId : storedDataMap.keySet()) {
                final List<Event> events = storedDataMap.remove(streamId);
                if (events != null) {
                    available.release(events.size());
                    return Optional.of(new SimpleEntry<>(streamId, events));
                }
            }
        }

        return Optional.empty();
    }
}
