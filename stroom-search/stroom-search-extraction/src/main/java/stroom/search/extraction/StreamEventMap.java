package stroom.search.extraction;

import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StreamEventMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private final Map<Long, Set<Event>> storedDataMap;
    private final LinkedList<Long> streamIdQueue;
    private final int capacity;
    private int count;

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public StreamEventMap(final int capacity) {
        this.storedDataMap = new HashMap<>();
        this.streamIdQueue = new LinkedList<>();
        this.capacity = capacity;
    }

    public void complete() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == capacity) {
                notFull.await();
            }
            streamIdQueue.addLast(-1L);
            ++count;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public void put(final Event event) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == capacity) {
                notFull.await();
            }
            final Set<Event> eventSet = storedDataMap.compute(event.getStreamId(), (k, v) -> {
                if (v == null) {
                    // The value is null so this is a new entry in the map.
                    // Remember this fact for use after we have added the new value.
                    v = new HashSet<>();
                    streamIdQueue.addLast(k);
                }
                return v;
            });
            if (eventSet.add(event)) {
                ++count;
                notEmpty.signal();
            } else {
                LOGGER.warn("Duplicate segment for streamId=" +
                        event.getStreamId() +
                        ", eventId=" +
                        event.getEventId());
            }
        } finally {
            lock.unlock();
        }
    }

    public Entry<Long, Set<Event>> take() throws InterruptedException, CompleteException {
        Entry<Long, Set<Event>> entry = null;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            final long streamId = streamIdQueue.removeFirst();
            if (streamId == -1) {
                streamIdQueue.addLast(-1L);
                notEmpty.signal();
            } else {
                final Set<Event> events = storedDataMap.remove(streamId);
                entry = new SimpleEntry<>(streamId, events);
                count -= events.size();
            }
            notFull.signal();
        } finally {
            lock.unlock();
        }

        if (entry == null) {
            throw new CompleteException();
        }
        return entry;
    }

    public int size() {
        return count;
    }
}
