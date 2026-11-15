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
import java.util.Optional;

/// Runs an ES|QL query on the index of an Elastic Index document, as the user: totals, top N and grouped questions
/// answered by Elasticsearch itself, rather than by scrolling every document through Stroom. It only reads: nothing
/// in Elasticsearch is changed. Implemented by Stroom's Elasticsearch module, which needs Use on the Elastic Index
/// document (as a search does) and queries with its cluster's connection.
///
/// The caller gives only the commands after `FROM` (e.g. `| WHERE ... | STATS ... BY ...`): `FROM` and the index
/// are written from the document, so a query can't reach another index. Only commands that work on the rows already
/// read are allowed. The caller audits the query, as Stroom audits a search where it is asked for.
///
/// Unlike a Stroom search, which drops the hits of streams the user can't see or that are no longer in Stroom, a
/// query doesn't look its documents' streams up (for speed, as agents ask many in turn): its counts and sums come
/// straight from Elasticsearch, so they can differ from a search's or a dashboard's. The caller says so.
public interface ElasticEsql {

    /// The most rows a query may return, as Elasticsearch's own cap (`esql.query.result_truncation_max_size`).
    int MAX_ROWS = 10_000;

    /// Runs a query.
    ///
    /// @param request The index, the commands after `FROM`, and any filter, time range, row limit and time limit.
    /// @return The columns and rows, how long it took, the query run, and whether the rows were cut short.
    /// @throws Refusal saying why it wasn't run, or why Elasticsearch refused it, with what to do instead.
    /// @throws stroom.util.shared.PermissionException if the user may not use the Elastic Index document.
    Result query(Request request);

    /// Whether ES|QL can be used on an Elastic Index document's index, for saying so before a query: the user may
    /// use the document and its cluster is 8.14 or later. Never fails: a document that can't be read or a cluster
    /// that can't be reached is empty.
    ///
    /// @param index The Elastic Index document.
    /// @return The cluster's version, or empty if ES|QL can't be used on it, or that isn't known.
    Optional<String> esqlVersion(DocRef index);


    // --------------------------------------------------------------------------------


    /// Why a query wasn't run, or failed.
    enum Reason {
        /// The Elastic Index document doesn't exist.
        NOT_FOUND,
        /// The document has no index name or cluster, or its index name can't be written in `FROM`.
        NOT_QUERYABLE,
        /// A command that isn't allowed, or isn't known: e.g. `FROM` naming an index, `ENRICH` or `LOOKUP JOIN`.
        COMMAND_NOT_ALLOWED,
        /// The commands can't be read: e.g. an unterminated string or comment, or a `LIMIT` that isn't a number.
        INVALID,
        /// Elasticsearch doesn't know a column the query names ([Refusal#names()] has them).
        UNKNOWN_FIELD,
        /// The query groups by a text field that has a keyword sub-field to group by instead ([Refusal#names()]
        /// has the text field, then the sub-field).
        TEXT_GROUPING,
        /// The filter can't be used: e.g. it has a vector search, which ES|QL's filter can't take.
        FILTER_NOT_SUPPORTED,
        /// The cluster is older than 8.14, the first with ES|QL generally available.
        CLUSTER_TOO_OLD,
        /// The query took longer than it may.
        TIMEOUT,
        /// Elasticsearch refused the query for another reason, given in its own words.
        REFUSED,
        /// The cluster couldn't be reached, or its answer couldn't be read.
        CLUSTER_ERROR
    }


    // --------------------------------------------------------------------------------


    /// A query to run.
    ///
    /// @param index      The Elastic Index document.
    /// @param pipeline   The ES|QL commands after `FROM`, e.g. `| STATS n = COUNT(*) BY user.name | SORT n DESC`;
    ///                   the leading `|` may be left out. Empty for the rows as they are.
    /// @param filter     A Stroom expression the documents must match, as a search's expression is, or null.
    /// @param timeRange  A time range on the document's time field, or null.
    /// @param limit      The most rows to return, or null for the configured default; at most [#MAX_ROWS].
    /// @param timeLimit  How long the query may take, or null for the configured limit (never longer).
    /// @param countTotal True to count, when the rows are cut short by the limit, how many rows the query gives
    ///                   without its limits (for `STATS ... BY`, the number of groups), with a second query.
    record Request(DocRef index,
                   String pipeline,
                   ExpressionOperator filter,
                   TimeRange timeRange,
                   Integer limit,
                   Duration timeLimit,
                   boolean countTotal) {

    }


    // --------------------------------------------------------------------------------


    /// A column of the result.
    ///
    /// @param name Its name.
    /// @param type Its ES|QL type, e.g. `long`, `keyword`, `date`.
    record Column(String name, String type) {

    }


    // --------------------------------------------------------------------------------


    /// What a query gave.
    ///
    /// @param query       The ES|QL query run, `FROM` and the limit included.
    /// @param indexName   The index (or pattern) the query read, from the document.
    /// @param columns     The columns.
    /// @param rows        The rows, each a value per column: a `String`, `Long`, `Double`, `Boolean`,
    ///                    `java.math.BigInteger` or `java.math.BigDecimal`, a list of those for a multi-valued
    ///                    column, or null.
    /// @param tookMs      How long Elasticsearch took, in milliseconds.
    /// @param limit       The most rows the query could return: the lowest `LIMIT` it holds, the one added included.
    /// @param limitNote   What was done to the limit (added, or lowered to the most allowed), or null if nothing.
    /// @param truncated   True if the rows reached the limit, so there may be more.
    /// @param grouped     True if the query's last `STATS` groups its rows (`BY`), so each row is a group.
    /// @param totalRows   How many rows the query gives without its limits (for `STATS ... BY`, the number of
    ///                    groups), counted only when asked and truncated; else null.
    /// @param totalNote   Why the total isn't known when it was asked for, or null.
    /// @param version     The cluster's version.
    /// @param warnings    What Elasticsearch warned of while running the query, in its own words (e.g. that a
    ///                    function met a multi-valued field, so gave null), without the limit it added; empty for
    ///                    none.
    record Result(String query,
                  String indexName,
                  List<Column> columns,
                  List<List<Object>> rows,
                  long tookMs,
                  int limit,
                  String limitNote,
                  boolean truncated,
                  boolean grouped,
                  Long totalRows,
                  String totalNote,
                  String version,
                  List<String> warnings) {

        /// Copies the lists, so the result can't be changed.
        public Result {
            warnings = warnings == null
                    ? List.of()
                    : List.copyOf(warnings);
            columns = columns == null
                    ? List.of()
                    : List.copyOf(columns);
            rows = rows == null
                    ? List.of()
                    : rows.stream()
                            .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                            .toList();
        }
    }


    // --------------------------------------------------------------------------------


    /// A query that wasn't run, or that Elasticsearch refused, with why.
    class Refusal extends RuntimeException {

        private final Reason reason;
        private final List<String> names;

        /// @param reason  Why.
        /// @param message What to tell the caller.
        /// @param names   What the refusal is about, e.g. the unknown columns; null for none.
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

        /// @return What the refusal is about, e.g. the unknown columns; empty for none.
        public List<String> names() {
            return names;
        }
    }
}
