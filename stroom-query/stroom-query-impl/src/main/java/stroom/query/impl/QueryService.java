package stroom.query.impl;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.ResourceGeneration;

import java.util.List;
import java.util.Optional;

public interface QueryService {

    QueryDoc read(DocRef docRef);

    QueryDoc update(QueryDoc doc);

    ValidateExpressionResult validateQuery(String expressionString);

    ResourceGeneration downloadSearchResults(DownloadQueryResultsRequest request);

    DashboardSearchResponse search(QuerySearchRequest request);

//    Boolean destroy(DestroyQueryRequest request);

    List<String> fetchTimeZones();

    Optional<DataSource> getDataSource(DocRef docRef);

    Optional<DataSource> getDataSource(String query);

    Documentation fetchDocumentation(DocRef docRef);
}
