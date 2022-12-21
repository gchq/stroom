package stroom.search.elastic.suggest;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.FieldTypes;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskContextFactory;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.SuggestionBuilder;
import org.elasticsearch.search.suggest.term.TermSuggestion;
import org.elasticsearch.search.suggest.term.TermSuggestionBuilder;
import org.elasticsearch.search.suggest.term.TermSuggestionBuilder.SuggestMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ElasticSuggestionsQueryHandlerImpl implements ElasticSuggestionsQueryHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSuggestionsQueryHandlerImpl.class);

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticSuggestConfig elasticSuggestConfig;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public ElasticSuggestionsQueryHandlerImpl(final ElasticClientCache elasticClientCache,
                                              final ElasticClusterStore elasticClusterStore,
                                              final ElasticIndexStore elasticIndexStore,
                                              final ElasticSuggestConfig elasticSuggestConfig,
                                              final TaskContextFactory taskContextFactory) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticSuggestConfig = elasticSuggestConfig;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public List<String> getSuggestions(final FetchSuggestionsRequest request) {
        final ElasticIndexDoc elasticIndex = elasticIndexStore.readDocument(request.getDataSource());
        final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
        final AbstractField field = request.getField();
        final String query = request.getText();

        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(taskContextFactory.contextResult(
                "Query suggestions for Elasticsearch index '" + elasticIndex.getName() + "'",
                taskContext -> elasticClientCache.contextResult(elasticCluster.getConnection(), elasticClient -> {
                    try {
                        if (!elasticSuggestConfig.getEnabled() || query == null || query.length() == 0) {
                            return Collections.emptyList();
                        }
                        if (!(FieldTypes.TEXT.equals(field.getType()) || FieldTypes.KEYWORD.equals(field.getType()))) {
                            // Only generate suggestions for text and keyword fields
                            return Collections.emptyList();
                        }

                        final SuggestionBuilder<TermSuggestionBuilder> termSuggestionBuilder = SuggestBuilders
                                .termSuggestion(field.getName())
                                .suggestMode(SuggestMode.ALWAYS)
                                .minWordLength(3)
                                .text(query);
                        final SuggestBuilder suggestBuilder = new SuggestBuilder()
                                .addSuggestion("suggest", termSuggestionBuilder);
                        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                                .fetchField(field.getName())
                                .suggest(suggestBuilder);
                        final SearchRequest searchRequest = new SearchRequest(elasticIndex.getIndexName())
                                .source(searchSourceBuilder);

                        SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
                        Suggest suggestResponse = searchResponse.getSuggest();
                        TermSuggestion termSuggestion = suggestResponse.getSuggestion("suggest");

                        return termSuggestion.getEntries().stream()
                                .flatMap(entry -> entry.getOptions().stream())
                                .map(option -> option.getText().string())
                                .collect(Collectors.toList());
                    } catch (IOException | RuntimeException e) {
                        LOGGER.error(() -> "Failed to retrieve search suggestions for field: " + field.getName() +
                                ". " + e.getMessage(), e);
                        return new ArrayList<>();
                    }
                })));

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(() -> "Thread interrupted");
            return Collections.emptyList();
        } catch (ExecutionException e) {
            throw new RuntimeException("Error getting Elasticsearch term suggestions: " + e.getMessage(), e);
        }
    }
}
