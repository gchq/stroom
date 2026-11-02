/*
 * Copyright 2020 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ColumnValues extends ResultPage<ColumnValue> {

    @JsonProperty
    private final long availableCount;

    @JsonCreator
    public ColumnValues(@JsonProperty("values") final List<ColumnValue> values,
                        @JsonProperty("pageResponse") final PageResponse pageResponse,
                        @JsonProperty("availableCount") final long availableCount) {
        super(values, pageResponse);
        this.availableCount = availableCount;
    }

    /// Creates an empty page with no values.
    ///
    /// @return An empty page.
    public static ColumnValues empty() {
        return new ColumnValues(Collections.emptyList(), PageResponse.empty(), 0);
    }

    /// @return The number of available values across all pages, i.e. values that can currently appear in the
    /// table. Available values are sorted before all others, so this is also the index of the first value that
    /// is not available.
    public long getAvailableCount() {
        return availableCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        final ColumnValues that = (ColumnValues) o;
        return availableCount == that.availableCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), availableCount);
    }
}
