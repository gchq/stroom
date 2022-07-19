package stroom.search.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;

public class StroomIndexQueryService implements DataSourceProvider {

    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final LuceneSearchStoreFactory storeFactory;
    private final IndexStore indexStore;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;

    @Inject
    public StroomIndexQueryService(final SearchResponseCreatorManager searchResponseCreatorManager,
                                   final LuceneSearchStoreFactory storeFactory,
                                   final IndexStore indexStore,
                                   final SecurityContext securityContext,
                                   final TaskContextFactory taskContextFactory,
                                   final ExecutorProvider executorProvider) {
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.storeFactory = storeFactory;
        this.indexStore = indexStore;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final Supplier<DataSource> supplier = taskContextFactory.contextResult(
                    "Getting Data Source",
                    TerminateHandlerFactory.NOOP_FACTORY,
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
        return searchResponseCreatorManager.search(storeFactory, request);
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        return searchResponseCreatorManager.keepAlive(queryKey);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManager.remove(queryKey);
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }
}
