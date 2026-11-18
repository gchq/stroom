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

package stroom.dataconnection.impl;

import java.util.List;

/// Counts the rows a query reads, and those that pass its `where` expression, up to its row limit.
///
/// Every kind of connection reads its rows the same way: each row is taken until the limit is
/// reached, and a row past the limit, if there is one, shows that the limit was reached.
final class RowCounter {

    private final int maxRows;
    private long rowsRead;
    private long rowsPassed;
    private boolean limitReached;

    /// Creates a counter.
    ///
    /// @param maxRows The most rows to read.
    RowCounter(final int maxRows) {
        this.maxRows = maxRows;
    }

    /// Takes the next row, unless the limit has been reached, in which case the row is left and no
    /// more should be read.
    ///
    /// @param row Gives the row to the row consumer, returning true if it passed the `where`
    ///            expression.
    /// @param <E> The exception giving the row may throw.
    /// @return True if the row was taken, false if the limit has been reached.
    /// @throws E If giving the row fails.
    <E extends Exception> boolean take(final Row<E> row) throws E {
        if (rowsRead >= maxRows) {
            limitReached = true;
            return false;
        }
        rowsRead++;
        if (row.accept()) {
            rowsPassed++;
        }
        return true;
    }

    /// @return The rows read so far.
    long rowsRead() {
        return rowsRead;
    }

    /// @return True if there were more rows than the limit, so some were not read.
    boolean limitReached() {
        return limitReached;
    }

    /// Sums up a query that read nothing, as when it was cancelled before its rows were read.
    ///
    /// @param maxRows The row limit.
    /// @return The summary.
    static QueryRunSummary nothingRead(final int maxRows) {
        return new RowCounter(maxRows).summary(List.of(), null, List.of());
    }

    /// Sums up what was read.
    ///
    /// @param unmatchedFields The query's fields that no column supplied.
    /// @param info            Something to tell the user about the run, or `null`.
    /// @param warnings        Problems found while reading.
    /// @return The summary.
    QueryRunSummary summary(final List<String> unmatchedFields, final String info, final List<String> warnings) {
        return new QueryRunSummary(unmatchedFields, rowsRead, rowsPassed, limitReached, maxRows, info, warnings);
    }


    // --------------------------------------------------------------------------------


    /// Gives a row to the row consumer.
    ///
    /// @param <E> The exception giving the row may throw.
    @FunctionalInterface
    interface Row<E extends Exception> {

        /// Gives the row to the row consumer.
        ///
        /// @return True if the row passed the `where` expression.
        /// @throws E If giving the row fails.
        boolean accept() throws E;
    }
}
