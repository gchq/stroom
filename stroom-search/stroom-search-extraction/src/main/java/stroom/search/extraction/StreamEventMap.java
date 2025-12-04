/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    private volatile boolean complete = false;

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    private long extractionDelayMs;

    public StreamEventMap(final int capacity) {
        this.storedDataMap = new HashMap<>();
        this.streamIdQueue = new LinkedList<>();
        this.capacity = capacity;
    }

    public void terminate() {
        if (!complete) {
            complete = true;
            try {
                lock.lockInterruptibly();
                try {
                    count = 0;
                    streamIdQueue.clear();
                    storedDataMap.clear();

                    streamIdQueue.addLast(COMPLETE);
                    count++;
                    notEmpty.signalAll();
                    notFull.signalAll();
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw new UncheckedInterruptedException(e);
            }
        }
    }

    public void complete() {
        if (!complete) {
            try {
                lock.lockInterruptibly();
                try {
                    if (!complete) {
                        while (count == capacity) {
                            notFull.await();
                        }

                        // If not already completed then add the item to the queue, so it completes
                        // when it gets taken off.
                        if (!complete) {
                            streamIdQueue.addLast(COMPLETE);
                            count++;
                            notEmpty.signal();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw new UncheckedInterruptedException(e);
            }
        }
    }

    public void put(final Event event) throws CompleteException {
        if (complete) {
            LOGGER.debug("Ignoring put as StreamEventMap is completed");
            return;
        }
        try {
            lock.lockInterruptibly();
            if (complete) {
                LOGGER.debug("Ignoring put as StreamEventMap is completed");
                return;
            }
            try {
                while (count == capacity) {
                    notFull.await();
                    if (complete) {
                        LOGGER.debug("Ignoring put as StreamEventMap is completed");
                        return;
                    }
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
            LOGGER.debug(e::getMessage, e);
            throw new UncheckedInterruptedException(e);
        }
    }

    public EventSet take() throws CompleteException {
        if (complete) {
            throw new CompleteException();
        }
        try {
            Key key;
            EventSet eventSet = null;
            long delay = 0;

            lock.lockInterruptibly();
            try {
                if (complete) {
                    throw new CompleteException();
                }
                while (count == 0) {
                    notEmpty.await();
                    if (complete) {
                        throw new CompleteException();
                    }
                }

                key = streamIdQueue.peekFirst();
                if (key != null) {
                    if (key == COMPLETE) {
                        complete = true;
                        notEmpty.signalAll();
                        notFull.signalAll();
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
            LOGGER.debug(e::getMessage, e);
            throw new UncheckedInterruptedException(e);
        }
    }

    public int size() {
        return count;
    }

    public void setExtractionDelayMs(final long extractionDelayMs) {
        this.extractionDelayMs = extractionDelayMs;
    }


    // --------------------------------------------------------------------------------


    private record Key(long streamId, long createTimeMs) {

    }


    // --------------------------------------------------------------------------------


    public record EventSet(long streamId, Set<Event> events) {

        public int size() {
            return events.size();
        }
    }
}
