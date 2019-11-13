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

package stroom.search.extraction;

import stroom.search.coprocessor.Values;

class Event implements Comparable<Event> {
    private final long streamId;
    private final long eventId;
    private final Values values;

    Event(final long streamId, final long eventId, final Values values) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.values = values;
    }

    public long getStreamId() {
        return streamId;
    }

    public long getEventId() {
        return eventId;
    }

    public Values getValues() {
        return values;
    }

    @Override
    public int compareTo(final Event o) {
        return Long.compare(streamId, o.streamId);
    }

    @Override
    public String toString() {
        return String.valueOf(streamId);
    }
}
