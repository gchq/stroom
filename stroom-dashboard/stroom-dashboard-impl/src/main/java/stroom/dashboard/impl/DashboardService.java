package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadQueryRequest;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.SearchBusPollRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.util.shared.ResourceGeneration;

import java.util.List;
import java.util.Set;

public interface DashboardService {
    DashboardDoc read(final DocRef docRef);

    DashboardDoc update(final DashboardDoc doc);

    ValidateExpressionResult validateExpression(final String expressionString);

    ResourceGeneration downloadQuery(final DownloadQueryRequest request);

    ResourceGeneration downloadSearchResults(final DownloadSearchResultsRequest request);

    Set<DashboardSearchResponse> poll(final SearchBusPollRequest request);

    List<String> fetchTimeZones();

    List<FunctionSignature> fetchFunctions();

}
