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

@JsonPropertyOrder({"componentId", "jsonData", "dataPoints", "errors", "errorMessages"})
@JsonInclude(Include.NON_NULL)
public final class VisResult extends Result {

    @JsonProperty
    private final String jsonData;
    @JsonProperty
    private final long dataPoints;

    @JsonCreator
    public VisResult(@JsonProperty("componentId") final String componentId,
                     @JsonProperty("jsonData") final String jsonData,
                     @JsonProperty("dataPoints") final long dataPoints,
                     @JsonProperty("errors") final List<String> errors,
                     @JsonProperty("errorMessages") final List<ErrorMessage> errorMessages) {
        super(componentId, errors, errorMessages);
        this.jsonData = jsonData;
        this.dataPoints = dataPoints;
    }

    public String getJsonData() {
        return jsonData;
    }

    @Override
    public String toString() {
        return dataPoints + " data points";
    }

    public static class Store {

        public Object key;
        public Object[] values;
        public Double[] min;
        public Double[] max;
        public Double[] sum;
        public String[] types;
        public String[] sortDirections;
        public String keyType;
        public String keySortDirection;

        @JsonProperty("key")
        public Object getKey() {
            return key;
        }

        @JsonProperty("keyType")
        public String getKeyType() {
            return keyType;
        }

        @JsonProperty("keySortDirection")
        public String getKeySortDirection() {
            return keySortDirection;
        }

        @JsonProperty("values")
        public Object[] getValues() {
            return values;
        }

        @JsonProperty("types")
        public String[] getTypes() {
            return types;
        }

        @JsonProperty("sortDirections")
        public String[] getSortDirections() {
            return sortDirections;
        }

        @JsonProperty("min")
        public Double[] getMin() {
            return min;
        }

        @JsonProperty("max")
        public Double[] getMax() {
            return max;
        }

        @JsonProperty("sum")
        public Double[] getSum() {
            return sum;
        }
    }
}
