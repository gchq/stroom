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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/// Reads the events of Elastic Index documents in join-key order, a page at a time, as the user: the two sides of a
/// join each streamed in key order, so the caller can merge them holding only the current key's rows (a sort-merge
/// join), never bounded by Elasticsearch's 10,000-hit window, its buckets limit or ES|QL's 10,000 rows. It only
/// reads: nothing in Elasticsearch is changed. Implemented by Stroom's Elasticsearch module, which needs Use on
/// every document (as a search does) and queries with their cluster's connection.
///
/// Two ways to read a side, both in key order:
///
/// - **Keys** ([#keyPage]): a composite aggregation on the join key, with the side's filter as its query, paged by
///   its `after_key`: every key once, with how many events it has, without `max_buckets`.
/// - **Events** ([#openScan], [#scanPage], [#close]): a point-in-time search sorted by the key and then the time
///   field, paged with `search_after`, fetching only the fields asked for.
///
/// Paged and stateless: each call takes and gives its own position, so the caller drives the paging, its workers
/// each read a key range ([KeyRange], cut from [#sampleKeys]) through the same methods, and a run stops at any page.
/// Every call checks Use on its document again, takes its own time limit and a stop flag, as [ElasticEsql.Request]
/// takes `stopped`. The caller audits what it reads, as Stroom audits a search where it is asked for.
///
/// A join field can be transformed ([Transform]: lowercased, its domain stripped, a path's file name), so values
/// written differently by two sources compare equal: Elasticsearch computes the transformed key (a runtime field)
/// and sorts by it, so the events still come in the order of the key the caller compares.
///
/// As with [ElasticEsql] and [ElasticEql], the events' streams aren't looked up: the events come straight from
/// Elasticsearch, and may be of streams the user can't see, or that are no longer in Stroom. The caller says so.
public interface ElasticKeyStreams {

    /// The most keys or events one page may hold.
    int MAX_PAGE = 10_000;

    /// The most values a terms condition may hold, as Elasticsearch's own default (`index.max_terms_count`).
    int MAX_TERMS = 65_536;

    /// What the documents are, checked before anything is read: Use on each, one cluster, each one's time field
    /// (which may differ, or be missing for a document that is only read by key) and its fields.
    ///
    /// @param indexes The Elastic Index documents, in order.
    /// @return Their cluster's version and each document's index name, time field and fields.
    /// @throws Refusal saying why they can't be read together, and what differs.
    /// @throws stroom.util.shared.PermissionException if the user may not use one of them, naming it.
    Target target(List<DocRef> indexes);

    /// A page of a side's keys, in key order: a composite aggregation on the join key, the side's filter its query.
    ///
    /// @param request The side, the key to read after (null for the first page), the page's size and time limit.
    /// @return The keys, each with its count of events, and the key to read the next page after (null at the end).
    /// @throws Refusal saying why it wasn't read, or why Elasticsearch refused it.
    /// @throws stroom.util.shared.PermissionException if the user may not use the document.
    KeyPage keyPage(KeyRequest request);

    /// Opens a scan of a side's events in key order, then time order: a point in time on the document's index.
    /// Nothing is read until [#scanPage]; [#close] closes it, as each page read keeps it open a while longer.
    ///
    /// @param request The side, the fields to fetch, the page's size, the time limit and the stop flag.
    /// @return The scan, at its start.
    /// @throws Refusal saying why it can't be opened: e.g. the document has no time field to order by.
    /// @throws stroom.util.shared.PermissionException if the user may not use the document.
    Scan openScan(ScanRequest request);

    /// Reads the next page of a scan.
    ///
    /// @param request The scan, where it has got to, and the time this page may take.
    /// @return The events, in key then time order, and the scan where this page leaves it (null at the end).
    /// @throws Refusal saying why it wasn't read; [Reason#SCAN_EXPIRED] if the point in time has gone.
    /// @throws stroom.util.shared.PermissionException if the user may not use the document.
    ScanPage scanPage(NextPage request);

    /// Closes a scan's point in time. Never fails: one that can't be closed is removed by Elasticsearch when its
    /// keep-alive passes.
    ///
    /// @param scan The scan, or null for none.
    void close(Scan scan);

