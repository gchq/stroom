package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class StreamEventMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private final Map<Long, List<Event>> storedDataMap;
    private final LinkedList<Long> streamIdQueue;
    private final int capacity;
    private int count;

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    StreamEventMap(final int capacity) {
        this.storedDataMap = new HashMap<>();
        this.streamIdQueue = new LinkedList<>();
        this.capacity = capacity;
    }

    void put(final Event event) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == capacity) {
                notFull.await();
            }
            if (event == null) {
                streamIdQueue.addLast(-1L);
            } else {
                storedDataMap.compute(event.getStreamId(), (k, v) -> {
                    if (v == null) {
                        // The value is null so this is a new entry in the map.
                        // Remember this fact for use after we have added the new value.
                        v = new ArrayList<>();
                        streamIdQueue.addLast(k);
                    }
                    v.add(event);
                    return v;
                });
            }
            ++count;
            LOGGER.debug(() -> "size=" + count);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    Optional<Entry<Long, List<Event>>> take() throws InterruptedException {
        Entry<Long, List<Event>> entry = null;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            final Long streamId = streamIdQueue.removeFirst();
            if (streamId == -1) {
                streamIdQueue.addLast(-1L);
                notEmpty.signal();
            } else {
                final List<Event> events = storedDataMap.remove(streamId);
                entry = new SimpleEntry<>(streamId, events);
                count -= events.size();
            }
            notFull.signal();
        } finally {
            lock.unlock();
        }
        return Optional.ofNullable(entry);
    }

    int size() {
        return count;
    }
}
