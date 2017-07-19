package stroom.resources.query.v1;

import stroom.datasource.api.v1.DataSource;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;
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
