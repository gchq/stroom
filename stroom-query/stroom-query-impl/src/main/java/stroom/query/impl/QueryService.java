package stroom.query.impl;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.shared.DestroyQueryRequest;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.ResourceGeneration;

import java.util.List;

public interface QueryService {

    QueryDoc read(DocRef docRef);

    QueryDoc update(QueryDoc doc);

    ValidateExpressionResult validateQuery(String expressionString);

    ResourceGeneration downloadSearchResults(DownloadQueryResultsRequest request);

    DashboardSearchResponse search(QuerySearchRequest request);

    Boolean destroy(DestroyQueryRequest request);

    List<String> fetchTimeZones();
}
