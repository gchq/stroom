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

import java.util.Map;

public class SearchBusPollRequest {
    private String applicationInstanceId;
    private Map<DashboardQueryKey, SearchRequest> searchActionMap;

    public SearchBusPollRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public SearchBusPollRequest(final String applicationInstanceId,
                                final Map<DashboardQueryKey, SearchRequest> searchActionMap) {
        this.applicationInstanceId = applicationInstanceId;
        this.searchActionMap = searchActionMap;
    }

    public String getApplicationInstanceId() {
        return applicationInstanceId;
    }

    public void setApplicationInstanceId(final String applicationInstanceId) {
        this.applicationInstanceId = applicationInstanceId;
    }

    public Map<DashboardQueryKey, SearchRequest> getSearchActionMap() {
        return searchActionMap;
    }

    public void setSearchActionMap(final Map<DashboardQueryKey, SearchRequest> searchActionMap) {
        this.searchActionMap = searchActionMap;
    }
}
