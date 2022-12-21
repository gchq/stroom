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

package stroom.query.shared;

import stroom.query.api.v2.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QuerySearchRequest {

    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final String query;
    @JsonProperty
    private final QueryContext queryContext;

    @JsonPropertyDescription("If true the response will contain all results found so far, typically no results on the " +
            "first request. Future requests for the same query key may return more results. Intended for use on " +
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
    private final String applicationInstanceUuid;
    @JsonProperty
    private final String queryDocUuid;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final boolean storeHistory;

    @JsonCreator
    public QuerySearchRequest(
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("query") final String query,
            @JsonProperty("queryContext") final QueryContext queryContext,
            @JsonProperty("incremental") final boolean incremental,
            @JsonProperty("timeout") final long timeout,
            @JsonProperty("applicationInstanceUuid") final String applicationInstanceUuid,
            @JsonProperty("queryDocUuid") final String queryDocUuid,
            @JsonProperty("componentId") final String componentId,
            @JsonProperty("storeHistory") final boolean storeHistory) {
        this.queryKey = queryKey;
        this.query = query;
        this.queryContext = queryContext;
        this.incremental = incremental;
        this.timeout = timeout;
        this.applicationInstanceUuid = applicationInstanceUuid;
        this.queryDocUuid = queryDocUuid;
        this.componentId = componentId;
        this.storeHistory = storeHistory;
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

    public String getApplicationInstanceUuid() {
        return applicationInstanceUuid;
    }

    public String getQueryDocUuid() {
        return queryDocUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    public boolean isStoreHistory() {
        return storeHistory;
    }

    @Override
    public String toString() {
        return "QuerySearchRequest{" +
                "queryKey=" + queryKey +
                ", query='" + query + '\'' +
                ", queryContext=" + queryContext +
                ", incremental=" + incremental +
                ", timeout=" + timeout +
                ", applicationInstanceUuid='" + applicationInstanceUuid + '\'' +
                ", queryDocUuid='" + queryDocUuid + '\'' +
                ", componentId='" + componentId + '\'' +
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

        private QueryKey queryKey;
        private String query;
        private QueryContext queryContext;
        private boolean incremental = true;
        private long timeout = 1000L;
        private String applicationInstanceUuid;
        private String queryDocUuid;
        private String componentId;
        private boolean storeHistory;

        private Builder() {
        }

        private Builder(final QuerySearchRequest request) {
            this.queryKey = request.queryKey;
            this.query = request.query;
            this.queryContext = request.queryContext;
            this.incremental = request.incremental;
            this.timeout = request.timeout;
            this.applicationInstanceUuid = request.applicationInstanceUuid;
            this.queryDocUuid = request.queryDocUuid;
            this.storeHistory = request.storeHistory;
            this.componentId = request.componentId;
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

        public Builder applicationInstanceUuid(final String applicationInstanceUuid) {
            this.applicationInstanceUuid = applicationInstanceUuid;
            return this;
        }

        public Builder queryDocUuid(final String queryDocUuid) {
            this.queryDocUuid = queryDocUuid;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public Builder storeHistory(final boolean storeHistory) {
            this.storeHistory = storeHistory;
            return this;
        }

        public QuerySearchRequest build() {
            return new QuerySearchRequest(
                    queryKey,
                    query,
                    queryContext,
                    incremental,
                    timeout,
                    applicationInstanceUuid,
                    queryDocUuid,
                    componentId,
                    storeHistory);
        }
    }
}
