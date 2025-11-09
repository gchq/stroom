package stroom.dashboard.impl;

import stroom.dashboard.shared.AskStroomAiRequest;
import stroom.dashboard.shared.AskStroomAiResponse;
import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.ColumnValuesRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.util.shared.ResourceGeneration;

public interface DashboardService {

    DashboardDoc read(DocRef docRef);

    DashboardDoc update(DashboardDoc doc);

    ValidateExpressionResult validateExpression(String expressionString);

    ResourceGeneration downloadQuery(DashboardSearchRequest request);

    ResourceGeneration downloadSearchResults(DownloadSearchResultsRequest request);

    AskStroomAiResponse askStroomAi(AskStroomAiRequest request);

    DashboardSearchResponse search(DashboardSearchRequest request);

//    Boolean destroy(DestroySearchRequest request);

    ColumnValues getColumnValues(ColumnValuesRequest request);

    String getBestNode(String nodeName, DashboardSearchRequest request);
}
