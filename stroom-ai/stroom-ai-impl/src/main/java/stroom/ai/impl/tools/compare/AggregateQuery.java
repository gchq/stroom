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

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// One aggregate `compare_activity` asks of an index, whatever runs it (ES|QL on an Elastic Index, StroomQL on any
/// other): the documents matching some fields' values in a time range, grouped by a time bucket and fields, with
/// counts, sums, distinct counts, first and last times and one event's ids per group. Never the documents
/// themselves.
///
/// @param source  The index.
/// @param filters Each field's values, a document matching one of them for every field; empty for all.
/// @param from    The range's start (inclusive).
/// @param to      The range's end (exclusive).
/// @param bucket  The time bucket grouped by, if any.
/// @param groups  The fields grouped by, besides the bucket: their columns are `g0`, `g1`, ...
/// @param metrics What each group gives.
/// @param limit   The most rows.
record AggregateQuery(Source source,
                      Map<String, List<String>> filters,
                      Instant from,
                      Instant to,
                      Bucket bucket,
                      List<String> groups,
                      List<Metric> metrics,
                      int limit) {

    /// The bucket's column.
    static final String BUCKET_COLUMN = "b";
    /// The prefix of a group field's column.
    static final String GROUP_PREFIX = "g";

    /// Copies the collections.
    AggregateQuery {
        filters = new LinkedHashMap<>(NullSafe.map(filters));
        groups = List.copyOf(NullSafe.list(groups));
        metrics = List.copyOf(NullSafe.list(metrics));
    }

    /// @param from The new start.
    /// @param to   The new end.
    /// @return The same query over another range, e.g. a month's slice.
    AggregateQuery between(final Instant from, final Instant to) {
        return new AggregateQuery(source, filters, from, to, bucket, groups, metrics, limit);
    }

    /// @param index A group field's place.
    /// @return Its column's name.
    static String groupColumn(final int index) {
        return GROUP_PREFIX + index;
    }


    // --------------------------------------------------------------------------------


    /// A time bucket, in the person's zone.
    enum Bucket {
        /// No bucket.
        NONE,
        /// The hour: its column holds the hour's start as an ISO instant (UTC).
        HOUR,
        /// The day in the person's zone: its column holds the date, `yyyy-MM-dd`.
        DAY
    }


    // --------------------------------------------------------------------------------


    /// What a metric is.
    enum Kind {
        /// The documents.
        COUNT,
        /// The sum of a numeric field.
        SUM,
        /// The distinct values of a field.
        DISTINCT,
        /// The earliest time (an ISO instant, UTC).
        MIN_TIME,
        /// The latest time (an ISO instant, UTC).
        MAX_TIME,
        /// One document's `StreamId:EventId`.
        ONE_EVENT
    }


    // --------------------------------------------------------------------------------


    /// A metric each group gives.
    ///
    /// @param kind  What it is.
    /// @param field The field it is of; null for a count or times (the time field), or the ids.
    /// @param name  Its column's name: letters, digits and underscores.
    record Metric(Kind kind, String field, String name) {

    }


    // --------------------------------------------------------------------------------


    /// An index aggregated.
    ///
    /// @param doc       Its document.
    /// @param timeField Its time field.
    /// @param elastic   True for an Elastic Index (ES|QL), false for any other (StroomQL).
    record Source(DocRef doc, String timeField, boolean elastic) {

        /// @return Its name, or uuid if it has none.
        String name() {
            return NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid());
        }
    }
}
