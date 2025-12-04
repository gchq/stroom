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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Optional;

/**
 * A count of something where the count may not be known with certainty.
 */
@JsonPropertyOrder({"count", "exact"})
@JsonInclude(Include.NON_NULL)
public class Count<T extends Number> {

    @JsonProperty
    private final T count;
    @JsonProperty
    private final boolean exact;

    @JsonCreator
    public Count(@JsonProperty("count") final T count,
                 @JsonProperty("exact") final boolean exact) {
        this.count = count;
        this.exact = exact;
    }

    public static <T extends Number> Count<T> of(final T count, final boolean exact) {
        return new Count<>(count, exact);
    }

    public static Count<Long> zeroLong() {
        // Avoids class cast issues when auto boxing
        return new Count<>(Long.valueOf(0), true);
    }

    public static Count<Integer> zeroInt() {
        // Avoids class cast issues when auto boxing
        return new Count<>(Integer.valueOf(0), true);
    }

    public static <T extends Number> Count<T> exactly(final T count) {
        return new Count<>(count, true);
    }

    public static <T extends Number> Count<T> approximately(final T count) {
        return new Count<>(count, false);
    }

    public T getCount() {
        return count;
    }

    public boolean isExact() {
        return exact;
    }

    public T orElse(final T other) {
        return exact
                ? count
                : other;
    }

    /**
     * @return Empty if the count is not exact or is null
     */
    public Optional<T> asOptional() {
        return exact && count != null
                ? Optional.of(count)
                : Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Count<?> count = (Count<?>) o;
        return exact == count.exact &&
                Objects.equals(this.count, count.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, exact);
    }

    @Override
    public String toString() {
        return "count=" + count + ", exact=" + exact;
    }
}
