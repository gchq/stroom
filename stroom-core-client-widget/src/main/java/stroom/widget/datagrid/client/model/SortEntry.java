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

package stroom.widget.datagrid.client.model;

import java.util.Objects;

/// One column in a sort list, with its direction.
///
/// @param <C> The type of the column objects.
public final class SortEntry<C> {

    private final C column;
    private final boolean ascending;

    /// @param column    The sorted column.
    /// @param ascending `true` for ascending, `false` for descending.
    public SortEntry(final C column, final boolean ascending) {
        this.column = Objects.requireNonNull(column);
        this.ascending = ascending;
    }

    /// @return The sorted column.
    public C getColumn() {
        return column;
    }

    /// @return `true` for ascending, `false` for descending.
    public boolean isAscending() {
        return ascending;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SortEntry<?> that = (SortEntry<?>) o;
        return ascending == that.ascending && column.equals(that.column);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, ascending);
    }

    @Override
    public String toString() {
        return column + (ascending
                ? " asc"
                : " desc");
    }
}
