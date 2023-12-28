package stroom.search.elastic.suggest;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldAndFormat;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.elasticsearch.core.search.TermSuggestOption;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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

        CompletableFuture<Suggestions> future = CompletableFuture.supplyAsync(taskContextFactoryProvider.get()
                .contextResult("Query suggestions for Elasticsearch index '" + elasticIndex.getName() + "'",
                        taskContext -> elasticClientCacheProvider.get().contextResult(elasticCluster.getConnection(),
                                elasticClient -> querySuggestions(request, elasticIndex, elasticClient)
                        )
                )
        );

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(() -> "Thread interrupted");
            return Suggestions.EMPTY;
        } catch (ExecutionException e) {
            throw new RuntimeException("Error getting Elasticsearch term suggestions: " + e.getMessage(), e);
        }
    }

    private Suggestions querySuggestions(final FetchSuggestionsRequest request,
                                          final ElasticIndexDoc elasticIndex,
                                          final ElasticsearchClient elasticClient) {
        final FieldInfo field = request.getField();
        final String query = request.getText();

        try {
            if (!elasticSuggestConfigProvider.get().getEnabled() || query == null || query.isEmpty()) {
                return Suggestions.EMPTY;
            }
            if (!(FieldType.TEXT.equals(field.getFieldType()) || FieldType.KEYWORD.equals(field.getFieldType()))) {
                // Only generate suggestions for text and keyword fields
                return Suggestions.EMPTY;
            }
            final var searchRequest = SearchRequest.of(s -> s
                    .index(elasticIndex.getIndexName())
                    .suggest(suggest -> suggest
                            .suggesters("suggest", FieldSuggester.of(suggester -> suggester
                                    .text(query)
                                    .term(t -> t
                                            .field(field.getFieldName())
                                            .suggestMode(SuggestMode.Always)
                                            .minWordLength(3)
                                    )
                            ))
                    )
            );

            final SearchResponse<Void> searchResponse = elasticClient.search(searchRequest, Void.class);
            final Map<String, List<Suggestion<Void>>> suggestResponse = searchResponse.suggest();
            final List<Suggestion<Void>> termSuggestion = suggestResponse.get("suggest");

            return new Suggestions(termSuggestion.get(0).term().options().stream()
                    .map(TermSuggestOption::text)
                    .collect(Collectors.toList()));
        } catch (IOException | RuntimeException e) {
            LOGGER.error(() -> "Failed to retrieve search suggestions for field: " + field.getFieldName() +
                    ". " + e.getMessage(), e);
            return Suggestions.EMPTY;
        }
    }
}
