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

package stroom.query.api;

import stroom.util.shared.ErrorMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "componentId",
        "visSettings",
        "jsonData",
        "dataPoints",
        "errors",
        "errorMessages"})
@JsonInclude(Include.NON_NULL)
public final class QLVisResult extends Result {

    @JsonProperty
    private final QLVisSettings visSettings;
    @JsonProperty
    private final String jsonData;
    @JsonProperty
    private final long dataPoints;

    @JsonCreator
    public QLVisResult(@JsonProperty("componentId") final String componentId,
                       @JsonProperty("visSettings") final QLVisSettings visSettings,
                       @JsonProperty("jsonData") final String jsonData,
                       @JsonProperty("dataPoints") final long dataPoints,
                       @JsonProperty("errors") final List<String> errors,
                       @JsonProperty("errorMessages") final List<ErrorMessage> errorMessages) {
        super(componentId, errors, errorMessages);
        this.visSettings = visSettings;
        this.jsonData = jsonData;
        this.dataPoints = dataPoints;
    }

    public QLVisSettings getVisSettings() {
        return visSettings;
    }

    public String getJsonData() {
        return jsonData;
    }

    @Override
    public String toString() {
        return dataPoints + " data points";
    }
}
