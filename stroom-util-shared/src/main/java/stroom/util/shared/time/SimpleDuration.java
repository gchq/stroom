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

package stroom.util.shared.time;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SimpleDuration {

    public static SimpleDuration ZERO = new SimpleDuration(0, TimeUnit.NANOSECONDS);

    @JsonProperty
    private final long time;
    @JsonProperty
    private final TimeUnit timeUnit;

    @JsonCreator
    public SimpleDuration(@JsonProperty("time") final long time,
                          @JsonProperty("timeUnit") final TimeUnit timeUnit) {
        this.time = time < 0
                ? 0
                : time;
        this.timeUnit = timeUnit == null
                ? TimeUnit.DAYS
                : timeUnit;
    }

    @JsonCreator
    public static SimpleDuration parse(final String value) {
        if (value == null) {
            throw new NullPointerException("Null value passed to SimpleDuration");
        }

        final TimeUnit timeUnit = TimeUnit.parse(value);
        if (timeUnit != null) {
            final String num = value.substring(0, value.length() - timeUnit.getShortForm().length());
            final long l = Long.parseLong(num);
            return new SimpleDuration(l, timeUnit);
        }

        throw new RuntimeException("Error parsing simple duration: " + value);
    }

    public long getTime() {
        return time;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SimpleDuration that = (SimpleDuration) o;
        return time == that.time && timeUnit == that.timeUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, timeUnit);
    }

    @Override
    public String toString() {
        return time + timeUnit.getShortForm();
    }

    public String toLongString() {
        return time + " " + timeUnit.getDisplayValue();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private long time;
        private TimeUnit timeUnit;

        private Builder() {
        }

        private Builder(final SimpleDuration simpleDuration) {
            if (simpleDuration != null) {
                this.time = simpleDuration.time;
                this.timeUnit = simpleDuration.timeUnit;
            }
        }

        public Builder time(final long time) {
            this.time = time;
            return this;
        }

        public Builder timeUnit(final TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public SimpleDuration build() {
            return new SimpleDuration(time, timeUnit);
        }
    }
}
