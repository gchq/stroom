/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Var;
import stroom.query.Payload;
import stroom.query.shared.IndexConstants;

import java.util.concurrent.locks.ReentrantLock;

public class EventCoprocessor implements Coprocessor<EventCoprocessorSettings> {
    private final EventRef minEvent;
    private final long maxStreams;
    private final long maxEvents;
    private final long maxEventsPerStream;
    private final ReentrantLock eventRefsLock = new ReentrantLock();
    private final int[] fieldIndexes;
    private volatile EventRef maxEvent;
    private volatile EventRefs eventRefs;

    public EventCoprocessor(final EventCoprocessorSettings settings,
                            final FieldIndexMap fieldIndexMap) {
        this.minEvent = settings.getMinEvent();
        this.maxEvent = settings.getMaxEvent();
        this.maxStreams = settings.getMaxStreams();
        this.maxEvents = settings.getMaxEvents();
        this.maxEventsPerStream = settings.getMaxEventsPerStream();

        fieldIndexes = new int[2];
        fieldIndexes[0] = fieldIndexMap.get(IndexConstants.STREAM_ID);
        fieldIndexes[1] = fieldIndexMap.get(IndexConstants.EVENT_ID);
    }

    @Override
    public void receive(final Var[] values) {
        final Long longStreamId = getLong(values, fieldIndexes[0]);
        final Long longEventId = getLong(values, fieldIndexes[1]);

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
            return new EventRefsPayload(refs);
        }

        return null;
    }

    private Long getLong(final Var[] storedData, final int index) {
        try {
            if (index >= 0 && storedData.length > index) {
                final Var value = storedData[index];
                return value.toLong();
            }
        } catch (final Exception e) {
            // Ignore
        }

        return null;
    }
}
