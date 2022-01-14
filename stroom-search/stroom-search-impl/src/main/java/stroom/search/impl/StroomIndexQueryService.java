package stroom.search.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.impl.StroomIndexQueryResource;
import stroom.index.shared.IndexDoc;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class StroomIndexQueryService implements DataSourceProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomIndexQueryResource.class);

    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final IndexStore indexStore;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;

    @Inject
    public StroomIndexQueryService(final LuceneSearchResponseCreatorManager searchResponseCreatorManager,
                                   final IndexStore indexStore,
                                   final SecurityContext securityContext,
                                   final TaskContextFactory taskContextFactory,
                                   final ExecutorProvider executorProvider) {
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.indexStore = indexStore;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final Supplier<DataSource> supplier = taskContextFactory.contextResult("Getting Data Source",
                    taskContext -> {
                        final IndexDoc index = indexStore.readDocument(docRef);
                        return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index, securityContext));
                    });
            final Executor executor = executorProvider.get();
            final CompletableFuture<DataSource> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
            try {
                return completableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        final Supplier<SearchResponse> supplier = taskContextFactory.contextResult("Getting search results",
                taskContext -> {
                    // if this is the first call for this query key then it will create a searchResponseCreator
                    // (& store) that have a lifespan beyond the scope of this request and then begin the search for
                    // the data If it is not the first call for this query key then it will return the existing
                    // searchResponseCreator with access to whatever data has been found so far
                    final SearchResponseCreatorCache.Key key = new SearchResponseCreatorCache.Key(request);
                    final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(key);

                    //create a response from the data found so far, this could be complete/incomplete
                    taskContext.info(() -> "Creating search result");
                    SearchResponse searchResponse = searchResponseCreator.create(request);

                    LOGGER.trace(() ->
                            getResponseInfoForLogging(request, searchResponse));

                    return searchResponse;
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<SearchResponse> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        LOGGER.trace(() -> "keepAlive() " + queryKey);
        return searchResponseCreatorManager
                .getOptional(new SearchResponseCreatorCache.Key(queryKey))
                .map(SearchResponseCreator::keepAlive)
                .orElse(Boolean.FALSE);
    }

    private String getResponseInfoForLogging(final SearchRequest request, final SearchResponse searchResponse) {
        String resultInfo;

        if (searchResponse.getResults() != null) {
            resultInfo = "\n" + searchResponse.getResults().stream()
                    .map(result -> {
                        if (result instanceof FlatResult) {
                            FlatResult flatResult = (FlatResult) result;
                            return LogUtil.message(
                                    "  FlatResult - componentId: {}, size: {}, ",
                                    flatResult.getComponentId(),
                                    flatResult.getSize());
                        } else if (result instanceof TableResult) {
                            TableResult tableResult = (TableResult) result;
                            return LogUtil.message(
                                    "  TableResult - componentId: {}, rows: {}, totalResults: {}, " +
                                            "resultRange: {}",
                                    tableResult.getComponentId(),
                                    tableResult.getRows().size(),
                                    tableResult.getTotalResults(),
                                    tableResult.getResultRange());
                        } else {
                            return "  Unknown type " + result.getClass().getName();
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } else {
            resultInfo = "null";
        }

        return LogUtil.message("Return search response, key: {}, result sets: {}, " +
                        "complete: {}, errors: {}, results: {}",
                request.getKey().toString(),
                searchResponse.getResults(),
                searchResponse.complete(),
                searchResponse.getErrors(),
                resultInfo);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Destroy search",
                taskContext -> {
                    taskContext.info(queryKey::getUuid);
                    searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
                    return Boolean.TRUE;
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }
}