    /// Every Nth key of a side, in key order, to cut the key space into ranges of about the same number of events
    /// for workers: the key at which the running count of events passes each multiple of `everyRows`.
    ///
    /// @param request The side, how many events a range is to hold, the most boundaries, and the time limit.
    /// @return The boundaries (values of the first key field, each the last key of its range), and how many keys
    ///         and events the side has.
    /// @throws Refusal saying why it wasn't read.
    /// @throws stroom.util.shared.PermissionException if the user may not use the document.
    Sample sampleKeys(SampleRequest request);


    // --------------------------------------------------------------------------------


    /// Why a read wasn't made, or failed.
    enum Reason {
        /// A document doesn't exist.
        NOT_FOUND,
        /// A document has no index name or cluster.
        NOT_QUERYABLE,
        /// The documents are on different clusters ([Refusal#names()] has each document and its cluster).
        DIFFERENT_CLUSTERS,
        /// The document has no time field, which a scan orders each key's events by.
        NO_TIME_FIELD,
        /// A join field can't be a key: e.g. a text field without a keyword subfield, a nested field, or a
        /// transform on a field that isn't text.
        KEY_NOT_USABLE,
        /// Elasticsearch doesn't know a field the request names.
        UNKNOWN_FIELD,
        /// The filter can't be used, e.g. it has a vector search.
        FILTER_NOT_SUPPORTED,
        /// The request can't be read: e.g. a time range, or more terms than allowed.
        INVALID,
        /// The point in time a scan reads has gone (it was idle longer than its keep-alive): open it again.
        SCAN_EXPIRED,
        /// The read took longer than it may.
        TIMEOUT,
        /// The caller stopped the read while it ran (e.g. the user stopped the chat).
        STOPPED,
        /// The cluster is short of memory or overloaded right now: the same read may work shortly.
        CLUSTER_BUSY,
        /// The read needs more memory than Elasticsearch allows it: it must ask for less.
        TOO_MUCH_MEMORY,
        /// The cluster is too old for a point in time (7.10).
        CLUSTER_TOO_OLD,
        /// Elasticsearch refused the read for another reason, given in its own words.
        REFUSED,
        /// The cluster couldn't be reached, or its answer couldn't be read.
        CLUSTER_ERROR
    }


    // --------------------------------------------------------------------------------


    /// A transform of a join field's value, so values two sources write differently compare equal. Computed by
    /// Elasticsearch for each event (so events are ordered by the transformed key); [#apply(String)] is the same
    /// transform in Java, for the caller's own values (e.g. a pushed-down side's keys, examples in a diagnosis).
    enum Transform {
        /// Lowercased: `JSmith` is `jsmith`.
        LOWERCASE,
        /// The domain stripped: `CORP\jsmith` and `jsmith@corp.example` are `jsmith`.
        STRIP_DOMAIN,
        /// A path's file name: `C:\Users\jsmith\report.docx` and `/home/jsmith/report.docx` are `report.docx`.
        FILE_NAME;

        /// @param value A value, or null.
        /// @return It transformed; null for null.
        public String apply(final String value) {
            if (value == null) {
                return null;
            }
            return switch (this) {
                case LOWERCASE -> value.toLowerCase(Locale.ROOT);
                case STRIP_DOMAIN -> {
                    final int slash = value.lastIndexOf('\\');
                    final String user = slash >= 0
                            ? value.substring(slash + 1)
                            : value;
                    final int at = user.indexOf('@');
                    yield at > 0
                            ? user.substring(0, at)
                            : user;
                }
                case FILE_NAME -> {
                    final int end = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
                    yield end >= 0
                            ? value.substring(end + 1)
                            : value;
                }
            };
        }

        /// @param transforms Transforms, applied in order.
        /// @param value      A value, or null.
        /// @return It transformed by each in turn.
        public static String applyAll(final List<Transform> transforms, final String value) {
            String transformed = value;
            for (final Transform transform : transforms == null
                    ? List.<Transform>of()
                    : transforms) {
                transformed = transform.apply(transformed);
            }
            return transformed;
        }
    }


    // --------------------------------------------------------------------------------


    /// The documents' cluster, and what each document is.
    ///
    /// @param version The cluster's version, e.g. `9.5.3`.
    /// @param docs    Each document, in the order asked for.
    record Target(String version, List<Doc> docs) {

        /// Copies the list.
        public Target {
            docs = docs == null
                    ? List.of()
                    : List.copyOf(docs);
        }
    }


