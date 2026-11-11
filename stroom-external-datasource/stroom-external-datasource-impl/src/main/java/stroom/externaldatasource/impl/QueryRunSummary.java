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

import java.util.List;

/// What running a query read.
///
/// @param columns         The columns the query returned.
/// @param unmatchedFields The query's fields that no column supplied.
/// @param rowsRead        The rows read.
/// @param rowsPassed      The rows that passed the `where` expression.
/// @param limitReached    True if there were more rows than the limit, so some were not read.
/// @param maxRows         The row limit.
/// @param info            Something to tell the user about the run, such as the data Amazon Athena
///                        scanned, or `null`.
/// @param warnings        Problems found while reading, such as values that are not of their column's
///                        type.
public record QueryRunSummary(List<SqlColumn> columns,
                              List<String> unmatchedFields,
                              long rowsRead,
                              long rowsPassed,
                              boolean limitReached,
                              int maxRows,
                              String info,
                              List<String> warnings) {

    /// Creates a summary.
    public QueryRunSummary {
        warnings = List.copyOf(warnings);
    }

    /// Creates a summary with no warnings.
    ///
    /// @param columns         The columns the query returned.
    /// @param unmatchedFields The query's fields that no column supplied.
    /// @param rowsRead        The rows read.
    /// @param rowsPassed      The rows that passed the `where` expression.
    /// @param limitReached    True if there were more rows than the limit, so some were not read.
    /// @param maxRows         The row limit.
    /// @param info            Something to tell the user about the run, or `null`.
    public QueryRunSummary(final List<SqlColumn> columns,
                           final List<String> unmatchedFields,
                           final long rowsRead,
                           final long rowsPassed,
                           final boolean limitReached,
                           final int maxRows,
                           final String info) {
        this(columns, unmatchedFields, rowsRead, rowsPassed, limitReached, maxRows, info, List.of());
    }
}
