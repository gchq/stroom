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

import java.io.Serializable;
import java.util.Map;

@JsonInclude(Include.NON_DEFAULT)
public class SearchRequest implements Serializable {
    private static final long serialVersionUID = -6668626615097471925L;

    @JsonProperty
    private final DashboardQueryKey dashboardQueryKey;
    @JsonProperty
    private final Search search;
    @JsonProperty
    private final Map<String, ComponentResultRequest> componentResultRequests;
    @JsonProperty
    private final String dateTimeLocale;

    @JsonCreator
    public SearchRequest(@JsonProperty("dashboardQueryKey") final DashboardQueryKey dashboardQueryKey,
                         @JsonProperty("search") final Search search,
                         @JsonProperty("componentResultRequests") final Map<String, ComponentResultRequest> componentResultRequests,
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

    public Map<String, ComponentResultRequest> getComponentResultRequests() {
        return componentResultRequests;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SearchRequest that = (SearchRequest) o;

        if (search != null ? !search.equals(that.search) : that.search != null) return false;
        if (componentResultRequests != null ? !componentResultRequests.equals(that.componentResultRequests) : that.componentResultRequests != null)
            return false;
        return dateTimeLocale != null ? dateTimeLocale.equals(that.dateTimeLocale) : that.dateTimeLocale == null;
    }

    @Override
    public int hashCode() {
        int result = search != null ? search.hashCode() : 0;
        result = 31 * result + (componentResultRequests != null ? componentResultRequests.hashCode() : 0);
        result = 31 * result + (dateTimeLocale != null ? dateTimeLocale.hashCode() : 0);
        return result;
    }
}
