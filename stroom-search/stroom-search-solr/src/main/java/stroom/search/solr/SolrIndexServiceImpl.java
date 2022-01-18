package stroom.search.solr;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.search.solr.search.SolrSearchResponseCreatorManager;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class SolrIndexServiceImpl implements SolrIndexService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexServiceImpl.class);

    private final SolrIndexStore solrIndexStore;
    private final SecurityContext securityContext;
    private final Provider<SolrSearchResponseCreatorManager> searchResponseCreatorManagerProvider;

    @Inject
    SolrIndexServiceImpl(final SolrIndexStore solrIndexStore,
                         final SecurityContext securityContext,
                         final Provider<SolrSearchResponseCreatorManager> searchResponseCreatorManagerProvider) {
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
        this.searchResponseCreatorManagerProvider = searchResponseCreatorManagerProvider;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
            return new DataSource(SolrIndexDataSourceFieldUtil.getDataSourceFields(index));
        });
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        //if this is the first call for this query key then it will create a searchResponseCreator (& store) that have
        //a lifespan beyond the scope of this request and then begin the search for the data
        //If it is not the first call for this query key then it will return the existing searchResponseCreator with
        //access to whatever data has been found so far
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManagerProvider.get()
                .get(new SearchResponseCreatorCache.Key(request));

        //create a response from the data found so far, this could be complete/incomplete
        SearchResponse searchResponse = searchResponseCreator.create(request);

        LOGGER.trace(() ->
                getResponseInfoForLogging(request, searchResponse));

        return searchResponse;
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        LOGGER.trace(() -> "keepAlive() " + queryKey);
        return searchResponseCreatorManagerProvider.get()
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
        searchResponseCreatorManagerProvider.get().remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }

    @Override
    public String getType() {
        return SolrIndexDoc.DOCUMENT_TYPE;
    }
}
