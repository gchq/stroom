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

import stroom.query.language.functions.Val;

import java.util.Objects;

public class Event implements Comparable<Event> {

    private final long streamId;
    private final long eventId;
    private final Val[] values;

    public Event(final long streamId, final long eventId, final Val[] values) {
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

    public Val[] getValues() {
        return values;
    }

    @Override
    public int compareTo(final Event o) {
        return Long.compare(streamId, o.streamId);
    }

    @Override
    public String toString() {
        return streamId + ":" + eventId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Event event = (Event) o;
        return streamId == event.streamId && eventId == event.eventId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamId, eventId);
    }
}
