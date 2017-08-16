package stroom.resources.query.v2;

import stroom.datasource.api.v2.DataSource;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.resources.HasHealthCheck;
import stroom.resources.NamedResource;

public interface QueryResource extends HasHealthCheck, NamedResource {

    String DATA_SOURCE_ENDPOINT = "/dataSource";
    String SEARCH_ENDPOINT = "/search";
    String DESTROY_ENDPOINT = "/destroy";

    DataSource getDataSource(final DocRef docRef);

    SearchResponse search(final SearchRequest request);

    Boolean destroy(final QueryKey queryKey);


}
