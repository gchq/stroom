package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    Optional<Map.Entry<Long, List<Event>>> get() {
        if (hasTerminate.isTerminated() || !Thread.currentThread().isInterrupted()) {
            for (final long streamId : storedDataMap.keySet()) {
                final List<Event> events = storedDataMap.remove(streamId);
                if (events != null) {
                    available.release(events.size());
                    return Optional.of(new AbstractMap.SimpleEntry<>(streamId, events));
                }
            }
        }

        return Optional.empty();
    }
}
