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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class EventRefs implements Iterable<EventRef> {

    @JsonProperty
    private final EventRef minEvent;
    @JsonProperty
    private final long maxStreams;
    @JsonProperty
    private final long maxEvents;
    @JsonProperty
    private final long maxEventsPerStream;
    @JsonProperty
    private final List<EventRef> list;
    @JsonProperty
    private volatile EventRef maxEvent;
    @JsonProperty
    private boolean reachedLimit;

    @JsonCreator
    public EventRefs(@JsonProperty("minEvent") final EventRef minEvent,
                     @JsonProperty("maxStreams") final long maxStreams,
                     @JsonProperty("maxEvents") final long maxEvents,
                     @JsonProperty("maxEventsPerStream") final long maxEventsPerStream,
                     @JsonProperty("list") final List<EventRef> list,
                     @JsonProperty("maxEvent") final EventRef maxEvent,
                     @JsonProperty("reachedLimit") final boolean reachedLimit) {
        this.minEvent = minEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
        this.list = list;
        this.maxEvent = maxEvent;
        this.reachedLimit = reachedLimit;
    }

    public EventRefs(final EventRef minEvent,
                     final EventRef maxEvent,
                     final long maxStreams,
                     final long maxEvents,
                     final long maxEventsPerStream) {
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
        list = new ArrayList<>();
    }

    public void add(final List<EventRef> eventRefs) {
        list.addAll(eventRefs);

        // Trim if the list gets bigger than double the number of events.
        if (list.size() > (maxEvents * 2)) {
            trim();
        }
    }

//    public void add(final EventRefs eventRefs) {
//        list.addAll(eventRefs.list);
//
//        // Trim if the list gets bigger than double the number of events.
//        if (list.size() > (maxEvents * 2)) {
//            trim();
//        }
//    }

    public void add(final EventRef ref) {
        if ((ref.getStreamId() > minEvent.getStreamId()
             || (ref.getStreamId() == minEvent.getStreamId() && ref.getEventId() >= minEvent.getEventId()))
            && (ref.getStreamId() < maxEvent.getStreamId() || (ref.getStreamId() == maxEvent.getStreamId()
                                                               && ref.getEventId() <= maxEvent.getEventId()))) {
            list.add(ref);

            // Trim if the list gets bigger than double the number of events.
            if (list.size() > (maxEvents * 2)) {
                trim();
            }
        }
    }

    public void trim() {
        // Sort the event reference list.
        Collections.sort(list, new EventRefComparator());

        // Trim.
        long lastStreamId = -1;
        long streamCount = 0;
        long eventCount = 0;
        long eventCountPerStream = 0;
        boolean trim = false;
        final Iterator<EventRef> iter = list.iterator();
        while (iter.hasNext()) {
            final EventRef ref = iter.next();

            if (trim) {
                iter.remove();

            } else {
                if (lastStreamId != ref.getStreamId()) {
                    lastStreamId = ref.getStreamId();
                    streamCount++;
                    eventCountPerStream = 0;
                }
                eventCount++;
                eventCountPerStream++;

                if (streamCount == maxStreams || eventCount == maxEvents) {
                    reachedLimit = true;
                    trim = true;

                } else if (eventCountPerStream == maxEventsPerStream) {
                    trim = true;
                }
            }
        }

        // Can we set the max event to be lower than it is currently?
        if (trim) {
            final EventRef lastEvent = list.get(list.size() - 1);
            if (new EventRefComparator().compare(lastEvent, maxEvent) < 0) {
                maxEvent = lastEvent;
            }
        }
    }

    public EventRef getMaxEvent() {
        return maxEvent;
    }

    public int size() {
        return list.size();
    }

    public List<EventRef> getList() {
        return list;
    }

    @Override
    public Iterator<EventRef> iterator() {
        return list.iterator();
    }

    public boolean isReachedLimit() {
        return reachedLimit;
    }

    private static class EventRefComparator implements Comparator<EventRef> {

        @Override
        public int compare(final EventRef o1, final EventRef o2) {
            if (o1.getStreamId() == o2.getStreamId()) {
                return Long.compare(o1.getEventId(), o2.getEventId());
            }

            return Long.compare(o1.getStreamId(), o2.getStreamId());
        }
    }
}
