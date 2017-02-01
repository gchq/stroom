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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;
import stroom.dashboard.shared.ComponentResult;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.SearchResponse;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.VisResult;
import stroom.query.api.Node;
import stroom.query.api.Result;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class SearchResponseMapper {
    public SearchResponse mapResponse(final stroom.query.api.SearchResponse searchResponse) {
        if (searchResponse == null) {
            return null;
        }

        final SearchResponse copy = new SearchResponse();

        if (searchResponse.getResults() != null) {
            for (final Result result : searchResponse.getResults()) {
                copy.addResult(result.getComponentId(), mapResult(result));
            }
        }

        if (searchResponse.getHighlights() != null) {
            List<String> list = Arrays.asList(searchResponse.getHighlights());
            copy.setHighlights(new HashSet<>(list));
        }

        if (searchResponse.getErrors() != null) {
            final StringBuilder sb = new StringBuilder();
            for (final String error : searchResponse.getErrors()) {
                sb.append(error);
                sb.append("\n");
            }
            sb.setLength(sb.length() - 1);
            copy.setErrors(sb.toString());
        }

        copy.setComplete(searchResponse.complete());

        return copy;
    }

    private ComponentResult mapResult(final Result result) {
        if (result == null) {
            return null;
        }

        if (result instanceof stroom.query.api.TableResult) {
            final stroom.query.api.TableResult tableResult = (stroom.query.api.TableResult) result;
            final TableResult copy = new TableResult();

            copy.setRows(mapRows(tableResult.getRows()));
            copy.setResultRange(new OffsetRange<>(tableResult.getResultRange().getOffset().intValue(), tableResult.getResultRange().getLength().intValue()));
            copy.setError(tableResult.getError());
            copy.setTotalResults(tableResult.getTotalResults());

            return copy;
        } else if (result instanceof stroom.query.api.VisResult) {
            final stroom.query.api.VisResult visResult = (stroom.query.api.VisResult) result;
            final VisResult copy = mapVisResult(visResult);

            return copy;
        }

        return null;
    }

    private List<Row> mapRows(final stroom.query.api.Row[] rows) {
        final List<Row> copy = new ArrayList<>();
        if (rows != null) {
            for (final stroom.query.api.Row row : rows) {
                SharedString[] values = null;

                if (row.getValues() != null) {
                    values = new SharedString[row.getValues().length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = SharedString.wrap(row.getValues()[i]);
                    }
                }

                final Row item = new Row(row.getGroupKey(), values, row.getDepth());
                copy.add(item);
            }
        }
        return copy;
    }

    private VisResult mapVisResult(final stroom.query.api.VisResult visResult) {
        String json = null;
        String error = visResult.getError();

        if (error == null) {
            try {
                final Store store = new Store(null, null, visResult.getTypes());
                store.map = mapNodes(visResult.getNodes(), visResult.getTypes());
                store.list = mapValues(visResult.getValues());
                store.min = visResult.getMin();
                store.max = visResult.getMax();
                store.sum = visResult.getSum();

                final SimpleModule module = new SimpleModule();
                module.addSerializer(Double.class, new MyDoubleSerialiser());

                final ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(module);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
                mapper.setSerializationInclusion(Include.NON_NULL);

                json = mapper.writeValueAsString(store);

            } catch (final Exception e) {
                error = e.getMessage();
            }
        }

        return new VisResult(json, visResult.getSize(), error);
    }

    private Map<Object, Store> mapNodes(Node[] nodes, String[] types) {
        if (nodes == null) {
            return null;
        }

        final Map<Object, Store> map = new HashMap<>();
        for (final Node node : nodes) {
            final Store store = new Store(node.getKey().getValue(), node.getKey().getType(), types);
            store.map = mapNodes(node.getNodes(), types);
            store.list = mapValues(node.getValues());
            store.min = node.getMin();
            store.max = node.getMax();
            store.sum = node.getSum();

            map.put(node.getKey().getValue(), store);
        }


        return map;
    }

    private List<Object> mapValues(Object[][] values) {
        if (values == null) {
            return null;
        }

        final List<Object> list = new ArrayList<>();
        for (final Object[] vals : values) {
            list.add(vals);
        }

        return list;
    }

    public static class Store {
        private final Object key;

        private Map<Object, Store> map;
        private List<Object> list;

        private Double[] min;
        private Double[] max;
        private Double[] sum;
        private final String[] types;
        private String keyType;

        public Store(final Object key, final String keyType, final String[] types) {
            this.key = key;
            this.keyType = keyType;
            this.types = types;
        }

        public Store(final Object key, final List<Object> list, final String[] types, final int len) {
            this.key = key;
            this.list = list;
            this.types = types;
            this.min = new Double[len];
            this.max = new Double[len];
            this.sum = new Double[len];
        }

        @JsonProperty("key")
        public Object getKey() {
            return key;
        }

        @JsonProperty("keyType")
        public String getKeyType() {
            return keyType;
        }

        @JsonProperty("values")
        public Object getValues() {
            if (map != null) {
                return map.values();
            } else if (list != null) {
                return list;
            }

            return null;
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

        @JsonIgnore
        public Map<Object, Store> getMap() {
            return map;
        }

        public void setMap(final Map<Object, Store> map) {
            this.map = map;
        }

        @JsonIgnore
        public List<Object> getList() {
            return list;
        }

        public void add(final Object[] vals) {
            list.add(vals);
        }

        @Override
        public String toString() {
            return "Store [key=" + key + ", map=" + map + ", types=" + Arrays.toString(types) + ", keyType=" + keyType
                    + "]";
        }

    }
}
