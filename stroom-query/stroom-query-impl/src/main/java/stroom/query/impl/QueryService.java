package stroom.query.impl;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface QueryService {

    QueryDoc read(DocRef docRef);

    QueryDoc update(QueryDoc doc);

    ValidateExpressionResult validateQuery(String expressionString);

    ResourceGeneration downloadSearchResults(DownloadQueryResultsRequest request);

    DashboardSearchResponse search(QuerySearchRequest request);

    List<String> fetchTimeZones();

    DocRef fetchDefaultExtractionPipeline(DocRef dataSourceRef);

    Optional<DocRef> getReferencedDataSource(String query);

    ResultPage<QueryField> findFields(FindFieldInfoCriteria criteria);

    Optional<String> fetchDocumentation(DocRef dataSourceRef);
}
