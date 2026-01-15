/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.impl;

import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryColumnValuesRequest;
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

    ColumnValues getColumnValues(QueryColumnValuesRequest request);

    List<String> fetchTimeZones();

    Optional<DocRef> fetchDefaultExtractionPipeline(DocRef dataSourceRef);

    Optional<DocRef> getReferencedDataSource(String query);

    ContextualQueryHelp getQueryHelpContext(String query, int row, int col);

    ResultPage<QueryField> findFields(FindFieldCriteria criteria);

    int getFieldCount(DocRef dataSourceRef);

    Optional<String> fetchDocumentation(DocRef dataSourceRef);

    String getBestNode(String nodeName, QuerySearchRequest request);
}
