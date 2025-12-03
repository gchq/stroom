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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"startTimeMs", "endTimeMs"})
@JsonInclude(Include.NON_NULL)
public class ScheduleBounds {

    @JsonProperty
    private final Long startTimeMs;
    @JsonProperty
    private final Long endTimeMs;

    @SuppressWarnings({"unused"})
    @JsonCreator
    public ScheduleBounds(@JsonProperty("startTimeMs") final Long startTimeMs,
                          @JsonProperty("endTimeMs") final Long endTimeMs) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ScheduleBounds that = (ScheduleBounds) o;
        return Objects.equals(startTimeMs, that.startTimeMs) &&
                Objects.equals(endTimeMs, that.endTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                startTimeMs,
                endTimeMs);
    }

    @Override
    public String toString() {
        return "ScheduleBounds{" +
                "startTimeMs=" + startTimeMs +
                ", endTimeMs=" + endTimeMs +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Long startTimeMs;
        private Long endTimeMs;

        private Builder() {
        }

        private Builder(final ScheduleBounds scheduleBounds) {
            this.startTimeMs = scheduleBounds.startTimeMs;
            this.endTimeMs = scheduleBounds.endTimeMs;
        }


        public Builder startTimeMs(final Long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }

        public Builder endTimeMs(final Long endTimeMs) {
            this.endTimeMs = endTimeMs;
            return this;
        }

        public ScheduleBounds build() {
            return new ScheduleBounds(startTimeMs, endTimeMs);
        }
    }
}
