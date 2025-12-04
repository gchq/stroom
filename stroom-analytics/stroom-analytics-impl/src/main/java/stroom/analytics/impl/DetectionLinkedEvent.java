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

package stroom.analytics.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"stroom", "streamId", "eventId"})
@JsonInclude(Include.NON_NULL)
public class DetectionLinkedEvent {

    @JsonProperty
    private final String stroom;
    @JsonProperty
    private final Long streamId;
    @JsonProperty
    private final Long eventId;

    @JsonCreator
    public DetectionLinkedEvent(@JsonProperty("stroom") final String stroom,
                                @JsonProperty("streamId") final Long streamId,
                                @JsonProperty("eventId") final Long eventId) {
        this.stroom = stroom;
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public String getStroom() {
        return stroom;
    }

    public Long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    @Override
    public String toString() {
        return "DetectionLinkedEvent{" +
                "stroom='" + stroom + '\'' +
                ", streamId=" + streamId +
                ", eventId=" + eventId +
                '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DetectionLinkedEvent that = (DetectionLinkedEvent) object;
        return Objects.equals(stroom, that.stroom) && Objects.equals(streamId,
                that.streamId) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stroom, streamId, eventId);
    }
}
