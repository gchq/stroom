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

package stroom.ai.impl.tools.query;

import stroom.ai.api.ElasticEsql;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.TimeRange;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/// Whether an index a tool would read by default holds any data (in a period, where one is given), so one that holds
/// none isn't offered: an Elastic Index by an ES|QL count of its documents on its time field, as the user; a Lucene
/// index by its shards' document counts, ruling out shards whose partition lies outside the period. A check that
/// can't be made (a cluster that can't be reached, say) keeps the index, saying it couldn't be checked.
public class IndexDataCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexDataCheck.class);

    /// How long an Elastic Index's count may take.
    static final Duration COUNT_TIME_LIMIT = Duration.ofSeconds(10);
    /// The most shards of a Lucene index read.
    static final int MAX_SHARDS = 5_000;
    /// Why an index with no documents in the period is left out.
    public static final String NO_DOCUMENTS_IN_PERIOD = "no documents in the period";
    /// Why an index with no documents at all is left out.
    public static final String NO_DOCUMENTS = "no documents";
    /// Why a Lucene index with no shards is left out.
    public static final String NO_SHARDS = "no shards";
    /// Why a Lucene index whose shards' partitions all lie outside the period is left out.
    public static final String NO_SHARDS_IN_PERIOD = "no shards in the period";
    /// Why an Elastic Index whose index name stands for nothing on its cluster is left out.
    public static final String NO_INDEX = "no index of its name on its cluster";
    // How Elasticsearch says an ES|QL query's FROM names nothing.
    private static final String UNKNOWN_INDEX = "unknown index";

    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final Provider<IndexResource> indexResourceProvider;
    private final SecurityContext securityContext;

    /// @param elasticEsqlProvider   Counts an Elastic Index's documents, as the user.
    /// @param indexResourceProvider Lists a Lucene index's shards.
    /// @param securityContext       Reads the shards as the processing user, for an index the user can use.
    @Inject
    public IndexDataCheck(final Provider<ElasticEsql> elasticEsqlProvider,
                          final Provider<IndexResource> indexResourceProvider,
                          final SecurityContext securityContext) {
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.indexResourceProvider = indexResourceProvider;
        this.securityContext = securityContext;
    }

    /// Checks each index, keeping those that hold data and those that couldn't be checked.
    ///
    /// @param docs    The indexes, each one the user can use.
    /// @param from    The period's start (inclusive), or null for none.
    /// @param to      The period's end (exclusive), or null for none.
    /// @param stopped True once the caller wants the checks stopped; null for never.
    /// @return Those kept, in order, and those left out or not checked, with why.
    /// @throws TaskTerminatedException if stopped.
    public Checked checkAll(final List<DocRef> docs,
                            final Instant from,
                            final Instant to,
                            final BooleanSupplier stopped) {
        final List<DocRef> kept = new ArrayList<>();
        final List<Holding> empty = new ArrayList<>();
        final List<Holding> unchecked = new ArrayList<>();
        for (final DocRef doc : NullSafe.list(docs)) {
            final Holding holding = check(doc, from, to, stopped);
            switch (holding.state()) {
                case EMPTY -> empty.add(holding);
                case UNKNOWN -> {
                    unchecked.add(holding);
                    kept.add(doc);
                }
                default -> kept.add(doc);
            }
        }
        return new Checked(kept, empty, unchecked);
    }

    /// Checks one index.
    ///
    /// @param doc     The index, one the user can use: an Elastic Index or a Lucene index (any other isn't checked).
    /// @param from    The period's start (inclusive), or null for none.
    /// @param to      The period's end (exclusive), or null for none.
    /// @param stopped True once the caller wants the check stopped; null for never.
    /// @return Whether it holds data, with why where it doesn't or that isn't known.
    /// @throws TaskTerminatedException if stopped.
    public Holding check(final DocRef doc, final Instant from, final Instant to, final BooleanSupplier stopped) {
        if (doc == null) {
            return new Holding(null, State.UNKNOWN, "no index");
        }
        if (stopped != null && stopped.getAsBoolean()) {
            throw new TaskTerminatedException();
        }
        if (ElasticIndexDoc.TYPE.equals(doc.getType())) {
            return elastic(doc, from, to, stopped);
        } else if (LuceneIndexDoc.TYPE.equals(doc.getType())) {
            return lucene(doc, from, to);
        }
        return new Holding(doc, State.UNKNOWN, "not an Elastic or Lucene index");
    }

    // A count of the Elastic Index's documents in the period; where it has no time field to count on, of all of them.
    private Holding elastic(final DocRef doc, final Instant from, final Instant to, final BooleanSupplier stopped) {
        final ElasticEsql esql = elasticEsqlProvider.get();
        if (esql == null) {
            return new Holding(doc, State.UNKNOWN, "ES|QL isn't available");
        }
        final TimeRange range = from == null && to == null
                ? null
                : new TimeRange(null, NullSafe.get(from, Instant::toString), NullSafe.get(to, Instant::toString));
        try {
            return elasticCount(esql, doc, range, stopped);
        } catch (final ElasticEsql.Refusal refusal) {
            if (refusal.reason() == ElasticEsql.Reason.INVALID && range != null) {
                // No time field to count the period on: all its documents counted instead.
                try {
                    return elasticCount(esql, doc, null, stopped);
                } catch (final ElasticEsql.Refusal again) {
                    return refused(doc, again);
                } catch (final RuntimeException e) {
                    return failed(doc, e);
                }
            }
            return refused(doc, refusal);
        } catch (final TaskTerminatedException e) {
            throw e;
        } catch (final RuntimeException e) {
            return failed(doc, e);
        }
    }

    private static Holding elasticCount(final ElasticEsql esql,
                                        final DocRef doc,
                                        final TimeRange range,
                                        final BooleanSupplier stopped) {
        final ElasticEsql.Result result = esql.query(new ElasticEsql.Request(doc, "| STATS n = COUNT(*)", null,
                range, 1, COUNT_TIME_LIMIT, false, stopped));
        if (result == null || result.rows().isEmpty() || result.rows().getFirst().isEmpty()
            || !(result.rows().getFirst().getFirst() instanceof final Number count)) {
            return new Holding(doc, State.UNKNOWN, "Elasticsearch gave no count");
        }
        if (count.longValue() > 0) {
            return new Holding(doc, State.DATA, null);
        }
        return new Holding(doc, State.EMPTY, range == null
                ? NO_DOCUMENTS
                : NO_DOCUMENTS_IN_PERIOD);
    }

    private static Holding refused(final DocRef doc, final ElasticEsql.Refusal refusal) {
        if (refusal.reason() == ElasticEsql.Reason.STOPPED) {
            throw new TaskTerminatedException();
        }
        final String message = NullSafe.nonBlankStringElse(refusal.getMessage(), refusal.reason().name());
        if (refusal.reason() == ElasticEsql.Reason.REFUSED
            && message.toLowerCase(Locale.ROOT).contains(UNKNOWN_INDEX)) {
            return new Holding(doc, State.EMPTY, NO_INDEX);
        }
        return new Holding(doc, State.UNKNOWN, message);
    }

    private static Holding failed(final DocRef doc, final RuntimeException e) {
        LOGGER.debug(() -> LogUtil.message("The documents of {} weren't counted: {}", doc, e.getMessage()), e);
        return new Holding(doc, State.UNKNOWN, NullSafe.nonBlankStringElse(e.getMessage(),
                e.getClass().getSimpleName()));
    }

    // The Lucene index's shards: none, or none in the period, or none of those holding a document, is no data.
    private Holding lucene(final DocRef doc, final Instant from, final Instant to) {
        final List<IndexShard> shards;
        final boolean all;
        try {
            // Listing shards needs Manage Index Shards, which a user who may search the index needn't have; only
            // whether it holds documents is said.
            final ResultPage<IndexShard> page = securityContext.asProcessingUserResult(() ->
                    indexResourceProvider.get().find(FindIndexShardCriteria.builder()
                            .pageRequest(new PageRequest(0, MAX_SHARDS))
                            .indexUuidSet(new Selection<>(false, Set.of(doc.getUuid())))
                            .build()));
            if (page == null || page.getValues() == null) {
                return new Holding(doc, State.UNKNOWN, "its shards couldn't be listed");
            }
            shards = page.getValues().stream()
                    .filter(shard -> doc.getUuid().equals(shard.getIndexUuid())
                                     && shard.getStatus() != IndexShardStatus.DELETED)
                    .toList();
            all = page.getValues().size() < MAX_SHARDS;
        } catch (final RuntimeException e) {
            return failed(doc, e);
        }
        if (shards.isEmpty()) {
            return all
                    ? new Holding(doc, State.EMPTY, NO_SHARDS)
                    : new Holding(doc, State.UNKNOWN, "more shards than could be read");
        }
        final List<IndexShard> inPeriod = shards.stream()
                .filter(shard -> overlaps(shard, from, to))
                .toList();
        if (inPeriod.isEmpty()) {
            return new Holding(doc, State.EMPTY, NO_SHARDS_IN_PERIOD);
        }
        if (inPeriod.stream().anyMatch(shard -> shard.getDocumentCount() > 0)) {
            return new Holding(doc, State.DATA, null);
        }
        if (!all) {
            return new Holding(doc, State.UNKNOWN, "more shards than could be read");
        }
        return new Holding(doc, State.EMPTY, from == null && to == null
                ? NO_DOCUMENTS
                : NO_DOCUMENTS_IN_PERIOD);
    }

    /// @param shard A shard.
    /// @param from  The period's start (inclusive), or null for none.
    /// @param to    The period's end (exclusive), or null for none.
    /// @return True if the shard's partition may hold events in the period: it has no partition times, or they
    /// overlap it.
    static boolean overlaps(final IndexShard shard, final Instant from, final Instant to) {
        final Long partitionFrom = shard.getPartitionFromTime();
        final Long partitionTo = shard.getPartitionToTime();
        if (to != null && partitionFrom != null && partitionFrom >= to.toEpochMilli()) {
            return false;
        }
        return from == null || partitionTo == null || partitionTo > from.toEpochMilli();
    }

    /// @param holdings Indexes and why each was left out or not checked.
    /// @return Each as `name (why)`, separated by `; `.
    public static String describe(final List<Holding> holdings) {
        return String.join("; ", NullSafe.list(holdings).stream()
                .map(holding -> NullSafe.nonBlankStringElse(holding.doc().getName(), holding.doc().getUuid())
                                + " (" + holding.why() + ")")
                .toList());
    }


    // --------------------------------------------------------------------------------


    /// Whether an index holds data.
    public enum State {
        /// It holds at least one document (in the period).
        DATA,
        /// It holds none (in the period).
        EMPTY,
        /// It couldn't be checked.
        UNKNOWN
    }


    // --------------------------------------------------------------------------------


    /// An index checked.
    ///
    /// @param doc   The index.
    /// @param state Whether it holds data.
    /// @param why   Why it holds none, or why it couldn't be checked; null where it holds data.
    public record Holding(DocRef doc, State state, String why) {

    }


    // --------------------------------------------------------------------------------


    /// Indexes checked.
    ///
    /// @param kept      Those holding data or not checked, in order.
    /// @param empty     Those left out, holding none.
    /// @param unchecked Those kept that couldn't be checked.
    public record Checked(List<DocRef> kept, List<Holding> empty, List<Holding> unchecked) {

        /// Copies the lists.
        public Checked {
            kept = List.copyOf(kept);
            empty = List.copyOf(empty);
            unchecked = List.copyOf(unchecked);
        }

        /// @return What was left out and not checked, e.g. `; left out, holding no data: A (no shards); couldn't
        /// check for data: B (timed out)`; empty if nothing was.
        public String notes() {
            final StringBuilder sb = new StringBuilder();
            if (!empty.isEmpty()) {
                sb.append("; left out, holding no data: ").append(describe(empty));
            }
            if (!unchecked.isEmpty()) {
                sb.append("; couldn't check for data: ").append(describe(unchecked));
            }
            return sb.toString();
        }
    }
}
