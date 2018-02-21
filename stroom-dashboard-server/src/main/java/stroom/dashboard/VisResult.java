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

package stroom.dashboard.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VisResult implements ComponentResult {
    private static final long serialVersionUID = 3826654996795750099L;

    private Store store;
    private long dataPoints;
    private String error;

    public VisResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public VisResult(final Store store, final long dataPoints, final String error) {
        this.store = store;
        this.dataPoints = dataPoints;
        this.error = error;
    }

    public Store getStore() {
        return store;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return dataPoints + " data points";
    }

    public static class Store {
        Object key;
        Object[] values;
        Double[] min;
        Double[] max;
        Double[] sum;
        String[] types;
        String keyType;

        @JsonProperty("key")
        public Object getKey() {
            return key;
        }

        @JsonProperty("keyType")
        public String getKeyType() {
            return keyType;
        }

        @JsonProperty("values")
        public Object[] getValues() {
            return values;
        }

        @JsonProperty("types")
        public String[] getTypes() {
            return types;
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
