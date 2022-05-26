package stroom.search.extraction;

import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StreamEventMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamEventMap.class);

    private static final Key COMPLETE = new Key(-1, -1);

    private final Map<Long, Set<Event>> storedDataMap;
    private final LinkedList<Key> streamIdQueue;
    private final int capacity;
    private int count;

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    private long extractionDelayMs;

    public StreamEventMap(final int capacity) {
        this.storedDataMap = new HashMap<>();
        this.streamIdQueue = new LinkedList<>();
        this.capacity = capacity;
    }

    public void complete() {
        try {
            lock.lockInterruptibly();
            try {
                while (count == capacity) {
                    notFull.await();
                }

                streamIdQueue.addLast(COMPLETE);
                count++;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedInterruptedException(e);
        }
    }

    public void put(final Event event) {
        try {
            lock.lockInterruptibly();
            try {
                while (count == capacity) {
                    notFull.await();
                }

                final Set<Event> events = storedDataMap.compute(event.getStreamId(), (k, v) -> {
                    if (v == null) {
                        // The value is null so this is a new entry in the map.
                        // Remember this fact for use after we have added the new value.
                        v = new HashSet<>();
                        streamIdQueue.addLast(new Key(k, System.currentTimeMillis()));
                    }
                    return v;
                });
                if (events.add(event)) {
                    count++;
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
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedInterruptedException(e);
        }
    }

    public EventSet take() throws CompleteException {
        try {
            Key key;
            EventSet eventSet = null;
            long delay = 0;

            lock.lockInterruptibly();
            try {
                while (count == 0) {
                    notEmpty.await();
                }

                key = streamIdQueue.peekFirst();
                if (key != null) {
                    if (key == COMPLETE) {
                        notEmpty.signal();
                    } else {
                        delay = extractionDelayMs - (System.currentTimeMillis() - key.createTimeMs);
                        if (delay <= 0) {
                            key = streamIdQueue.removeFirst();
                            final Set<Event> events = storedDataMap.remove(key.streamId);
                            eventSet = new EventSet(key.streamId, events);
                            count -= events.size();
                            notFull.signal();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }

            if (key == COMPLETE) {
                throw new CompleteException();
            }

            if (delay > 0) {
                Thread.sleep(delay);
            }

            return eventSet;
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedInterruptedException(e);
        }
    }

    public int size() {
        return count;
    }

    public void setExtractionDelayMs(final long extractionDelayMs) {
        this.extractionDelayMs = extractionDelayMs;
    }

    private static class Key {

        private final long streamId;
        private final long createTimeMs;

        public Key(final long streamId,
                   final long createTimeMs) {
            this.streamId = streamId;
            this.createTimeMs = createTimeMs;
        }

        public long getStreamId() {
            return streamId;
        }

        public long getCreateTimeMs() {
            return createTimeMs;
        }
    }

    public static class EventSet {

        private final long streamId;
        private final Set<Event> events;

        public EventSet(final long streamId,
                        final Set<Event> events) {
            this.streamId = streamId;
            this.events = events;
        }

        public long getStreamId() {
            return streamId;
        }

        public Set<Event> getEvents() {
            return events;
        }

        public int size() {
            return events.size();
        }
    }
}
