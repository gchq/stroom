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

import stroom.entity.shared.Action;
import stroom.query.shared.QueryKey;
import stroom.query.shared.SearchRequest;

import java.util.Map;

public class SearchBusPollAction extends Action<SearchBusPollResult> {
    private static final long serialVersionUID = -6668626615097471925L;

    private Map<QueryKey, SearchRequest> searchActionMap;

    public SearchBusPollAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public SearchBusPollAction(final Map<QueryKey, SearchRequest> searchActionMap) {
        this.searchActionMap = searchActionMap;
    }

    public Map<QueryKey, SearchRequest> getSearchActionMap() {
        return searchActionMap;
    }

    @Override
    public String getTaskName() {
        return "Search Bus Poll";
    }
}
