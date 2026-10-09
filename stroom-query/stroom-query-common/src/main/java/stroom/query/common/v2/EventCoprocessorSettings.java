/*
 * Copyright 2020 Crown Copyright
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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class EventCoprocessorSettings implements CoprocessorSettings {
    @JsonProperty
    private final int coprocessorId;
    @JsonProperty
    private final EventRef minEvent;
    @JsonProperty
    private final EventRef maxEvent;
    @JsonProperty
    private final long maxStreams;
    @JsonProperty
    private final long maxEvents;
    @JsonProperty
    private final long maxEventsPerStream;

    @JsonCreator
    public EventCoprocessorSettings(@JsonProperty("coprocessorId") final Integer coprocessorId,
                                    @JsonProperty("minEvent") final EventRef minEvent,
                                    @JsonProperty("maxEvent") final EventRef maxEvent,
                                    @JsonProperty("maxStreams") final Long maxStreams,
                                    @JsonProperty("maxEvents") final Long maxEvents,
                                    @JsonProperty("maxEventsPerStream") final Long maxEventsPerStream) {
        this.coprocessorId = Objects.requireNonNullElse(coprocessorId, 0);
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = Objects.requireNonNullElse(maxStreams, 0L);
        this.maxEvents = Objects.requireNonNullElse(maxEvents, 0L);
        this.maxEventsPerStream = Objects.requireNonNullElse(maxEventsPerStream, 0L);
    }

    @Override
    public int getCoprocessorId() {
        return coprocessorId;
    }

    public EventRef getMinEvent() {
        return minEvent;
    }

    public EventRef getMaxEvent() {
        return maxEvent;
    }

    public long getMaxStreams() {
        return maxStreams;
    }

    public long getMaxEvents() {
        return maxEvents;
    }

    public long getMaxEventsPerStream() {
        return maxEventsPerStream;
    }
}
