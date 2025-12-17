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

package stroom.dashboard.impl;

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

    DashboardSearchResponse search(DashboardSearchRequest request);

//    Boolean destroy(DestroySearchRequest request);

    ColumnValues getColumnValues(ColumnValuesRequest request);

    String getBestNode(String nodeName, DashboardSearchRequest request);
}
