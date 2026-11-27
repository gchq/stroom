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

package stroom.ai.impl.tools.compare;

import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/// Runs the aggregates `compare_activity` asks ([AggregateQuery]), as the user: by ES|QL on an Elastic Index, by
/// StroomQL on any other. An interface so the tool's tests run on events held in memory.
interface AggregateRunner {

    /// Runs aggregates, a query's time range cut into slices (months) run in parallel where it is bucketed by time.
    ///
    /// @param queries The aggregates.
    /// @param context The call's zone, limits and stop.
    /// @return Each query's table, in the order given.
    List<Table> run(List<AggregateQuery> queries, Context context);


    // --------------------------------------------------------------------------------


    /// What a run is under.
    ///
    /// @param zone      The person's zone: a day bucket is a day in it.
    /// @param timeLimit How long each query may take.
    /// @param workers   The most queries run at once.
    /// @param stopped   True once the user has stopped the call.
    record Context(ZoneId zone, Duration timeLimit, int workers, BooleanSupplier stopped) {

    }


    // --------------------------------------------------------------------------------


    /// An aggregate's rows, each a value by column (`b` for the bucket, `g0`... for the group fields, then the
    /// metrics' names). An hour bucket and times are ISO instants in UTC, a day bucket `yyyy-MM-dd`, numbers as
    /// written; a missing value is empty.
    ///
    /// @param rows     The rows.
    /// @param cutShort True if the rows reached the query's limit, so there may be more.
    /// @param queries  How many queries were run for it (one per slice).
    /// @param notes    What the result should say of it (e.g. a cluster not applying the zone); empty for none.
    /// @param text     The first slice's query as run, for the audit log.
    record Table(List<Map<String, String>> rows, boolean cutShort, int queries, List<String> notes, String text) {

        /// Copies the lists.
        public Table {
            rows = List.copyOf(NullSafe.list(rows));
            notes = List.copyOf(NullSafe.list(notes));
        }
    }
}
