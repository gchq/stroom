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

package stroom.query.common.v2;

import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class EventCoprocessor implements Coprocessor, HasCompletionState {

    private static final String STREAM_ID = "StreamId";
    private static final String EVENT_ID = "EventId";

    private final ErrorConsumer errorConsumer;
    private final EventRef minEvent;
    private final long maxStreams;
    private final long maxEvents;
    private final long maxEventsPerStream;
    private final ReentrantLock eventRefsLock = new ReentrantLock();
    private final CompletionState completionState = new CompletionStateImpl();
    private final Integer streamIdIndex;
    private final Integer eventIdIndex;
    private volatile EventRef maxEvent;
    private volatile EventRefs eventRefs;

    public EventCoprocessor(final EventCoprocessorSettings settings,
                            final FieldIndex fieldIndex,
                            final ErrorConsumer errorConsumer) {
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
    public void accept(final Val[] values) {
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
    public ErrorConsumer getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public CompletionState getCompletionState() {
        return completionState;
    }

    @Override
    public void readPayload(final Input input) {
        final EventRef[] array = EventRefsSerialiser.readArray(input);
        if (array.length > 0) {
            eventRefsLock.lock();
            try {
                if (eventRefs == null) {
                    eventRefs = new EventRefs(minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream);
                }

                eventRefs.add(List.of(array));
                eventRefs.trim();

            } finally {
                eventRefsLock.unlock();
            }
        }
    }

    @Override
    public void writePayload(final Output output) {
        final EventRefs refs;
        eventRefsLock.lock();
        try {
            refs = eventRefs;
            eventRefs = null;
        } finally {
            eventRefsLock.unlock();
        }

        EventRef[] array = new EventRef[0];
        if (refs != null && refs.size() > 0) {
            refs.trim();
            array = refs.getList().toArray(new EventRef[0]);
        }

        EventRefsSerialiser.writeArray(output, array);
    }

    public EventRefs getEventRefs() {
        return eventRefs;
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

    @Override
    public void clear() {
        eventRefs = null;
    }

    @Override
    public long getByteSize() {
        return 0;
    }
}
