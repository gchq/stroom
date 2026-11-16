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

package stroom.ai.api;

import stroom.docref.DocRef;
import stroom.query.api.TimeRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/// Runs an EQL sequence over the indexes of one or more Elastic Index documents on one cluster, as the user: ordered
/// hunts across sources (a device plugged in and then a file copied to it; 20 failed logons then a success), matched
/// by Elasticsearch itself. It only reads: nothing in Elasticsearch is changed. Implemented by Stroom's
/// Elasticsearch module, which needs Use on every document (as a search does) and queries with their cluster's
/// connection.
///
/// The caller writes the EQL; the indexes are the documents', so a query can't reach another index. A step that is
/// to match only one document's events says so with a condition on [#DOC_FIELD], a runtime field holding the
/// position (from 1) of each document whose index holds the event. The caller audits the query, as Stroom audits a
/// search where it is asked for.
///
/// As with [ElasticEsql], the events' streams aren't looked up: the sequences come straight from Elasticsearch, and
/// may hold events of streams the user can't see, or that are no longer in Stroom. The caller says so.
public interface ElasticEql {

    /// The most sequences a search may return.
    int MAX_SEQUENCES = 10_000;

    /// The runtime field holding, for each event, the position (from 1, as a string) of each document of the
    /// request whose index holds it: e.g. `stroom_eql_doc == "2"` in a step matches the second document's events.
    String DOC_FIELD = "stroom_eql_doc";

    /// What the documents are, checked before a search is written: Use on each, one cluster, one time field.
    ///
    /// @param indexes The Elastic Index documents, in order.
    /// @return Their cluster's version, time field and index names.
    /// @throws Refusal saying why they can't be searched together, and what differs.
    /// @throws stroom.util.shared.PermissionException if the user may not use one of them, naming it.
    Target target(List<DocRef> indexes);

    /// Runs an EQL search.
    ///
    /// @param request The documents, the EQL, the time range, the fields to fetch, the size and the time limit.
    /// @return The sequences, how long it took and the cluster's version.
    /// @throws Refusal saying why it wasn't run, or why Elasticsearch refused it.
    /// @throws stroom.util.shared.PermissionException if the user may not use one of the documents, naming it.
    Result search(Request request);


    // --------------------------------------------------------------------------------


    /// Why a search wasn't run, or failed.
    enum Reason {
        /// A document doesn't exist.
        NOT_FOUND,
        /// A document has no index name or cluster.
        NOT_QUERYABLE,
        /// The documents are on different clusters ([Refusal#names()] has each document and its cluster).
        DIFFERENT_CLUSTERS,
        /// The documents have different time fields ([Refusal#names()] has each document and its time field).
        DIFFERENT_TIME_FIELDS,
        /// A document has no time field, which an EQL sequence orders its events by.
        NO_TIME_FIELD,
        /// The cluster is too old for EQL.
        CLUSTER_TOO_OLD,
        /// Elasticsearch doesn't know a field the query names.
        UNKNOWN_FIELD,
        /// The query took longer than it may.
        TIMEOUT,
        /// The caller stopped the search while it ran (e.g. the user stopped the chat): it was deleted in
        /// Elasticsearch, so it stopped there too.
        STOPPED,
        /// The cluster is short of memory or overloaded right now (a transient circuit breaker, or a rejected
        /// execution): the same search may work shortly.
        CLUSTER_BUSY,
        /// The search needs more memory than Elasticsearch allows it (a permanent circuit breaker, EQL's sequence
        /// breaker or the request breaker): it must ask for less, not be run again as it is.
        TOO_MUCH_MEMORY,
        /// The time range can't be read.
        INVALID,
        /// Elasticsearch refused the query for another reason, given in its own words.
        REFUSED,
        /// The cluster couldn't be reached, or its answer couldn't be read.
        CLUSTER_ERROR
    }


    // --------------------------------------------------------------------------------


    /// The documents' cluster and time field, and what their mappings say of their fields.
    ///
    /// @param version    The cluster's version, e.g. `9.5.3`.
    /// @param timeField  The documents' time field, which orders a sequence's events.
    /// @param indexNames Each document's index name (or pattern), in order.
    /// @param fields     Each document's fields as its mapping has them, by name, in the documents' order; empty
    ///                   if not known.
    record Target(String version, String timeField, List<String> indexNames, List<Map<String, Field>> fields) {

        /// Copies the lists.
        public Target {
            indexNames = indexNames == null
                    ? List.of()
                    : List.copyOf(indexNames);
            fields = fields == null
                    ? List.of()
                    : fields.stream().map(Map::copyOf).toList();
        }

        /// Documents whose fields aren't known.
        ///
        /// @param version    The cluster's version.
        /// @param timeField  The documents' time field.
        /// @param indexNames Each document's index name (or pattern), in order.
        public Target(final String version, final String timeField, final List<String> indexNames) {
            this(version, timeField, indexNames, null);
        }
    }


    // --------------------------------------------------------------------------------


