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

package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class Limits {

    @JsonProperty
    private final Long streamCount;
    @JsonProperty
    private final Long eventCount;
    @JsonProperty
    private final Long durationMs;

    @JsonCreator
    public Limits(@JsonProperty("streamCount") final Long streamCount,
                  @JsonProperty("eventCount") final Long eventCount,
                  @JsonProperty("durationMs") final Long durationMs) {
        this.streamCount = streamCount;
        this.eventCount = eventCount;
        this.durationMs = durationMs;
    }

    public Long getStreamCount() {
        return streamCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private Long streamCount;
        private Long eventCount;
        private Long durationMs;

        private Builder() {
        }

        private Builder(final Limits limits) {
            this.streamCount = limits.streamCount;
            this.eventCount = limits.eventCount;
            this.durationMs = limits.durationMs;
        }

        public Builder streamCount(final Long value) {
            this.streamCount = value;
            return this;
        }

        public Builder eventCount(final Long value) {
            this.eventCount = value;
            return this;
        }

        public Builder durationMs(final Long value) {
            this.durationMs = value;
            return this;
        }

        public Limits build() {
            return new Limits(
                    streamCount,
                    eventCount,
                    durationMs);
        }
    }
}
