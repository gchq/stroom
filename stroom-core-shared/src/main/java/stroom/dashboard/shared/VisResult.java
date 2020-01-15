/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonProperty;

public class VisResult implements ComponentResult {
    private static final long serialVersionUID = 3826654996795750099L;

    private String jsonData;
    private long dataPoints;
    private String error;

    public VisResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public VisResult(final String jsonData, final long dataPoints, final String error) {
        this.jsonData = jsonData;
        this.dataPoints = dataPoints;
        this.error = error;
    }

    public String getJsonData() {
        return jsonData;
    }

    public String getError() {
        return error;
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
