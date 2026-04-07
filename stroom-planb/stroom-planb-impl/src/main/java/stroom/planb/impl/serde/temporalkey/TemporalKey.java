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

package stroom.planb.impl.serde.temporalkey;

import stroom.planb.impl.serde.keyprefix.KeyPrefix;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Objects;

@JsonPropertyOrder({"prefix", "time"})
@JsonInclude(Include.NON_NULL)
public class TemporalKey {

    @JsonProperty
    private final KeyPrefix prefix;
    @JsonProperty
    private final Instant time;

    @JsonCreator
    public TemporalKey(@JsonProperty("prefix") final KeyPrefix prefix,
                       @JsonProperty("time") final Instant time) {
        this.prefix = prefix;
        this.time = time;
    }

    public KeyPrefix getPrefix() {
        return prefix;
    }

    public Instant getTime() {
        return time;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TemporalKey that = (TemporalKey) o;
        return Objects.equals(prefix, that.prefix) && Objects.equals(time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, time);
    }

    @Override
    public String toString() {
        return prefix.toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private KeyPrefix prefix;
        private Instant time;

        private Builder() {
        }

        private Builder(final TemporalKey key) {
            this.prefix = key.prefix;
            this.time = key.time;
        }

        public Builder prefix(final KeyPrefix prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder time(final Instant time) {
            this.time = time;
            return this;
        }

        public TemporalKey build() {
            return new TemporalKey(prefix, time);
        }
    }
}
