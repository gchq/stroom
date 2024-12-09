package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.util.shared.ResourceGeneration;

public interface DashboardService {

    DashboardDoc create();

    DashboardDoc read(DocRef docRef);

    DashboardDoc update(DashboardDoc doc);

    ValidateExpressionResult validateExpression(String expressionString);

    ResourceGeneration downloadQuery(DashboardSearchRequest request);

    ResourceGeneration downloadSearchResults(DownloadSearchResultsRequest request);

    DashboardSearchResponse search(DashboardSearchRequest request);
}
