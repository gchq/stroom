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
    public EventCoprocessorSettings(@JsonProperty("coprocessorId") final int coprocessorId,
                                    @JsonProperty("minEvent") final EventRef minEvent,
                                    @JsonProperty("maxEvent") final EventRef maxEvent,
                                    @JsonProperty("maxStreams") final long maxStreams,
                                    @JsonProperty("maxEvents") final long maxEvents,
                                    @JsonProperty("maxEventsPerStream") final long maxEventsPerStream) {
        this.coprocessorId = coprocessorId;
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
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
