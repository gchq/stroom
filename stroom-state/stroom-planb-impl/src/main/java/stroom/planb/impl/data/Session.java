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

package stroom.planb.impl.data;

import stroom.planb.impl.serde.keyprefix.KeyPrefix;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"prefix", "start", "end"})
@JsonInclude(Include.NON_NULL)
public final class Session implements PlanBValue {

    @JsonProperty
    private final KeyPrefix prefix;
    @JsonProperty
    private final Instant start;
    @JsonProperty
    private final Instant end;

    @JsonCreator
    public Session(@JsonProperty("prefix") final KeyPrefix prefix,
                   @JsonProperty("start") final Instant start,
                   @JsonProperty("end") final Instant end) {
        this.prefix = prefix;
        this.start = start;
        this.end = end;
    }

    public KeyPrefix getPrefix() {
        return prefix;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private KeyPrefix prefix;
        private Instant start;
        private Instant end;

        private Builder() {
        }

        private Builder(final Session session) {
            this.prefix = session.prefix;
            this.start = session.start;
            this.end = session.end;
        }

        public Builder prefix(final KeyPrefix prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder start(final Instant start) {
            this.start = start;
            return this;
        }

        public Builder end(final Instant end) {
            this.end = end;
            return this;
        }

        public Session build() {
            return new Session(prefix, start, end);
        }
    }
}
