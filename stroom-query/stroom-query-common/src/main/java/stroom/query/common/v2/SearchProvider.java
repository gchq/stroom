package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.query.api.v2.SearchRequest;

public interface SearchProvider extends DataSourceProvider {

    ResultStore createResultStore(SearchRequest searchRequest);
}
