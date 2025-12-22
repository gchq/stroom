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

import stroom.query.api.ResultRequest.Fetch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableResultRequest.class, name = "table"),
        @JsonSubTypes.Type(value = VisResultRequest.class, name = "vis")
})
@JsonInclude(Include.NON_NULL)
public abstract sealed class ComponentResultRequest permits TableResultRequest, VisResultRequest {

    @Schema(description = "The ID of the component that will receive the results corresponding to this ResultRequest",
            required = true)
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final Fetch fetch;

    @JsonCreator
    public ComponentResultRequest(@JsonProperty("componentId") final String componentId,
                                  @JsonProperty("fetch") final Fetch fetch) {
        this.componentId = componentId;
        this.fetch = fetch;
    }

    public String getComponentId() {
        return componentId;
    }

    public Fetch getFetch() {
        return fetch;
    }

    @Override
    public String toString() {
        return "ComponentResultRequest{" +
               "componentId='" + componentId + '\'' +
               ", fetch=" + fetch +
               '}';
    }
}
