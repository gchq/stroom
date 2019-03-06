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

import stroom.task.shared.Action;
import stroom.util.shared.ResourceGeneration;

public class DownloadQueryAction extends Action<ResourceGeneration> {

    private static final long serialVersionUID = 7570924837610187155L;

    private DashboardQueryKey dashboardQueryKey;
    private SearchRequest searchRequest;

    public DownloadQueryAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public DownloadQueryAction(final DashboardQueryKey dashboardQueryKey,
                               final SearchRequest searchRequest) {
        this.dashboardQueryKey = dashboardQueryKey;
        this.searchRequest = searchRequest;
    }

    public DashboardQueryKey getDashboardQueryKey() {
        return dashboardQueryKey;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    @Override
    public String getTaskName() {
        return "Download Query";
    }
}
