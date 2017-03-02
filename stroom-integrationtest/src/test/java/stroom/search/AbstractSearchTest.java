package stroom.search;

import stroom.AbstractCoreIntegrationTest;
import stroom.query.SearchResponseCreator;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.search.server.SearchResultCreatorManager;

import javax.annotation.Resource;

public abstract class AbstractSearchTest extends AbstractCoreIntegrationTest {
    @Resource
    protected SearchResultCreatorManager searchResultCreatorManager;

    protected SearchResponse search(SearchRequest searchRequest){
        final SearchResponseCreator searchResponseCreator = searchResultCreatorManager.get(new SearchResultCreatorManager.Key(searchRequest));
        return searchResponseCreator.create(searchRequest);
    }

    protected Boolean destroy(final QueryKey queryKey) {
        searchResultCreatorManager.remove(new SearchResultCreatorManager.Key(queryKey));
        return Boolean.TRUE;
    }
}
