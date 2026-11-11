/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The most common values of a field, most common first, with the number of documents they were counted
/// across so that each value's share can be shown.
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FieldValues {

    /// No values, e.g. because the data source can't provide them.
    public static final FieldValues EMPTY = new FieldValues(Collections.emptyList(), 0L, false, null);

    @JsonProperty
    private final List<FieldValueCount> values;
    @JsonProperty
    private final long totalCount;
    @JsonProperty
    private final boolean allTime;
    @JsonProperty
    private final String scope;

    /// Creates values whose scope is described by the request's time range.
    ///
    /// @param values     The most common values, most common first.
    /// @param totalCount The number of documents the values were counted across.
    /// @param allTime    True if the values were counted across all time rather than a time range.
    public FieldValues(final List<FieldValueCount> values,
                       final Long totalCount,
                       final Boolean allTime) {
        this(values, totalCount, allTime, null);
    }

    /// @param values     The most common values, most common first.
    /// @param totalCount The number of documents the values were counted across.
    /// @param allTime    True if the values were counted across all time rather than a time range.
    /// @param scope      A description of the documents the values were counted across, e.g. the time
    ///                   conditions used, or null if the request's time range describes them.
    @JsonCreator
    public FieldValues(@JsonProperty("values") final List<FieldValueCount> values,
                       @JsonProperty("totalCount") final Long totalCount,
                       @JsonProperty("allTime") final Boolean allTime,
                       @JsonProperty("scope") final String scope) {
        this.values = values == null
                ? Collections.emptyList()
                : values;
        this.totalCount = totalCount == null
                ? 0
                : totalCount;
        this.allTime = Boolean.TRUE.equals(allTime);
        this.scope = scope;
    }

    /// @return The most common values, most common first.
    public List<FieldValueCount> getValues() {
        return values;
    }

    /// @return The number of documents the values were counted across.
    public long getTotalCount() {
        return totalCount;
    }

    /// @return True if the values were counted across all time rather than a time range.
    public boolean isAllTime() {
        return allTime;
    }

    /// @return A description of the documents the values were counted across, or null if the request's time
    /// range describes them.
    public String getScope() {
        return scope;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FieldValues that = (FieldValues) o;
        return totalCount == that.totalCount
               && allTime == that.allTime
               && Objects.equals(values, that.values)
               && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, totalCount, allTime, scope);
    }

    @Override
    public String toString() {
        return "FieldValues{" +
               "values=" + values +
               ", totalCount=" + totalCount +
               ", allTime=" + allTime +
               ", scope='" + scope + '\'' +
               '}';
    }
}
