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

package stroom.query.language.functions;

import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValDuration implements ValNumber {

    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValDuration.class, ValComparators.AS_LONG_COMPARATOR);

    public static final Type TYPE = Type.DURATION;
    @JsonProperty
    private final long milliseconds;

    @JsonCreator
    private ValDuration(@JsonProperty("milliseconds") final long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public static ValDuration create(final long milliseconds) {
        return new ValDuration(milliseconds);
    }

    public static ValDuration create(final Duration duration) {
        return new ValDuration(Objects.requireNonNull(duration).toMillis());
    }

    @Override
    public Integer toInteger() {
        return (int) milliseconds;
    }

    @Override
    public Long toLong() {
        return milliseconds;
    }

    @Override
    public Float toFloat() {
        return (float) milliseconds;
    }

    @Override
    public Double toDouble() {
        return (double) milliseconds;
    }

    @Override
    public Boolean toBoolean() {
        return milliseconds != 0;
    }

    @Override
    public String toString() {
        return ModelStringUtil.formatDurationString(milliseconds, true);
    }

    @Override
    public Number toNumber() {
        return milliseconds;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(this);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValDuration valDuration = (ValDuration) o;
        return milliseconds == valDuration.milliseconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(milliseconds);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return milliseconds;
    }
}
