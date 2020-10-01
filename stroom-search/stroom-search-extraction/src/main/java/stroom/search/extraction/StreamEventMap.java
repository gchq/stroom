package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class StreamEventMap {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private final HasTerminate hasTerminate;
    private final ConcurrentHashMap<Long, List<Event>> storedDataMap;
    private final Semaphore available;

    StreamEventMap(final HasTerminate hasTerminate,
                   final int capacity) {
        this.hasTerminate = hasTerminate;
        storedDataMap = new ConcurrentHashMap<>();
        available = new Semaphore(capacity);
    }

    void add(final Event event) {
        try {
            while (!hasTerminate.isTerminated() &&
                    !available.tryAcquire(1, TimeUnit.SECONDS)) {
                LOGGER.debug(() -> "Waiting to add event: " + event);
            }

            if (!hasTerminate.isTerminated()) {
                storedDataMap.compute(event.getStreamId(), (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.add(event);
                    return v;
                });
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            // Keep interrupting.
            Thread.currentThread().interrupt();
        }
    }

    Map<Long, List<Event>> get() {
        if (hasTerminate.isTerminated() || Thread.currentThread().isInterrupted()) {
            return Collections.emptyMap();
        }

        final Map<Long, List<Event>> map = new HashMap<>();
        storedDataMap.keySet().forEach(streamId -> {
            final List<Event> events = storedDataMap.remove(streamId);
            if (events != null) {
                available.release(events.size());
                map.put(streamId, events);
            }
        });
        return map;
    }
}
