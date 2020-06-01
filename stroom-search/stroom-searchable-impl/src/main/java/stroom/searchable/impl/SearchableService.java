package stroom.searchable.impl;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
// Used by DI
class SearchableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableService.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SearchableService.class);

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final SearchableProvider searchableProvider;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SecurityContext securityContext;

    @Inject
    SearchableService(final SearchableProvider searchableProvider,
                      final SearchableSearchResponseCreatorManager searchResponseCreatorManager,
                      final SecurityContext securityContext) {
        this.searchableProvider = searchableProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.securityContext = securityContext;
    }

     DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug("getDataSource called for docRef {}", docRef);
            final Searchable searchable = searchableProvider.get(docRef);
            if (searchable == null) {
                return null;
            }
            return searchable.getDataSource();
        });
    }

    public SearchResponse search(final SearchRequest searchRequest) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug("search called for searchRequest {}", searchRequest);

            // Replace expression parameters.
            final Query query = searchRequest.getQuery();
            if (query != null) {
                ExpressionOperator expression = query.getExpression();
                final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
                expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
                query.setExpression(expression);
            }

            final DocRef docRef = Preconditions.checkNotNull(
                    Preconditions.checkNotNull(
                            Preconditions.checkNotNull(searchRequest)
                                    .getQuery())
                            .getDataSource());
            Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
            Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

            final Searchable searchable = searchableProvider.get(docRef);
            if (searchable == null) {
                return buildEmptyResponse(
                        searchRequest,
                        "Searchable could not be found for uuid " + docRef.getUuid());
            } else {
                return buildResponse(searchRequest, searchable);
            }
        });
    }

     Boolean destroy(final QueryKey queryKey) {
        LOGGER.debug("destroy called for queryKey {}", queryKey);
        // remove the creator from the cache which will trigger the onRemove listener
        // which will call destroy on the store
        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }

    private SearchResponse buildResponse(final SearchRequest searchRequest,
                                         final Searchable searchable) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(searchable);

        // This will create/get a searchResponseCreator for this query key
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(
                new SearchResponseCreatorCache.Key(searchRequest));

        // This will build a response from the search whether it is still running or has finished
        return searchResponseCreator.create(searchRequest);
    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final String errorMessage) {
        return buildEmptyResponse(searchRequest, Collections.singletonList(errorMessage));
    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final List<String> errorMessages) {

        List<Result> results;
        if (searchRequest.getResultRequests() != null) {
            results = searchRequest.getResultRequests().stream()
                    .map(resultRequest -> new TableResult(
                            resultRequest.getComponentId(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            new OffsetRange(0, 0),
                            0,
                            null))
                    .collect(Collectors.toList());
        } else {
            results = Collections.emptyList();
        }

        return new SearchResponse(
                Collections.emptyList(),
                results,
                errorMessages,
                true);
    }
}
