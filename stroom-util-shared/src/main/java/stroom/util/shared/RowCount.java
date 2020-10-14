/*
 * Copyright 2016 Crown Copyright
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Optional;

@JsonPropertyOrder({"count", "exact"})
@JsonInclude(Include.NON_NULL)
public class RowCount<T extends Number> {
    @JsonProperty
    private final T count;
    @JsonProperty
    private final boolean exact;

    @JsonCreator
    public RowCount(@JsonProperty("count") final T count,
                    @JsonProperty("exact") final boolean exact) {
        this.count = count;
        this.exact = exact;
    }

    public static <T extends Number> RowCount<T> of(final T count, final boolean exact) {
        return new RowCount<>(count, exact);
    }

    public T getCount() {
        return count;
    }

    public boolean isExact() {
        return exact;
    }

    @JsonIgnore
    public T orElse(final T other) {
        return exact
                ? count
                : other;
    }

    @JsonIgnore
    public Optional<T> asOptional() {
        return exact
                ? Optional.of(count)
                : Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RowCount<?> rowCount = (RowCount<?>) o;
        return exact == rowCount.exact &&
                Objects.equals(count, rowCount.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, exact);
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("count", count);
        builder.append("exact", exact);
        return builder.toString();
    }
}
