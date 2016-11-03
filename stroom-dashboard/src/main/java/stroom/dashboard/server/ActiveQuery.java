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

package stroom.dashboard.server;

import java.util.HashMap;
import java.util.Map;

import stroom.query.SearchResultCollector;
import stroom.util.shared.SharedObject;

public class ActiveQuery {
    private final Map<String, ComponentResultCreator> componentResultCreatorMap = new HashMap<String, ComponentResultCreator>();
    private final Map<String, SharedObject> lastResults = new HashMap<String, SharedObject>();
    private final SearchResultCollector searchResultCollector;

    public ActiveQuery(final SearchResultCollector searchResultCollector) {
        this.searchResultCollector = searchResultCollector;
    }

    public SearchResultCollector getSearchResultCollector() {
        return searchResultCollector;
    }

    public Map<String, ComponentResultCreator> getComponentResultCreatorMap() {
        return componentResultCreatorMap;
    }

    public Map<String, SharedObject> getLastResults() {
        return lastResults;
    }
}
