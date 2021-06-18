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

import stroom.query.api.v2.DateTimeSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DashboardSearchRequest {

    @JsonProperty
    private final DashboardQueryKey dashboardQueryKey;
    @JsonProperty
    private final Search search;
    @JsonProperty
    private final List<ComponentResultRequest> componentResultRequests;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @JsonCreator
    public DashboardSearchRequest(
            @JsonProperty("dashboardQueryKey") final DashboardQueryKey dashboardQueryKey,
            @JsonProperty("search") final Search search,
            @JsonProperty("componentResultRequests") final List<ComponentResultRequest> componentResultRequests,
            @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings) {

        this.dashboardQueryKey = dashboardQueryKey;
        this.search = search;
        this.componentResultRequests = componentResultRequests;
        this.dateTimeSettings = dateTimeSettings;
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

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DashboardSearchRequest that = (DashboardSearchRequest) o;
        return Objects.equals(dashboardQueryKey, that.dashboardQueryKey) &&
                Objects.equals(search, that.search) &&
                Objects.equals(componentResultRequests, that.componentResultRequests) &&
                Objects.equals(dateTimeSettings, that.dateTimeSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dashboardQueryKey, search, componentResultRequests, dateTimeSettings);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "dashboardQueryKey=" + dashboardQueryKey +
                ", search=" + search +
                ", componentResultRequests=" + componentResultRequests +
                ", dateTimeSettings='" + dateTimeSettings + '\'' +
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
        private DateTimeSettings dateTimeSettings;

        private Builder() {
        }

        private Builder(final DashboardSearchRequest searchRequest) {
            this.dashboardQueryKey = searchRequest.dashboardQueryKey;
            this.search = searchRequest.search;
            this.componentResultRequests = searchRequest.componentResultRequests;
            this.dateTimeSettings = searchRequest.dateTimeSettings;
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

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public DashboardSearchRequest build() {
            return new DashboardSearchRequest(dashboardQueryKey, search, componentResultRequests, dateTimeSettings);
        }
    }
}
