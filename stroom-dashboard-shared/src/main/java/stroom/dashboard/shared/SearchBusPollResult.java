/*
 * Copyright 2016 Crown Copyright
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

import stroom.query.api.QueryKey;
import stroom.util.shared.SharedObject;

import java.util.HashMap;
import java.util.Map;

public class SearchBusPollResult implements SharedObject {
    private static final long serialVersionUID = -2964122512841756795L;

    private Map<DashboardQueryKey, SearchResponse> searchResultMap = new HashMap<>();

    public SearchBusPollResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public SearchBusPollResult(final Map<DashboardQueryKey, SearchResponse> searchResultMap) {
        this.searchResultMap = searchResultMap;
    }

    public Map<DashboardQueryKey, SearchResponse> getSearchResultMap() {
        return searchResultMap;
    }
}
