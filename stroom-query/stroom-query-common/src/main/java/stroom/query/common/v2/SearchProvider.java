package stroom.query.common.v2;

import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.DataSourceProvider;

public interface SearchProvider extends DataSourceProvider {

    ResultStore createResultStore(SearchRequest searchRequest);
}
