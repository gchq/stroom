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

package stroom.dashboard.shared;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DashboardSearchRequest {

    @JsonProperty
    private final SearchRequestSource searchRequestSource;
    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final Search search;
    @JsonProperty
    private final List<ComponentResultRequest> componentResultRequests;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @Schema(description = "Set the maximum time (in ms) for the server to wait for a complete result set. The " +
            "timeout applies to both incremental and non incremental queries, though the behaviour is slightly " +
            "different. The timeout will make the server wait for which ever comes first out of the query " +
            "completing or the timeout period being reached. If no value is supplied then for an " +
            "incremental query a default value of 0 will be used (i.e. returning immediately) and for a " +
            "non-incremental query the server's default timeout period will be used. For an incremental " +
            "query, if the query has not completed by the end of the timeout period, it will return " +
            "the currently know results with complete=false, however for a non-incremental query it will " +
            "return no results, complete=false and details of the timeout in the error field")
    @JsonProperty
    private final long timeout;
    @JsonProperty
    private final boolean storeHistory;

    @JsonCreator
    public DashboardSearchRequest(
            @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("search") final Search search,
            @JsonProperty("componentResultRequests") final List<ComponentResultRequest> componentResultRequests,
            @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings,
            @JsonProperty("timeout") final long timeout,
            @JsonProperty("storeHistory") final boolean storeHistory) {
        this.searchRequestSource = searchRequestSource;
        this.queryKey = queryKey;
        this.search = search;
        this.componentResultRequests = componentResultRequests;
        this.dateTimeSettings = dateTimeSettings;
        this.timeout = timeout;
        this.storeHistory = storeHistory;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public Search getSearch() {
        return search;
    }

    public List<ComponentResultRequest> getComponentResultRequests() {
        return componentResultRequests;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean isStoreHistory() {
        return storeHistory;
    }

    @Override
    public String toString() {
        return "DashboardSearchRequest{" +
                "queryKey=" + queryKey +
                ", search=" + search +
                ", componentResultRequests=" + componentResultRequests +
                ", dateTimeSettings='" + dateTimeSettings + '\'' +
                ", timeout=" + timeout +
                ", searchRequestSource='" + searchRequestSource + '\'' +
                ", storeHistory=" + storeHistory +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private SearchRequestSource searchRequestSource;
        private QueryKey queryKey;
        private Search search;
        private List<ComponentResultRequest> componentResultRequests;
        private DateTimeSettings dateTimeSettings;
        private long timeout = 1000L;
        private boolean storeHistory;

        private Builder() {
        }

        private Builder(final DashboardSearchRequest searchRequest) {
            this.searchRequestSource = searchRequest.searchRequestSource;
            this.queryKey = searchRequest.queryKey;
            this.search = searchRequest.search;
            this.componentResultRequests = searchRequest.componentResultRequests;
            this.dateTimeSettings = searchRequest.dateTimeSettings;
            this.timeout = searchRequest.timeout;
            this.storeHistory = searchRequest.storeHistory;
        }

        /**
         * Where did this search request originate, e.g. query, dashboard or API?
         *
         * @return The {@link SearchRequest.Builder}, enabling method chaining
         */
        public Builder searchRequestSource(final SearchRequestSource searchRequestSource) {
            this.searchRequestSource = searchRequestSource;
            return this;
        }

        public Builder queryKey(final QueryKey queryKey) {
            this.queryKey = queryKey;
            return this;
        }

        public Builder search(final Search search) {
            this.search = search;
            return this;
        }

        public Builder componentResultRequests(final List<ComponentResultRequest> componentResultRequests) {
            this.componentResultRequests = componentResultRequests;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public Builder timeout(final long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder storeHistory(final boolean storeHistory) {
            this.storeHistory = storeHistory;
            return this;
        }

        public DashboardSearchRequest build() {
            return new DashboardSearchRequest(
                    searchRequestSource,
                    queryKey,
                    search,
                    componentResultRequests,
                    dateTimeSettings,
                    timeout,
                    storeHistory);
        }
    }
}
