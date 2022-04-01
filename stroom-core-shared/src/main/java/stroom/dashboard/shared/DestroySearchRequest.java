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

import stroom.query.api.v2.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class DestroySearchRequest {

    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final String applicationInstanceUuid;
    @JsonProperty
    private final String dashboardUuid;
    @JsonProperty
    private final String componentId;

    @JsonCreator
    public DestroySearchRequest(
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("applicationInstanceUuid") final String applicationInstanceUuid,
            @JsonProperty("dashboardUuid") final String dashboardUuid,
            @JsonProperty("componentId") final String componentId) {
        this.queryKey = queryKey;
        this.applicationInstanceUuid = applicationInstanceUuid;
        this.dashboardUuid = dashboardUuid;
        this.componentId = componentId;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public String getApplicationInstanceUuid() {
        return applicationInstanceUuid;
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    @Override
    public String toString() {
        return "DestroySearchRequest{" +
                "queryKey=" + queryKey +
                ", applicationInstanceUuid='" + applicationInstanceUuid + '\'' +
                ", dashboardUuid='" + dashboardUuid + '\'' +
                ", componentId='" + componentId + '\'' +
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
        private String applicationInstanceUuid;
        private String dashboardUuid;
        private String componentId;

        private Builder() {
        }

        private Builder(final DestroySearchRequest searchRequest) {
            this.queryKey = searchRequest.queryKey;
            this.applicationInstanceUuid = searchRequest.applicationInstanceUuid;
            this.dashboardUuid = searchRequest.dashboardUuid;
            this.componentId = searchRequest.componentId;
        }

        public Builder queryKey(final QueryKey queryKey) {
            this.queryKey = queryKey;
            return this;
        }

        public Builder applicationInstanceUuid(final String applicationInstanceUuid) {
            this.applicationInstanceUuid = applicationInstanceUuid;
            return this;
        }

        public Builder dashboardUuid(final String dashboardUuid) {
            this.dashboardUuid = dashboardUuid;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public DestroySearchRequest build() {
            return new DestroySearchRequest(
                    queryKey,
                    applicationInstanceUuid,
                    dashboardUuid,
                    componentId);
        }
    }
}
