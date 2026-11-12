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

import stroom.query.api.ExpressionOperator;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// A query checked by its [Connector] and ready to run.
///
/// @param expression The expression to filter rows with in Stroom, or `null` for none. It may differ
///                   from the query's own, e.g. when the time range is applied in Stroom.
/// @param warnings   Warnings to show with the results, known before the query runs.
/// @param run        Runs the query.
record PreparedQuery(ExpressionOperator expression, List<String> warnings, Run run) {

    PreparedQuery {
        warnings = List.copyOf(Objects.requireNonNull(warnings));
        Objects.requireNonNull(run);
    }

    /// Runs a prepared query, feeding its rows to a row consumer.
    @FunctionalInterface
    interface Run {

        /// @param rowConsumerFactory Makes the row consumer once the columns are known.
        /// @param cancellation       Stops the work when the search is cancelled.
        /// @param progress           Takes messages about the query's progress.
        /// @return What the run read.
        /// @throws Exception If the query fails.
        QueryRunSummary run(Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                            QueryCancellation cancellation,
                            Consumer<String> progress) throws Exception;
    }
}
