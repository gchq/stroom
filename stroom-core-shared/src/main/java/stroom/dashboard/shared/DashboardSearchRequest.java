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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DashboardSearchRequest {

    @JsonProperty
    private final DashboardQueryKey dashboardQueryKey;
    @JsonProperty
    private final Search search;
    @JsonProperty
    private final List<ComponentResultRequest> componentResultRequests;
    @JsonProperty
    private final String dateTimeLocale;

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

    @JsonCreator
    public DashboardSearchRequest(
            @JsonProperty("dashboardQueryKey") final DashboardQueryKey dashboardQueryKey,
            @JsonProperty("search") final Search search,
            @JsonProperty("componentResultRequests") final List<ComponentResultRequest> componentResultRequests,
            @JsonProperty("dateTimeLocale") final String dateTimeLocale,
            @JsonProperty("timeout") final long timeout) {
        this.dashboardQueryKey = dashboardQueryKey;
        this.search = search;
        this.componentResultRequests = componentResultRequests;
        this.dateTimeLocale = dateTimeLocale;
        this.timeout = timeout;
    }

    public DashboardQueryKey getDashboardQueryKey() {
        return dashboardQueryKey;
    }

    public Search getSearch() {
        return search;
    }

    public List<ComponentResultRequest> getComponentResultRequests() {
        return componentResultRequests;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "DashboardSearchRequest{" +
                "dashboardQueryKey=" + dashboardQueryKey +
                ", search=" + search +
                ", componentResultRequests=" + componentResultRequests +
                ", dateTimeLocale='" + dateTimeLocale + '\'' +
                ", timeout=" + timeout +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private DashboardQueryKey dashboardQueryKey;
        private Search search;
        private List<ComponentResultRequest> componentResultRequests;
        private String dateTimeLocale = "en-gb";
        private long timeout = 1000L;

        private Builder() {
        }

        private Builder(final DashboardSearchRequest searchRequest) {
            this.dashboardQueryKey = searchRequest.dashboardQueryKey;
            this.search = searchRequest.search;
            this.componentResultRequests = searchRequest.componentResultRequests;
            this.dateTimeLocale = searchRequest.dateTimeLocale;
            this.timeout = searchRequest.timeout;
        }

        public Builder dashboardQueryKey(final DashboardQueryKey dashboardQueryKey) {
            this.dashboardQueryKey = dashboardQueryKey;
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

        public Builder dateTimeLocale(final String dateTimeLocale) {
            this.dateTimeLocale = dateTimeLocale;
            return this;
        }

        public Builder timeout(final long timeout) {
            this.timeout = timeout;
            return this;
        }

        public DashboardSearchRequest build() {
            return new DashboardSearchRequest(
                    dashboardQueryKey,
                    search,
                    componentResultRequests,
                    dateTimeLocale,
                    timeout);
        }
    }
}
