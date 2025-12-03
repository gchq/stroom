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

package stroom.search.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.ResultStore;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FederatedSearchTask {

    private final SearchRequestSource searchRequestSource;
    private final QueryKey key;
    private final String searchName;
    private final Query query;
    @JsonProperty
    private final List<CoprocessorSettings> settings;
    private final DateTimeSettings dateTimeSettings;

    private transient volatile ResultStore resultStore;

    public FederatedSearchTask(final SearchRequestSource searchRequestSource,
                               final QueryKey key,
                               final String searchName,
                               final Query query,
                               @JsonProperty("settings") final List<CoprocessorSettings> settings,
                               final DateTimeSettings dateTimeSettings) {
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.searchName = searchName;
        this.query = query;
        this.settings = settings;
        this.dateTimeSettings = dateTimeSettings;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getKey() {
        return key;
    }

    public String getSearchName() {
        return searchName;
    }

    public Query getQuery() {
        return query;
    }

    public List<CoprocessorSettings> getSettings() {
        return settings;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public ResultStore getResultStore() {
        return resultStore;
    }

    public void setResultStore(final ResultStore resultStore) {
        this.resultStore = resultStore;
    }
}
