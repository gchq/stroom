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

package stroom.dashboard.shared;

public class DownloadQueryRequest {
    private DashboardQueryKey dashboardQueryKey;
    private SearchRequest searchRequest;

    public DownloadQueryRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public DownloadQueryRequest(final DashboardQueryKey dashboardQueryKey,
                                final SearchRequest searchRequest) {
        this.dashboardQueryKey = dashboardQueryKey;
        this.searchRequest = searchRequest;
    }

    public DashboardQueryKey getDashboardQueryKey() {
        return dashboardQueryKey;
    }

    public void setDashboardQueryKey(final DashboardQueryKey dashboardQueryKey) {
        this.dashboardQueryKey = dashboardQueryKey;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public void setSearchRequest(final SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
    }
}
