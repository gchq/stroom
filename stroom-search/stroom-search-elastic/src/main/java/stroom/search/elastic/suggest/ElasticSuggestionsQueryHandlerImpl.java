/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.elasticsearch.core.search.TermSuggestOption;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Singleton
public class ElasticSuggestionsQueryHandlerImpl implements ElasticSuggestionsQueryHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSuggestionsQueryHandlerImpl.class);

    private final Provider<ElasticClientCache> elasticClientCacheProvider;
    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<ElasticSuggestConfig> elasticSuggestConfigProvider;
    private final Provider<TaskContextFactory> taskContextFactoryProvider;

    @Inject
    public ElasticSuggestionsQueryHandlerImpl(final Provider<ElasticClientCache> elasticClientCacheProvider,
                                              final Provider<ElasticClusterStore> elasticClusterStoreProvider,
                                              final Provider<ElasticIndexStore> elasticIndexStoreProvider,
                                              final Provider<ElasticSuggestConfig> elasticSuggestConfigProvider,
                                              final Provider<TaskContextFactory> taskContextFactoryProvider) {
        this.elasticClientCacheProvider = elasticClientCacheProvider;
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.elasticIndexStoreProvider = elasticIndexStoreProvider;
        this.elasticSuggestConfigProvider = elasticSuggestConfigProvider;
        this.taskContextFactoryProvider = taskContextFactoryProvider;
    }

    @Override
    public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
        final ElasticIndexDoc elasticIndex = elasticIndexStoreProvider.get().readDocument(request.getDataSource());
        final ElasticClusterDoc elasticCluster = elasticClusterStoreProvider.get()
                .readDocument(elasticIndex.getClusterRef());

        final CompletableFuture<Suggestions> future = CompletableFuture.supplyAsync(taskContextFactoryProvider.get()
                .contextResult("Query suggestions for Elasticsearch index '" + elasticIndex.getName() + "'",
                        taskContext -> elasticClientCacheProvider.get().contextResult(elasticCluster.getConnection(),
                                elasticClient -> querySuggestions(request, elasticIndex, elasticClient)
                        )
                )
        );

        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(() -> "Thread interrupted");
            return Suggestions.EMPTY;
        } catch (final ExecutionException e) {
            throw new RuntimeException("Error getting Elasticsearch term suggestions: " + e.getMessage(), e);
        }
    }

    private Suggestions querySuggestions(final FetchSuggestionsRequest request,
                                         final ElasticIndexDoc elasticIndex,
                                         final ElasticsearchClient elasticClient) {
        final QueryField field = request.getField();
        final String query = request.getText();

        try {
            if (!elasticSuggestConfigProvider.get().getEnabled() || query == null || query.isEmpty()) {
                return Suggestions.EMPTY;
            }
            if (!(FieldType.TEXT.equals(field.getFldType()) || FieldType.KEYWORD.equals(field.getFldType()))) {
                // Only generate suggestions for text and keyword fields
                return Suggestions.EMPTY;
            }
            final SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(elasticIndex.getIndexName())
                    .suggest(suggest -> suggest
                            .suggesters("suggest", FieldSuggester.of(suggester -> suggester
                                    .text(query)
                                    .term(t -> t
                                            .field(field.getFldName())
                                            .suggestMode(SuggestMode.Always)
                                            .minWordLength(3)
                                    )
                            ))
                    )
            );

            final SearchResponse<Void> searchResponse = elasticClient.search(searchRequest, Void.class);
            final Map<String, List<Suggestion<Void>>> suggestResponse = searchResponse.suggest();
            final List<Suggestion<Void>> termSuggestion = suggestResponse.get("suggest");

            return new Suggestions(termSuggestion.getFirst().term().options().stream()
                    .map(TermSuggestOption::text)
                    .toList());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> "Failed to retrieve search suggestions for field: " + field.getFldName() +
                               ". " + e.getMessage(), e);
            return Suggestions.EMPTY;
        }
    }
}
