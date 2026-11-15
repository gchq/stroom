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

package stroom.search.elastic.esql;

import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.util.NamedValue;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Leaves out of an ES|QL query the documents of streams the user can't see, as a Stroom search of an index does:
/// a search looks up each hit's stream ([MetaService#getMeta(long)], which needs View on its feed and finds only
/// unlocked streams) and drops the hits whose stream it can't find. So the documents of a deleted stream, or of a
/// feed the user may not view, are in no answer.
///
/// The index's distinct `StreamId` values, within the query's filter, are read with a `composite` aggregation, each
/// is looked up in Stroom's meta as the user, and those not found are given to the query as a `must_not` filter.
/// An index without a `StreamId` field is left as it is, as a search leaves its hits.
public class EsqlStreamFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EsqlStreamFilter.class);

    /// The most distinct streams read for one query: more is refused, to narrow the filter or time range.
    static final int MAX_STREAMS = 100_000;
    /// The most streams left out of one query, as Elasticsearch takes at most this many terms in a query.
    static final int MAX_EXCLUDED = 65_536;
    private static final int PAGE = 10_000;
    private static final int META_BATCH = 1_000;
    private static final String STREAMS_AGG = "streams";
    private static final String ID_SOURCE = "id";

    private final Provider<MetaService> metaServiceProvider;

    /// @param metaServiceProvider Finds the streams the user can see.
    @Inject
    public EsqlStreamFilter(final Provider<MetaService> metaServiceProvider) {
        this.metaServiceProvider = metaServiceProvider;
    }

    /// Finds the streams of the documents the query reads that the user can't see.
    ///
    /// @param client    The cluster's client.
    /// @param indexDoc  The Elastic Index doc.
    /// @param indexName Its index name.
    /// @param filter    The query's filter, or null.
    /// @param deadline  When the query must be done by.
    /// @return The filter that leaves their documents out (null if none need leaving out), and how many streams.
    /// @throws Refusal if there are too many streams to check, or too many to leave out.
    Excluded exclude(final ElasticsearchClient client,
                     final ElasticIndexDoc indexDoc,
                     final String indexName,
                     final Query filter,
                     final Instant deadline) {
        if (!hasStreamIds(indexDoc)) {
            return Excluded.NONE;
        }
        final Set<Long> streamIds = streamIds(client, indexName, filter, deadline);
        if (streamIds.isEmpty()) {
            return Excluded.NONE;
        }
        final Set<Long> unseen = new LinkedHashSet<>(streamIds);
        unseen.removeAll(visible(streamIds));
        if (unseen.isEmpty()) {
            return Excluded.NONE;
        }
        if (unseen.size() > MAX_EXCLUDED) {
            throw new Refusal(Reason.REFUSED, "The documents come from " + unseen.size() + " streams you can't "
                                              + "see (or that are no longer in Stroom), more than can be left out "
                                              + "of a query: narrow the filter or the time range");
        }
        LOGGER.debug(() -> LogUtil.message("Leaving out the documents of {} of {} streams", unseen.size(),
                streamIds.size()));
        final List<FieldValue> values = unseen.stream().map(FieldValue::of).toList();
        final Query exclusion = QueryBuilders.bool(b -> b.mustNot(QueryBuilders.terms(t -> t
                .field(IndexConstants.STREAM_ID)
                .terms(v -> v.value(values)))));
        return new Excluded(exclusion, unseen.size());
    }

    private static boolean hasStreamIds(final ElasticIndexDoc indexDoc) {
        return NullSafe.list(indexDoc.getFields()).stream()
                .anyMatch(field -> field != null && IndexConstants.STREAM_ID.equals(field.getFldName())
                                   && isNumeric(field));
    }

    private static boolean isNumeric(final ElasticIndexField field) {
        return Set.of("long", "integer", "unsigned_long", "short").contains(field.getNativeType());
    }

    // The distinct stream ids of the documents matching the filter.
    private static Set<Long> streamIds(final ElasticsearchClient client,
                                       final String indexName,
                                       final Query filter,
                                       final Instant deadline) {
        final Set<Long> streamIds = new LinkedHashSet<>();
        final Query query = filter == null
                ? QueryBuilders.matchAll(m -> m)
                : filter;
        Map<String, FieldValue> after = null;
        do {
            if (Instant.now().isAfter(deadline)) {
                throw new Refusal(Reason.TIMEOUT, "Checking which streams the documents come from took too long: "
                                                  + "narrow the filter or the time range");
            }
            final Map<String, FieldValue> from = after;
            final CompositeAggregate page;
            try {
                final SearchResponse<Void> response = client.search(s -> s
                        .index(indexName)
                        .size(0)
                        .query(query)
                        .aggregations(STREAMS_AGG, a -> a.composite(c -> {
                            c.size(PAGE).sources(NamedValue.of(ID_SOURCE, CompositeAggregationSource.of(source ->
                                    source.terms(t -> t.field(IndexConstants.STREAM_ID)))));
                            if (from != null) {
                                c.after(from);
                            }
                            return c;
                        })), Void.class);
                page = response.aggregations().get(STREAMS_AGG).composite();
            } catch (final ElasticsearchException e) {
                final String reason = e.error() == null
                        ? e.getMessage()
                        : e.error().reason();
                throw new Refusal(Reason.REFUSED, "Elasticsearch refused to say which streams the documents come "
                                                  + "from: " + reason);
            } catch (final IOException | RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to read the stream ids: {}", e.getMessage()), e);
                throw new Refusal(Reason.CLUSTER_ERROR, "Couldn't read which streams the documents come from: "
                                                        + NullSafe.nonBlankStringElse(e.getMessage(),
                        e.getClass().getSimpleName()));
            }
            final List<CompositeBucket> buckets = page.buckets().array();
            for (final CompositeBucket bucket : buckets) {
                final FieldValue id = bucket.key().get(ID_SOURCE);
                if (id != null && id.isLong()) {
                    streamIds.add(id.longValue());
                } else if (id != null && id.isDouble()) {
                    streamIds.add((long) id.doubleValue());
                }
            }
            if (streamIds.size() > MAX_STREAMS) {
                throw new Refusal(Reason.REFUSED, "The documents come from more than " + MAX_STREAMS + " streams, "
                                                  + "too many to check which you can see: narrow the filter or "
                                                  + "the time range");
            }
            after = buckets.size() < PAGE
                    ? null
                    : page.afterKey();
        } while (after != null && !after.isEmpty());
        return streamIds;
    }

    // Those of the streams the user can see, as a search looks each up.
    private Set<Long> visible(final Set<Long> streamIds) {
        final MetaService metaService = metaServiceProvider.get();
        final List<Long> ids = new ArrayList<>(streamIds);
        final Set<Long> visible = new LinkedHashSet<>();
        for (int start = 0; start < ids.size(); start += META_BATCH) {
            final List<Long> batch = ids.subList(start, Math.min(ids.size(), start + META_BATCH));
            final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                    .addTerm(MetaFields.ID.getFldName(), Condition.IN, batch.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")))
                    .addTerm(MetaFields.STATUS.getFldName(), Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                    .build());
            criteria.setPageRequest(new PageRequest(0, batch.size()));
            final ResultPage<Meta> found = metaService.find(criteria);
            NullSafe.list(NullSafe.get(found, ResultPage::getValues)).stream()
                    .map(Meta::getId)
                    .forEach(visible::add);
        }
        return visible;
    }


    // --------------------------------------------------------------------------------


    /// The streams left out.
    ///
    /// @param query   The filter that leaves their documents out, or null for none.
    /// @param streams How many streams.
    record Excluded(Query query, int streams) {

        static final Excluded NONE = new Excluded(null, 0);
    }
}
