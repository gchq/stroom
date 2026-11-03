/*
 * Copyright 2022 Crown Copyright
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

package stroom.search.elastic.suggest;

import stroom.ai.api.AiService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.FieldValueCount;
import stroom.query.shared.FieldValues;
import stroom.query.shared.Suggestions;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.search.ElasticTopValues;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/// Lists the most common values of an Elasticsearch field with a `terms` aggregation, for the value picker of
/// an expression term. The values are counted across the documents in the dashboard's time range, or across
/// all documents if the index has no time field or there is no time range.
@Singleton
public class ElasticSuggestionsQueryHandlerImpl implements ElasticSuggestionsQueryHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSuggestionsQueryHandlerImpl.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Elastic Suggestions Query Handler");
    private static final String AGGREGATION_NAME = "values";

    private final Provider<ElasticClientCache> elasticClientCacheProvider;
    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<ElasticIndexCache> elasticIndexCacheProvider;
    private final Provider<IndexFieldCache> indexFieldCacheProvider;
    private final Provider<WordListProvider> wordListProviderProvider;
    private final Provider<AiService> aiServiceProvider;
    private final Provider<ElasticSuggestConfig> elasticSuggestConfigProvider;
    private final Provider<TaskContextFactory> taskContextFactoryProvider;
    private final Executor executor;

    @Inject
    public ElasticSuggestionsQueryHandlerImpl(final Provider<ElasticClientCache> elasticClientCacheProvider,
                                              final Provider<ElasticClusterStore> elasticClusterStoreProvider,
                                              final Provider<ElasticIndexCache> elasticIndexCacheProvider,
                                              final Provider<IndexFieldCache> indexFieldCacheProvider,
                                              final Provider<WordListProvider> wordListProviderProvider,
                                              final Provider<AiService> aiServiceProvider,
                                              final Provider<ElasticSuggestConfig> elasticSuggestConfigProvider,
                                              final Provider<TaskContextFactory> taskContextFactoryProvider,
                                              final ExecutorProvider executorProvider) {
        this.elasticClientCacheProvider = elasticClientCacheProvider;
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.elasticIndexCacheProvider = elasticIndexCacheProvider;
        this.indexFieldCacheProvider = indexFieldCacheProvider;
        this.wordListProviderProvider = wordListProviderProvider;
        this.aiServiceProvider = aiServiceProvider;
        this.elasticSuggestConfigProvider = elasticSuggestConfigProvider;
        this.taskContextFactoryProvider = taskContextFactoryProvider;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    /// The Elasticsearch term suggester has been replaced by the value picker, so no suggestions are made as
    /// the user types.
    @Override
    public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
        return Suggestions.EMPTY;
    }

    @Override
    public FieldValues getFieldValues(final FetchFieldValuesRequest request) {
        final ElasticSuggestConfig config = elasticSuggestConfigProvider.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return FieldValues.EMPTY;
        }

        // Reading the index through the cache checks the user has permission to search it
        final DocRef dataSource = request.getDataSource();
        final ElasticIndexDoc index = elasticIndexCacheProvider.get().get(dataSource);
        final String fieldName = request.getField().getFldName();
        final ElasticIndexField indexField = getElasticIndexField(dataSource, fieldName);
        if (indexField == null) {
            return FieldValues.EMPTY;
        }
        final String aggregationField = ElasticTopValues.getAggregationField(
                fieldName,
                indexField.getNativeType(),
                name -> NullSafe.get(getElasticIndexField(dataSource, name), ElasticIndexField::getNativeType));
        if (aggregationField == null) {
            LOGGER.debug("Field '{}' of type '{}' can't list its values", fieldName, indexField.getNativeType());
            return FieldValues.EMPTY;
        }

        final String filterRegex = getFilterRegex(request, aggregationField, dataSource);
        final ScopeQuery scopeQuery = getScopeQuery(index, request);
        final ElasticClusterDoc elasticCluster = elasticClusterStoreProvider.get()
                .readDocument(index.getClusterRef());

        final CompletableFuture<FieldValues> future = CompletableFuture.supplyAsync(
                taskContextFactoryProvider.get().contextResult(
                        "Fetch field values for Elasticsearch index '" + index.getName() + "'",
                        taskContext -> elasticClientCacheProvider.get().contextResult(
                                elasticCluster.getConnection(),
                                elasticClient -> queryFieldValues(
                                        elasticClient,
                                        index,
                                        aggregationField,
                                        filterRegex,
                                        scopeQuery,
                                        config.getEffectiveTopValuesCount(),
                                        request.getField().getFldType()))),
                executor);

        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Thread interrupted");
            return FieldValues.EMPTY;
        } catch (final ExecutionException e) {
            throw new RuntimeException(LogUtil.message("Error fetching values of field '{}': {}",
                    fieldName, NullSafe.getOrElse(e.getCause(), Throwable::getMessage, e.getMessage())), e);
        }
    }

    private ElasticIndexField getElasticIndexField(final DocRef dataSource, final String fieldName) {
        final IndexField indexField = indexFieldCacheProvider.get().get(dataSource, fieldName);
        return indexField instanceof final ElasticIndexField elasticIndexField
                ? elasticIndexField
                : null;
    }

    /// @return A regular expression the values must match, or null to list all values. Only fields holding
    /// strings can be filtered.
    private String getFilterRegex(final FetchFieldValuesRequest request,
                                  final String aggregationField,
                                  final DocRef dataSource) {
        if (NullSafe.isBlankString(request.getFilter())) {
            return null;
        }
        final String nativeType = NullSafe.get(
                getElasticIndexField(dataSource, aggregationField),
                ElasticIndexField::getNativeType);
        if (!ElasticTopValues.isFilterable(nativeType)) {
            return null;
        }
        // The filter is a convenience for finding values, so it ignores case whatever the term's options
        return ElasticTopValues.toContainsRegex(request.getFilter().trim(), true);
    }

    /// Creates the query selecting the documents to count values across: those within the dashboard's time
    /// range and the query's own conditions on the index's time field, if the index has its time field.
    /// Without either, all documents are counted.
    private ScopeQuery getScopeQuery(final ElasticIndexDoc index, final FetchFieldValuesRequest request) {
        final String timeField = index.getTimeField();
        final boolean hasTimeField = NullSafe.isNonBlankString(timeField)
                                     && getElasticIndexField(request.getDataSource(), timeField) != null;
        if (!hasTimeField) {
            return new ScopeQuery(MatchAllQuery.of(q -> q)._toQuery(), true, null);
        }

        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                aiServiceProvider,
                index,
                indexFieldCacheProvider.get(),
                wordListProviderProvider.get(),
                getDateTimeSettings(request));

        final List<ExpressionTerm> terms = new ArrayList<>(getTimeRangeTerms(timeField, request.getTimeRange()));
        final boolean hasTimeRange = !terms.isEmpty();
        final List<ExpressionTerm> queryTerms = getUsableTerms(
                builder,
                ElasticTopValues.getTimeRangeTerms(request.getExpression(), timeField));
        terms.addAll(queryTerms);
        if (terms.isEmpty()) {
            return new ScopeQuery(MatchAllQuery.of(q -> q)._toQuery(), true, null);
        }

        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.AND)
                .children(new ArrayList<>(terms))
                .build();
        return new ScopeQuery(
                builder.buildQuery(expression).getQuery(),
                false,
                describeScope(request.getTimeRange(), hasTimeRange, queryTerms));
    }

    /// @return Terms limiting the time field to the dashboard's time range, if it has one.
    private static List<ExpressionTerm> getTimeRangeTerms(final String timeField, final TimeRange timeRange) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        final String from = NullSafe.get(timeRange, TimeRange::getFrom);
        final String to = NullSafe.get(timeRange, TimeRange::getTo);
        if (NullSafe.isNonBlankString(from)) {
            terms.add(ExpressionTerm.builder()
                    .field(timeField)
                    .condition(Condition.GREATER_THAN_OR_EQUAL_TO)
                    .value(from)
                    .build());
        }
        if (NullSafe.isNonBlankString(to)) {
            terms.add(ExpressionTerm.builder()
                    .field(timeField)
                    .condition(Condition.LESS_THAN)
                    .value(to)
                    .build());
        }
        return terms;
    }

    /// Keeps the query's time conditions that can be searched with. One that can't, e.g. because a parameter in
    /// its value has no value, is left out rather than stopping the picker from listing values.
    private static List<ExpressionTerm> getUsableTerms(final SearchExpressionQueryBuilder builder,
                                                       final List<ExpressionTerm> terms) {
        final List<ExpressionTerm> usable = new ArrayList<>();
        for (final ExpressionTerm term : terms) {
            try {
                builder.buildQuery(ExpressionOperator.builder().addTerm(term).build());
                usable.add(term);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Ignoring time condition '{}': {}", term, e.getMessage()), e);
            }
        }
        return usable;
    }

    /// Describes the documents counted when the query's own time conditions were used, e.g.
    /// `Last 7 days and @timestamp >= 2026-10-01`. Otherwise null, so the picker describes them by the
    /// dashboard's time range.
    private static String describeScope(final TimeRange timeRange,
                                        final boolean hasTimeRange,
                                        final List<ExpressionTerm> queryTerms) {
        if (queryTerms.isEmpty()) {
            return null;
        }
        final String conditions = queryTerms.stream()
                .map(term -> term.getField()
                             + " "
                             + term.getCondition().getDisplayValue()
                             + " "
                             + term.getValue().trim())
                .collect(Collectors.joining(" and "));
        return hasTimeRange
                ? NullSafe.getOrElse(timeRange, TimeRange::getName, "Time range") + " and " + conditions
                : conditions;
    }

    /// @return The request's date time settings with a reference time, so that relative times such as
    /// `now() - 1d` can be resolved.
    private static DateTimeSettings getDateTimeSettings(final FetchFieldValuesRequest request) {
        final DateTimeSettings dateTimeSettings = request.getDateTimeSettings();
        if (dateTimeSettings == null) {
            return DateTimeSettings.builder().referenceTime(System.currentTimeMillis()).build();
        } else if (dateTimeSettings.getReferenceTime() == null) {
            return dateTimeSettings.copy().referenceTime(System.currentTimeMillis()).build();
        }
        return dateTimeSettings;
    }

    private FieldValues queryFieldValues(final ElasticsearchClient elasticClient,
                                         final ElasticIndexDoc index,
                                         final String aggregationField,
                                         final String filterRegex,
                                         final ScopeQuery scopeQuery,
                                         final int count,
                                         final FieldType fieldType) {
        final SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(index.getIndexName())
                .size(0)
                .trackTotalHits(t -> t.enabled(true))
                .query(scopeQuery.query())
                .aggregations(AGGREGATION_NAME, a -> a
                        .terms(t -> {
                            t.field(aggregationField).size(count);
                            if (filterRegex != null) {
                                t.include(i -> i.regexp(filterRegex));
                            }
                            return t;
                        })));
        LOGGER.debug(() -> "Field values request: " + searchRequest);

        final SearchResponse<Void> response;
        try {
            response = elasticClient.search(searchRequest, Void.class);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final long totalCount = NullSafe.getOrElse(response.hits().total(), total -> total.value(), 0L);
        final Aggregate aggregate = response.aggregations().get(AGGREGATION_NAME);
        return new FieldValues(
                getValueCounts(aggregate, fieldType),
                totalCount,
                scopeQuery.allTime(),
                scopeQuery.description());
    }

    // Each value is returned with its term value, quoted so that a term holding it matches the value exactly
    private static List<FieldValueCount> getValueCounts(final Aggregate aggregate, final FieldType fieldType) {
        if (aggregate == null) {
            return Collections.emptyList();
        } else if (aggregate.isSterms()) {
            return aggregate.sterms().buckets().array().stream()
                    .map(bucket -> toFieldValueCount(bucket.key().stringValue(), bucket.docCount(), fieldType))
                    .toList();
        } else if (aggregate.isLterms()) {
            // Booleans are aggregated as 0 or 1, with `true` or `false` as the key's string form
            return aggregate.lterms().buckets().array().stream()
                    .map(bucket -> toFieldValueCount(
                            Objects.requireNonNullElse(bucket.keyAsString(), String.valueOf(bucket.key())),
                            bucket.docCount(),
                            fieldType))
                    .toList();
        } else {
            LOGGER.debug("Unexpected aggregation type: {}", aggregate._kind());
            return Collections.emptyList();
        }
    }


    private static FieldValueCount toFieldValueCount(final String value,
                                                     final long count,
                                                     final FieldType fieldType) {
        final String termValue = ElasticTermValueQuoter.quote(fieldType, value);
        return new FieldValueCount(value, termValue.equals(value)
                ? null
                : termValue, count);
    }


    // --------------------------------------------------------------------------------


    /// A query selecting the documents to count values across.
    ///
    /// @param query       The query.
    /// @param allTime     True if the query selects documents from all time rather than a time range.
    /// @param description A description of the documents selected, or null if the dashboard's time range
    ///                    describes them.
    private record ScopeQuery(Query query, boolean allTime, String description) {

    }
}
