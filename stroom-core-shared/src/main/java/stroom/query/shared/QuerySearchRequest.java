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

package stroom.query.shared;

import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QuerySearchRequest {

    @JsonProperty
    private final SearchRequestSource searchRequestSource;
    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final String query;
    @JsonProperty
    private final QueryContext queryContext;

    @JsonPropertyDescription("If true the response will contain all results found so far, typically no results on " +
            "the first request. Future requests for the same query key may return more results. Intended for use on " +
            "longer running searches to allow partial result sets to be returned as soon as they are " +
            "available rather than waiting for the full result set.")
    @JsonProperty
    private final boolean incremental;

    @JsonPropertyDescription("Set the maximum time (in ms) for the server to wait for a complete result set. The " +
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
    @JsonProperty
    private final OffsetRange requestedRange;
    /**
     * @deprecated Use {@link GroupSelection#openGroups} instead.
     */
    @JsonProperty
    @Deprecated
    private final Set<String> openGroups;
    @JsonProperty
    private final GroupSelection groupSelection;
    @JsonProperty
    private final QueryTablePreferences queryTablePreferences;

    @JsonCreator
    public QuerySearchRequest(
            @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("query") final String query,
            @JsonProperty("queryContext") final QueryContext queryContext,
            @JsonProperty("incremental") final boolean incremental,
            @JsonProperty("timeout") final long timeout,
            @JsonProperty("storeHistory") final boolean storeHistory,
            @JsonProperty("requestedRange") final OffsetRange requestedRange,
            @JsonProperty("openGroups") final Set<String> openGroups,
            @JsonProperty("groupSelection") final GroupSelection groupSelection,
            @JsonProperty("queryTablePreferences") final QueryTablePreferences queryTablePreferences) {
        this.searchRequestSource = searchRequestSource;
        this.queryKey = queryKey;
        this.query = query;
        this.queryContext = queryContext;
        this.incremental = incremental;
        this.timeout = timeout;
        this.storeHistory = storeHistory;
        this.requestedRange = requestedRange;
        this.openGroups = openGroups;
        this.groupSelection = groupSelection == null ?
                GroupSelection.builder().openGroups(openGroups).build() : groupSelection;
        this.queryTablePreferences = queryTablePreferences;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public String getQuery() {
        return query;
    }

    public QueryContext getQueryContext() {
        return queryContext;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean isStoreHistory() {
        return storeHistory;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public GroupSelection getGroupSelection() {
        return groupSelection;
    }

    public QueryTablePreferences getQueryTablePreferences() {
        return queryTablePreferences;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QuerySearchRequest that = (QuerySearchRequest) o;
        return incremental == that.incremental &&
                timeout == that.timeout &&
                storeHistory == that.storeHistory &&
                Objects.equals(searchRequestSource, that.searchRequestSource) &&
                Objects.equals(queryKey, that.queryKey) &&
                Objects.equals(query, that.query) &&
                Objects.equals(queryContext, that.queryContext) &&
                Objects.equals(requestedRange, that.requestedRange) &&
                Objects.equals(openGroups, that.openGroups) &&
                Objects.equals(groupSelection, that.groupSelection) &&
                Objects.equals(queryTablePreferences, that.queryTablePreferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                searchRequestSource,
                queryKey,
                query,
                queryContext,
                incremental,
                timeout,
                storeHistory,
                requestedRange,
                openGroups,
                groupSelection,
                queryTablePreferences);
    }

    @Override
    public String toString() {
        return "QuerySearchRequest{" +
                "searchRequestSource=" + searchRequestSource +
                ", queryKey=" + queryKey +
                ", query='" + query + '\'' +
                ", queryContext=" + queryContext +
                ", incremental=" + incremental +
                ", timeout=" + timeout +
                ", storeHistory=" + storeHistory +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
                ", groupSelection=" + groupSelection +
                ", queryTablePreferences=" + queryTablePreferences +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private SearchRequestSource searchRequestSource;
        private QueryKey queryKey;
        private String query;
        private QueryContext queryContext;
        private boolean incremental = true;
        private long timeout = 1000L;
        private boolean storeHistory;
        private OffsetRange requestedRange = OffsetRange.ZERO_100;
        private Set<String> openGroups;
        private GroupSelection groupSelection;
        private QueryTablePreferences queryTablePreferences;

        private Builder() {
        }

        private Builder(final QuerySearchRequest request) {
            this.searchRequestSource = request.searchRequestSource;
            this.queryKey = request.queryKey;
            this.query = request.query;
            this.queryContext = request.queryContext;
            this.incremental = request.incremental;
            this.timeout = request.timeout;
            this.storeHistory = request.storeHistory;
            this.groupSelection = request.groupSelection;
            this.queryTablePreferences = request.queryTablePreferences;
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

        public Builder query(final String query) {
            this.query = query;
            return this;
        }

        public Builder queryContext(final QueryContext queryContext) {
            this.queryContext = queryContext;
            return this;
        }

        public Builder incremental(final boolean incremental) {
            this.incremental = incremental;
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

        public Builder requestedRange(final OffsetRange requestedRange) {
            this.requestedRange = requestedRange;
            return this;
        }

        public Builder openGroups(final Set<String> openGroups) {
            this.openGroups = openGroups;
            return this;
        }

        public Builder groupSelection(final GroupSelection groupSelection) {
            this.groupSelection = groupSelection;
            return this;
        }

        public Builder queryTablePreferences(final QueryTablePreferences queryTablePreferences) {
            this.queryTablePreferences = queryTablePreferences;
            return this;
        }

        public QuerySearchRequest build() {
            return new QuerySearchRequest(
                    searchRequestSource,
                    queryKey,
                    query,
                    queryContext,
                    incremental,
                    timeout,
                    storeHistory,
                    requestedRange,
                    openGroups,
                    groupSelection,
                    queryTablePreferences);
        }
    }
}
