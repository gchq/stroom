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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DestroyQueryRequest {

    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final String applicationInstanceUuid;
    @JsonProperty
    private final String queryDocUuid;
    @JsonProperty
    private final String componentId;

    @JsonCreator
    public DestroyQueryRequest(
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("applicationInstanceUuid") final String applicationInstanceUuid,
            @JsonProperty("queryDocUuid") final String queryDocUuid,
            @JsonProperty("componentId") final String componentId) {
        this.queryKey = queryKey;
        this.applicationInstanceUuid = applicationInstanceUuid;
        this.queryDocUuid = queryDocUuid;
        this.componentId = componentId;
    }

    public QueryKey getQueryKey() {
        return queryKey;
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

    @Override
    public String toString() {
        return "DestroySearchRequest{" +
                "queryKey=" + queryKey +
                ", applicationInstanceUuid='" + applicationInstanceUuid + '\'' +
                ", queryDocUuid='" + queryDocUuid + '\'' +
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
        private String queryDocUuid;
        private String componentId;

        private Builder() {
        }

        private Builder(final DestroyQueryRequest searchRequest) {
            this.queryKey = searchRequest.queryKey;
            this.applicationInstanceUuid = searchRequest.applicationInstanceUuid;
            this.queryDocUuid = searchRequest.queryDocUuid;
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

        public Builder queryDocUuid(final String queryDocUuid) {
            this.queryDocUuid = queryDocUuid;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public DestroyQueryRequest build() {
            return new DestroyQueryRequest(
                    queryKey,
                    applicationInstanceUuid,
                    queryDocUuid,
                    componentId);
        }
    }
}