    // --------------------------------------------------------------------------------


    /// One document of a [Target].
    ///
    /// @param doc       The document.
    /// @param indexName Its index name (or pattern).
    /// @param timeField Its time field, or null if it has none (it can then only be read by key).
    /// @param fields    Its fields as its mapping has them, by name; empty if not known.
    record Doc(DocRef doc, String indexName, String timeField, Map<String, ElasticEql.Field> fields) {

        /// Copies the map.
        public Doc {
            fields = fields == null
                    ? Map.of()
                    : Map.copyOf(fields);
        }
    }


    // --------------------------------------------------------------------------------


    /// One join field of a side.
    ///
    /// @param field      The field, as the document's mapping names it (a text field's keyword subfield).
    /// @param transforms What is done to its value before it is compared, in order; empty for nothing.
    record KeyField(String field, List<Transform> transforms) {

        /// Copies the list.
        public KeyField {
            transforms = transforms == null
                    ? List.of()
                    : List.copyOf(transforms);
        }

        /// A field compared as it is.
        ///
        /// @param field The field.
        public KeyField(final String field) {
            this(field, null);
        }
    }


    // --------------------------------------------------------------------------------


    /// A range of the first join field's values (as transformed), in key order.
    ///
    /// @param after The value the range starts after (exclusive), or null for the first key.
    /// @param upTo  The last value of the range (inclusive), or null for the last key.
    record KeyRange(Object after, Object upTo) {

        /// Every key.
        public static final KeyRange ALL = new KeyRange(null, null);

        /// @return True if the range is every key.
        public boolean all() {
            return after == null && upTo == null;
        }
    }


    // --------------------------------------------------------------------------------


    /// One side of a join: what of a document is read.
    ///
    /// @param index     The Elastic Index document.
    /// @param keys      Its join fields, in the order the keys compare (at least one).
    /// @param filter    A Stroom expression its events must match (as a search's expression is), or null.
    /// @param timeRange A time range on the document's time field, or null.
    /// @param range     The range of the first join field's values to read; null for every key.
    /// @param terms     For each join field, the values (as transformed) its key must be one of (a pushed-down
    ///                  side), or an empty list for any; empty for no restriction. At most [#MAX_TERMS] a field.
    record Side(DocRef index,
                List<KeyField> keys,
                ExpressionOperator filter,
                TimeRange timeRange,
                KeyRange range,
                List<List<Object>> terms) {

        /// Copies the lists; a null range is every key.
        public Side {
            keys = keys == null
                    ? List.of()
                    : List.copyOf(keys);
            range = range == null
                    ? KeyRange.ALL
                    : range;
            terms = terms == null
                    ? List.of()
                    : terms.stream()
                            .map(values -> values == null
                                    ? List.of()
                                    : Collections.unmodifiableList(new ArrayList<>(values)))
                            .toList();
        }

        /// @param otherRange Another range.
        /// @param otherTerms Other terms (see the record).
        /// @return This side over that range with those terms.
        public Side restricted(final KeyRange otherRange, final List<List<Object>> otherTerms) {
            return new Side(index, keys, filter, timeRange, otherRange, otherTerms);
        }
    }


    // --------------------------------------------------------------------------------


    /// A page of keys to read.
    ///
    /// @param side      The side.
    /// @param after     The key the page starts after (a value per join field), as the last page gave it; null
    ///                  for the first page.
    /// @param size      The most keys; at most [#MAX_PAGE].
    /// @param timeLimit How long the page may take, or null for none.
    /// @param stopped   True once the caller wants the read stopped; null for never.
    record KeyRequest(Side side, List<Object> after, int size, Duration timeLimit, BooleanSupplier stopped) {

