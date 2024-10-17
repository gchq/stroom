/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.impl.logging;

import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.SearchRequest;
import stroom.query.shared.DownloadQueryResultsRequest;

import java.util.List;

public interface SearchEventLog {

    void search(String type,
                String rawQuery,
                DocRef dataSourceRef,
                ExpressionOperator expression,
                String queryInfo,
                List<Param> params,
                Exception ex);

    default void downloadResults(DownloadSearchResultsRequest downloadSearchResultsRequest,
                                 Long resultCount) {
        downloadResults(downloadSearchResultsRequest,
                resultCount,
                null);
    }

    void downloadResults(DownloadSearchResultsRequest downloadSearchResultsRequest,
                         Long resultCount,
                         Exception ex);

    default void downloadResults(DownloadQueryResultsRequest downloadSearchResultsRequest,
                                 SearchRequest request,
                                 Long resultCount) {
        downloadResults(downloadSearchResultsRequest,
                request,
                resultCount,
                null);
    }

    void downloadResults(DownloadQueryResultsRequest req,
                         SearchRequest request,
                         Long resultCount,
                         Exception ex);
}
