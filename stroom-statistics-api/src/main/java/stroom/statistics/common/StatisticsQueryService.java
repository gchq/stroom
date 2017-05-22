package stroom.statistics.common;

import stroom.datasource.api.v1.DataSource;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;

public interface StatisticsQueryService {

    DataSource getDataSource(final DocRef docRef);

    SearchResponse search(final SearchRequest request);

    Boolean destroy(final QueryKey queryKey);
}
