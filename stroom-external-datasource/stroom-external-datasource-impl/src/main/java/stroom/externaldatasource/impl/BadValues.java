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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.util.logging.LogUtil;

import java.util.Objects;
import java.util.Optional;

/// Counts the values of a query that are not of their column's type, keeping the first as an example,
/// so the query can warn once rather than fail.
final class BadValues {

    private long count;
    private String example;

    /// Records a value that is not of its column's type.
    ///
    /// @param text   The value as read.
    /// @param column Its column.
    void add(final String text, final DataColumn column) {
        if (count == 0) {
            example = "'" + text + "' in column '" + column.getName() + "' ("
                      + Objects.requireNonNullElse(column.getType(), DataColumnType.TEXT).getDisplayValue()
                      + ")";
        }
        count++;
    }

    /// @return A warning saying how many values were not of their column's type, with an example, or
    ///         nothing if every value was.
    Optional<String> warning() {
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(LogUtil.message(
                "{} {} not of {} column's type, so {} empty, e.g. {}.",
                count,
                count == 1 ? "value was" : "values were",
                count == 1 ? "its" : "their",
                count == 1 ? "it is" : "they are",
                example));
    }
}
