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

import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;

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

    /// Creates a page of values with no filter error.
    ///
    /// @param values         The page's values, available ones first.
    /// @param pageResponse   Where the page is in all of the values.
    /// @param availableCount The number of available values across all pages.
    public ColumnValues(final List<ColumnValue> values,
                        final PageResponse pageResponse,
                        final long availableCount) {
        this(values, pageResponse, null, availableCount);
    }

    @JsonCreator
    public ColumnValues(@JsonProperty("values") final List<ColumnValue> values,
                        @JsonProperty("pageResponse") final PageResponse pageResponse,
                        @JsonProperty("filterError") final TokenError filterError,
                        @JsonProperty("availableCount") final Long availableCount) {
        super(values, pageResponse, filterError);
        this.availableCount = NullSafe.getLong(availableCount);
    }

    /// Creates an empty page with no values.
    ///
    /// @return An empty page.
    public static ColumnValues empty() {
        return new ColumnValues(Collections.emptyList(), PageResponse.empty(), 0L);
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
