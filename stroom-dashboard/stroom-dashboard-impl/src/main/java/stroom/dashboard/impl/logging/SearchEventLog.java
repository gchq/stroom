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

package stroom.dashboard.impl.logging;

import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.SearchRequest;
import stroom.query.shared.DownloadQueryResultsRequest;

import java.util.List;

public interface SearchEventLog {

    void search(QueryKey queryKey,
                String queryComponentId,
                String type,
                String rawQuery,
                DocRef dataSourceRef,
                ExpressionOperator expression,
                String queryInfo,
                List<Param> params,
                List<Result> results,
                Exception ex);

    default void downloadResults(final DownloadSearchResultsRequest downloadSearchResultsRequest,
                                 final Long resultCount) {
        downloadResults(downloadSearchResultsRequest,
                resultCount,
                null);
    }

    void downloadResults(DownloadSearchResultsRequest downloadSearchResultsRequest,
                         Long resultCount,
                         Exception ex);

    default void downloadResults(final DownloadQueryResultsRequest downloadSearchResultsRequest,
                                 final SearchRequest request,
                                 final Long resultCount) {
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
