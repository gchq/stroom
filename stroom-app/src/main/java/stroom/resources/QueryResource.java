package stroom.resources;

import stroom.datasource.api.DataSource;
import stroom.query.api.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;

public interface QueryResource extends HasHealthCheck, NamedResource {

    String DATASOURCE_ENDPOINT = "/datasource";
    String SEARCH_ENDPOINT = "/search";
    String DESTROY_ENDPOINT = "/destroy";

    DataSource getDataSource(final DocRef docRef);

    SearchResponse search(final SearchRequest request);

    Boolean destroy(final QueryKey queryKey);


}