    /// A field of a document, as its mapping has it: what EQL needs to know to search it.
    ///
    /// @param name             Its full name, e.g. `process.command_line.raw`.
    /// @param nativeType       Its Elasticsearch type, e.g. `text`, `keyword`, `wildcard`.
    /// @param multiFieldParent The field it is a multi-field (subfield) of, or null if it is none's.
    /// @param normaliser       Its `normalizer` (keyword fields), or null for none.
    /// @param nested           True if it is, or is inside, a `nested` field, which EQL can't search.
    record Field(String name, String nativeType, String multiFieldParent, String normaliser, boolean nested) {

    }


    // --------------------------------------------------------------------------------


    /// A search to run.
    ///
    /// @param indexes   The Elastic Index documents, in order: [#DOC_FIELD] holds their positions from 1.
    /// @param query     The EQL, e.g. `sequence with maxspan=2m [any where ...] by user [any where ...] by user`.
    /// @param timeRange A time range on the documents' time field, or null.
    /// @param fields    The fields to fetch of each event (the time field is always fetched, in epoch ms).
    /// @param size      The most sequences to return; at most [#MAX_SEQUENCES].
    /// @param timeLimit How long the search may take, or null for none.
    /// @param stopped   True once the caller wants the search stopped (e.g. the user stopped the chat), asked
    ///                  while it runs; null for never. A search stopped is deleted in Elasticsearch, and refused
    ///                  with [Reason#STOPPED].
    record Request(List<DocRef> indexes,
                   String query,
                   TimeRange timeRange,
                   List<String> fields,
                   int size,
                   Duration timeLimit,
                   BooleanSupplier stopped) {

        /// Copies the lists; a null `stopped` is never stopped.
        public Request {
            indexes = indexes == null
                    ? List.of()
                    : List.copyOf(indexes);
            fields = fields == null
                    ? List.of()
                    : List.copyOf(fields);
            stopped = stopped == null
                    ? () -> false
                    : stopped;
        }

        /// A search that is never stopped by its caller (only by its time limit).
        ///
        /// @param indexes   The Elastic Index documents, in order.
        /// @param query     The EQL.
        /// @param timeRange A time range on the documents' time field, or null.
        /// @param fields    The fields to fetch of each event.
        /// @param size      The most sequences to return.
        /// @param timeLimit How long the search may take, or null for none.
        public Request(final List<DocRef> indexes,
                       final String query,
                       final TimeRange timeRange,
                       final List<String> fields,
                       final int size,
                       final Duration timeLimit) {
            this(indexes, query, timeRange, fields, size, timeLimit, null);
        }
    }


    // --------------------------------------------------------------------------------


    /// What a search gave.
    ///
    /// @param query      The EQL run.
    /// @param indexNames The index names (or patterns) searched.
    /// @param timeField  The time field the events are ordered by.
    /// @param sequences  The sequences found, in Elasticsearch's order.
    /// @param tookMs     How long Elasticsearch took, in milliseconds.
    /// @param version    The cluster's version.
    record Result(String query,
                  List<String> indexNames,
                  String timeField,
                  List<Sequence> sequences,
                  long tookMs,
                  String version) {

        /// Copies the lists.
        public Result {
            indexNames = indexNames == null
                    ? List.of()
                    : List.copyOf(indexNames);
            sequences = sequences == null
                    ? List.of()
                    : List.copyOf(sequences);
        }
    }


    // --------------------------------------------------------------------------------


    /// One sequence.
    ///
    /// @param joinKeys The values it was joined on, in the order the steps name their join fields.
    /// @param events   Its events in step order: a step with `runs` has that many, a missing step one
    ///                 [Event#missing()] event, and an `until` step none.
    record Sequence(List<Object> joinKeys, List<Event> events) {

        /// Copies the lists.
        public Sequence {
            joinKeys = joinKeys == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(joinKeys));
            events = events == null
                    ? List.of()
                    : List.copyOf(events);
        }
    }


    // --------------------------------------------------------------------------------


    /// One event of a sequence.
    ///
    /// @param index   The concrete index holding it, or empty for a missing event.
    /// @param id      Its document id, or empty for a missing event.
    /// @param missing True for a missing event (`![...]`), which has no fields.
    /// @param fields  The fields fetched, each a list of values: a `String`, `Long`, `Double` or `Boolean`. The time
    ///                field's value is its epoch milliseconds.
    record Event(String index, String id, boolean missing, Map<String, List<Object>> fields) {

        /// Copies the map.
        public Event {
            fields = fields == null
                    ? Map.of()
                    : Map.copyOf(fields);
        }
    }


    // --------------------------------------------------------------------------------


    /// A search that wasn't run, or that Elasticsearch refused, with why.
    class Refusal extends RuntimeException {

        private final Reason reason;
        private final List<String> names;

        /// @param reason  Why.
        /// @param message What to tell the caller.
        /// @param names   What the refusal is about, e.g. each document and its cluster; null for none.
        public Refusal(final Reason reason, final String message, final List<String> names) {
            super(message);
            this.reason = reason;
            this.names = names == null
                    ? List.of()
                    : List.copyOf(names);
        }

        /// @param reason  Why.
        /// @param message What to tell the caller.
        public Refusal(final Reason reason, final String message) {
            this(reason, message, null);
        }

        /// @return Why.
        public Reason reason() {
            return reason;
        }

        /// @return What the refusal is about; empty for none.
        public List<String> names() {
            return names;
        }
    }
}
