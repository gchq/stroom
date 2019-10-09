package stroom.searchable.impl;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("unused")
class SearchableStoreFactory implements StoreFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SearchableStoreFactory.class);

    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final SearchableProvider searchableProvider;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    SearchableStoreFactory(final TaskContext taskContext,
                           final ExecutorProvider executorProvider,
                           final SearchableProvider searchableProvider,
                           final StroomPropertyService stroomPropertyService) {
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.searchableProvider = searchableProvider;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        LOGGER.debug("create called for searchRequest {} ", searchRequest);

        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                        Preconditions.checkNotNull(searchRequest)
                                .getQuery())
                        .getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

        final Searchable searchable = searchableProvider.get(docRef);

        Preconditions.checkNotNull(searchable, "Searchable could not be found for " + docRef);

        return buildStore(searchRequest, searchable);
    }

    private Store buildStore(final SearchRequest searchRequest,
                             final Searchable searchable) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(searchable);

        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        //wrap the resultHandler in a new store, initiating the search in the process
        return new SearchableStore(
                defaultMaxResultsSizes,
                storeSize,
                resultHandlerBatchSize,
                searchable,
                taskContext,
                searchRequest,
                executorProvider);
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = stroomPropertyService.getProperty("stroom.search.storeSize");
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return 5000;
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
