package stroom.statistics.common;

import stroom.datasource.api.DataSource;
import stroom.query.api.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;

public interface StatisticsQueryService {

    DataSource getDataSource(final DocRef docRef);

    SearchResponse search(final SearchRequest request);

    Boolean destroy(final QueryKey queryKey);
}
