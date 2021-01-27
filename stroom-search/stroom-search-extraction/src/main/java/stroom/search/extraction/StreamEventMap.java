package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class StreamEventMap {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private final ConcurrentHashMap<Long, List<Event>> storedDataMap;
    private final LinkedBlockingQueue<Long> streamIdQueue;
    private final Semaphore available;
    private final AtomicInteger size;

    StreamEventMap(final int capacity) {
        storedDataMap = new ConcurrentHashMap<>();
        streamIdQueue = new LinkedBlockingQueue<>();
        available = new Semaphore(capacity);
        size = new AtomicInteger();
    }

    void add(final Event event) {
        try {
            available.acquire();
            final AtomicBoolean newEntry = new AtomicBoolean();
            storedDataMap.compute(event.getStreamId(), (k, v) -> {
                if (v == null) {
                    // The value is null so this is a new entry in the map.
                    // Remember this fact for use after we have added the new value.
                    newEntry.set(true);
                    v = new ArrayList<>();
                }
                v.add(event);
                return v;
            });

            // If we added a new stream to the map then create an entry in the stream id queue so we know to
            // retrieve it in the get method. The stream id queue is used to ensure we get stream data in the order
            // it was added to the stream event map. Note that it is important to do this after the entry has been
            // added to the stream data map, i.e. outside of the compute method, as we must be able to rely on it's
            // presence when retrieving values in the get() method.
            if (newEntry.get()) {
                streamIdQueue.add(event.getStreamId());
            }

            // Increment the size.
            size.getAndIncrement();
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            // Keep interrupting.
            Thread.currentThread().interrupt();
        }
    }

    Optional<Entry<Long, List<Event>>> get() {
        Entry<Long, List<Event>> entry = null;
        if (!Thread.currentThread().isInterrupted()) {
            final Long streamId = streamIdQueue.poll();
            if (streamId != null) {
                final List<Event> events = storedDataMap.remove(streamId);
                if (events != null) {
                    // Release permits.
                    available.release(events.size());

                    // Decrement the size.
                    size.addAndGet(-events.size());

                    entry = new SimpleEntry<>(streamId, events);
                }
            }
        }
        return Optional.ofNullable(entry);
    }

    int size() {
        return size.get();
    }
}
