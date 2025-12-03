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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonInclude(Include.NON_NULL)
public class CurrentDbState {

    @JsonProperty
    private final long streamId;
    @JsonProperty
    private final Long eventId;
    @JsonProperty
    private final Long lastEventTime;

    @JsonCreator
    public CurrentDbState(@JsonProperty("streamId") final long streamId,
                          @JsonProperty("eventId") final Long eventId,
                          @JsonProperty("lastEventTime") final Long lastEventTime) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.lastEventTime = lastEventTime;
    }

    public long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }

    public boolean hasLastEventTime() {
        return lastEventTime != null;
    }

    @Override
    public String toString() {
        return "CurrentDbState{" +
                "streamId=" + streamId +
                ", eventId=" + eventId +
                ", lastEventTime=" + LocalDateTime.ofInstant(Instant.ofEpochMilli(lastEventTime), ZoneOffset.UTC) +
                '}';
    }

    /**
     * Merges existingCurrentDbState with this to create a new state.
     */
    public CurrentDbState mergeExisting(final CurrentDbState existingCurrentDbState) {
        final Long lastEventTime = NullSafe.requireNonNullElseGet(
                this.lastEventTime,
                () -> NullSafe.get(existingCurrentDbState, CurrentDbState::getLastEventTime));

        return new CurrentDbState(
                streamId,
                eventId,
                lastEventTime);
    }
}
