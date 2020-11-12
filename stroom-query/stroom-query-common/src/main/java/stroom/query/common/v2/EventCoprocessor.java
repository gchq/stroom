/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class EventCoprocessor implements Coprocessor {
    private static final String STREAM_ID = "StreamId";
    private static final String EVENT_ID = "EventId";

    private final Consumer<Throwable> errorConsumer;
    private final CoprocessorKey coprocessorKey;
    private final EventRef minEvent;
    private final long maxStreams;
    private final long maxEvents;
    private final long maxEventsPerStream;
    private final ReentrantLock eventRefsLock = new ReentrantLock();
    private final AtomicLong valuesCount = new AtomicLong();
    private final AtomicLong completionCount = new AtomicLong();
    private final CountDownLatch completionState = new CountDownLatch(1);
    private final Integer streamIdIndex;
    private final Integer eventIdIndex;
    private volatile EventRef maxEvent;
    private volatile EventRefs eventRefs;

    public EventCoprocessor(final CoprocessorKey coprocessorKey,
                            final EventCoprocessorSettings settings,
                            final FieldIndex fieldIndex,
                            final Consumer<Throwable> errorConsumer) {
        this.coprocessorKey = coprocessorKey;
        this.errorConsumer = errorConsumer;
        this.minEvent = settings.getMinEvent();
        this.maxEvent = settings.getMaxEvent();
        this.maxStreams = settings.getMaxStreams();
        this.maxEvents = settings.getMaxEvents();
        this.maxEventsPerStream = settings.getMaxEventsPerStream();

        // Add required fields.
        fieldIndex.create(STREAM_ID);
        fieldIndex.create(EVENT_ID);

        streamIdIndex = fieldIndex.getPos(STREAM_ID);
        eventIdIndex = fieldIndex.getPos(EVENT_ID);
    }

    @Override
    public Consumer<Val[]> getValuesConsumer() {
        return values -> {
            valuesCount.incrementAndGet();
            receive(values);
        };
    }

    @Override
    public Consumer<Throwable> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionConsumer() {
        return count -> {
            completionCount.set(count);
            completionState.countDown();
        };
    }

    @Override
    public AtomicLong getValuesCount() {
        return valuesCount;
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.await(timeout, unit);
    }

    private void receive(final Val[] values) {
        final Long longStreamId = getLong(values, streamIdIndex);
        final Long longEventId = getLong(values, eventIdIndex);

        if (longStreamId != null && longEventId != null) {
            final EventRef ref = new EventRef(longStreamId, longEventId);

            eventRefsLock.lock();
            try {
                if (eventRefs == null) {
                    eventRefs = new EventRefs(minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream);
                }

                eventRefs.add(ref);
                this.maxEvent = eventRefs.getMaxEvent();

            } finally {
                eventRefsLock.unlock();
            }
        }
    }

    @Override
    public Payload createPayload() {
        EventRefs refs;
        eventRefsLock.lock();
        try {
            refs = eventRefs;
            eventRefs = null;
        } finally {
            eventRefsLock.unlock();
        }

        if (refs != null && refs.size() > 0) {
            refs.trim();
            return new EventRefsPayload(coprocessorKey, refs);
        }

        return null;
    }

    @Override
    public boolean consumePayload(final Payload payload) {
        final EventRefsPayload eventRefsPayload = (EventRefsPayload) payload;
        eventRefsLock.lock();
        try {
            eventRefs.add(eventRefsPayload.getEventRefs());
            eventRefs.trim();
        } finally {
            eventRefsLock.unlock();
        }
        return true;
    }

    private Long getLong(final Val[] storedData, final Integer index) {
        try {
            if (index != null && storedData.length > index) {
                final Val value = storedData[index];
                return value.toLong();
            }
        } catch (final Exception e) {
            // Ignore
        }

        return null;
    }
}