        /// Copies the key; a null `stopped` is never stopped.
        public KeyRequest {
            after = after == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(after));
            stopped = stopped == null
                    ? () -> false
                    : stopped;
        }
    }


    // --------------------------------------------------------------------------------


    /// A key and how many events of the side it has.
    ///
    /// @param key   A value per join field: a `String`, `Long`, `Double` or `Boolean`.
    /// @param count Its events.
    record KeyCount(List<Object> key, long count) {

        /// Copies the key.
        public KeyCount {
            key = key == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(key));
        }
    }


    // --------------------------------------------------------------------------------


    /// A page of keys.
    ///
    /// @param keys   The keys, in key order.
    /// @param after  The key to read the next page after, or null if there are no more.
    /// @param tookMs How long Elasticsearch took, in milliseconds.
    record KeyPage(List<KeyCount> keys, List<Object> after, long tookMs) {

        /// Copies the lists.
        public KeyPage {
            keys = keys == null
                    ? List.of()
                    : List.copyOf(keys);
            after = after == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(after));
        }
    }


    // --------------------------------------------------------------------------------


    /// A scan of a side's events to open.
    ///
    /// @param side      The side.
    /// @param fields    The fields to fetch of each event, beside its key and time.
    /// @param size      The most events a page; at most [#MAX_PAGE].
    /// @param timeLimit How long opening may take, or null for none.
    /// @param stopped   True once the caller wants the scan stopped; null for never.
    record ScanRequest(Side side, List<String> fields, int size, Duration timeLimit, BooleanSupplier stopped) {

        /// Copies the list; a null `stopped` is never stopped.
        public ScanRequest {
            fields = fields == null
                    ? List.of()
                    : List.copyOf(fields);
            stopped = stopped == null
                    ? () -> false
                    : stopped;
        }
    }


    // --------------------------------------------------------------------------------


    /// A scan, where it has got to.
    ///
    /// @param request     What it reads.
    /// @param pointInTime The point in time's id.
    /// @param searchAfter The sort values of the last event read, or null before the first page.
    record Scan(ScanRequest request, String pointInTime, List<Object> searchAfter) {

        /// Copies the sort values.
        public Scan {
            searchAfter = searchAfter == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(searchAfter));
        }
    }


    // --------------------------------------------------------------------------------


    /// The next page of a scan to read.
    ///
    /// @param scan      The scan.
    /// @param timeLimit How long the page may take, or null for none.
    record NextPage(Scan scan, Duration timeLimit) {

    }


    // --------------------------------------------------------------------------------


    /// One event of a scan.
    ///
    /// @param key    A value per join field (as transformed).
    /// @param time   Its time in epoch milliseconds, or null if it has none.
    /// @param index  The concrete index holding it.
    /// @param id     Its document id.
    /// @param fields The fields fetched, each a list of values: a `String`, `Long`, `Double` or `Boolean`.
    record Row(List<Object> key, Long time, String index, String id, Map<String, List<Object>> fields) {

        /// Copies the key and the map.
        public Row {
            key = key == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(key));
            fields = fields == null
                    ? Map.of()
                    : Map.copyOf(fields);
        }
    }


    // --------------------------------------------------------------------------------


    /// A page of a scan.
    ///
    /// @param rows   The events, in key then time order.
    /// @param next   The scan where this page leaves it, or null if there are no more events (it is then closed).
    /// @param tookMs How long Elasticsearch took, in milliseconds.
    record ScanPage(List<Row> rows, Scan next, long tookMs) {

        /// Copies the list.
        public ScanPage {
            rows = rows == null
                    ? List.of()
                    : List.copyOf(rows);
        }
    }


    // --------------------------------------------------------------------------------


    /// A sample of a side's keys to take.
    ///
    /// @param side      The side (its range and terms are kept).
    /// @param everyRows How many events a range is to hold: a boundary each time the running count passes a
    ///                  multiple of it.
    /// @param most      The most boundaries.
    /// @param timeLimit How long the sample may take, or null for none.
    /// @param stopped   True once the caller wants the read stopped; null for never.
    record SampleRequest(Side side, long everyRows, int most, Duration timeLimit, BooleanSupplier stopped) {

        /// A null `stopped` is never stopped.
        public SampleRequest {
            stopped = stopped == null
                    ? () -> false
                    : stopped;
        }
    }


    // --------------------------------------------------------------------------------


    /// A sample of a side's keys.
    ///
    /// @param boundaries Values of the first join field in key order, each the last key of a range.
    /// @param keys       How many keys (of the first join field) the side has.
    /// @param rows       How many events the side has.
    /// @param tookMs     How long Elasticsearch took, in milliseconds.
    record Sample(List<Object> boundaries, long keys, long rows, long tookMs) {

        /// Copies the list.
        public Sample {
            boundaries = boundaries == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(boundaries));
        }
    }


    // --------------------------------------------------------------------------------


    /// A read that wasn't made, or that Elasticsearch refused, with why.
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
