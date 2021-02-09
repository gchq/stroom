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

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SearchRequest {

    @JsonProperty
    private final DashboardQueryKey dashboardQueryKey;
    @JsonProperty
    private final Search search;
    @JsonProperty
    private final List<ComponentResultRequest> componentResultRequests;
    @JsonProperty
    private final String dateTimeLocale;

    @JsonCreator
    public SearchRequest(@JsonProperty("dashboardQueryKey") final DashboardQueryKey dashboardQueryKey,
                         @JsonProperty("search") final Search search,
                         @JsonProperty("componentResultRequests") final
                         List<ComponentResultRequest> componentResultRequests,
                         @JsonProperty("dateTimeLocale") final String dateTimeLocale) {
        this.dashboardQueryKey = dashboardQueryKey;
        this.search = search;
        this.componentResultRequests = componentResultRequests;
        this.dateTimeLocale = dateTimeLocale;
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

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SearchRequest that = (SearchRequest) o;
        return Objects.equals(dashboardQueryKey, that.dashboardQueryKey) &&
                Objects.equals(search, that.search) &&
                Objects.equals(componentResultRequests, that.componentResultRequests) &&
                Objects.equals(dateTimeLocale, that.dateTimeLocale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dashboardQueryKey, search, componentResultRequests, dateTimeLocale);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "dashboardQueryKey=" + dashboardQueryKey +
                ", search=" + search +
                ", componentResultRequests=" + componentResultRequests +
                ", dateTimeLocale='" + dateTimeLocale + '\'' +
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
        private String dateTimeLocale;

        private Builder() {
        }

        private Builder(final SearchRequest searchRequest) {
            this.dashboardQueryKey = searchRequest.dashboardQueryKey;
            this.search = searchRequest.search;
            this.componentResultRequests = searchRequest.componentResultRequests;
            this.dateTimeLocale = searchRequest.dateTimeLocale;
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

        public SearchRequest build() {
            return new SearchRequest(dashboardQueryKey, search, componentResultRequests, dateTimeLocale);
        }
    }
}
