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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.TypeConverter;
import stroom.dashboard.shared.ComponentResult;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.SearchResponse;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.VisResult;
import stroom.query.api.Field;
import stroom.query.api.FlatResult;
import stroom.query.api.Result;
import stroom.util.shared.OffsetRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Component
public class SearchResponseMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchResponseMapper.class);

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
            copy.setHighlights(new HashSet<>(searchResponse.getHighlights()));
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

            final String json = writeValueAsString(mapRows(tableResult.getRows()));
            copy.setJsonData(json);
            if (tableResult.getResultRange() != null) {
                copy.setResultRange(new OffsetRange<>(tableResult.getResultRange().getOffset().intValue(), tableResult.getResultRange().getLength().intValue()));
            }
            copy.setError(tableResult.getError());
            copy.setTotalResults(tableResult.getTotalResults());

            return copy;
        } else if (result instanceof FlatResult) {
            final FlatResult visResult = (FlatResult) result;
            final VisResult copy = mapVisResult(visResult);

            return copy;
        }

        return null;
    }

    private List<Row> mapRows(final List<stroom.query.api.Row> rows) {
        final List<Row> copy = new ArrayList<>();
        if (rows != null) {
            for (final stroom.query.api.Row row : rows) {
                final Row item = new Row(row.getGroupKey(), row.getValues(), row.getDepth());
                copy.add(item);
            }
        }
        return copy;
    }

    private VisResult mapVisResult(final FlatResult visResult) {
        String json = null;
        String error = visResult.getError();

        if (error == null) {
            try {
                final List<Field> fields = visResult.getStructure();
                if (fields != null && visResult.getValues() != null) {
                    int valueOffset = 0;

                    final Map<Integer, List<String>> typeMap = new HashMap<>();
                    int maxDepth = 0;
                    for (final Field field : fields) {
                        // Ignore key and depth fields.
                        if (field.getName() != null && field.getName().startsWith(":")) {
                            valueOffset++;

                        } else {
                            String type = Type.GENERAL.name();
                            if (field.getFormat() != null && field.getFormat().getType() != null) {
                                type = field.getFormat().getType().name();
                            }
                            typeMap.computeIfAbsent(field.getGroup(), k -> new ArrayList<>()).add(type);

                            if (field.getGroup() != null) {
                                maxDepth = Math.max(maxDepth, field.getGroup());
                                valueOffset++;
                            }
                        }
                    }

                    maxDepth++;

                    // Create an array of types.
                    String[][] types = new String[maxDepth + 1][];
                    for (final Entry<Integer, List<String>> entry : typeMap.entrySet()) {
                        int group = maxDepth;
                        if (entry.getKey() != null) {
                            group = entry.getKey();
                        }

                        String[] row = new String[entry.getValue().size()];
                        row = entry.getValue().toArray(row);
                        types[group] = row;
                    }

                    final int valueCount = fields.size() - valueOffset;

                    final Map<Object, List<List<Object>>> map = new HashMap<>();
                    for (final List<Object> row : visResult.getValues()) {
                        map.computeIfAbsent(row.get(0), k -> new ArrayList<>()).add(row);
                    }

                    final Store store = getStore(null, map, types, valueCount, maxDepth, 0);
                    json = getMapper().writeValueAsString(store);
                }
            } catch (final Exception e) {
                error = e.getMessage();
            }
        }

        return new VisResult(json, visResult.getSize(), error);
    }

    private Store getStore(final Object key, final Map<Object, List<List<Object>>> map, final String[][] types, final int valueCount, final int maxDepth, final int depth) {
        Store store = null;

        final List<List<Object>> rows = map.get(key);
        if (rows != null) {
            final List<Object> values = new ArrayList<>();
            final Double[] min = new Double[valueCount];
            final Double[] max = new Double[valueCount];
            final Double[] sum = new Double[valueCount];

            for (final List<Object> row : rows) {
                if (depth < maxDepth) {
                    final Store childStore = getStore(row.get(1), map, types, valueCount, maxDepth, depth + 1);
                    if (childStore != null) {
                        values.add(childStore);

                        for (int i = 0; i < valueCount; i++) {
                            min[i] = min(min[i], childStore.min[i]);
                            max[i] = max(max[i], childStore.max[i]);
                            sum[i] = sum(sum[i], childStore.sum[i]);
                        }
                    }
                } else {
                    final List<Object> vals = row.subList(row.size() - valueCount, row.size());
                    values.add(vals);
                    for (int i = 0; i < valueCount; i++) {
                        final Double dbl = TypeConverter.getDouble(vals.get(i));
                        if (dbl != null) {
                            min[i] = min(min[i], dbl);
                            max[i] = max(max[i], dbl);
                            sum[i] = sum(sum[i], dbl);
                        }
                    }
                }
            }

            store = new Store();
            if (key != null && key instanceof List) {
                List list = (List) key;
                store.key = list.get(list.size() - 1);
            }
            store.keyType = types[depth][0];
            store.values = values.toArray(new Object[values.size()]);
            store.types = types[types.length - 1];
            store.min = min;
            store.max = max;
            store.sum = sum;
        }

        return store;
    }

    private Double min(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        } else if (m2 == null) {
            return m1;
        }
        return Math.min(m1, m2);
    }

    private Double max(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        } else if (m2 == null) {
            return m1;
        }
        return Math.max(m1, m2);
    }

    private Double sum(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        } else if (m2 == null) {
            return m1;
        }
        return m1 + m2;
    }

//    private Map<Object, Store> mapNodes(final Node[] nodes, final Field[][] structure, final int depth, final String[] types) {
//        if (nodes == null) {
//            return null;
//        }
//
//        String keyType = Type.GENERAL.name();
//        Field[] fields = structure[depth];
//        if (fields.length == 1) {
//            keyType = fields[0].getFormat().getType().name();
//        }
//
//        final Map<Object, Store> map = new HashMap<>();
//        for (final Node node : nodes) {
//
//            // Turn node key into basic single key value that visualisations expect.
//            Object keyValue = null;
//            if (node.getKey() != null) {
//                final Object[] keyValues = node.getKey()[depth];
//                if (keyValues != null && keyValues.length > 0) {
//                    if (keyValues.length == 1) {
//                        keyValue = keyValues[0];
//                    } else {
//                        final StringBuilder sb = new StringBuilder();
//                        for (final Object v : keyValues) {
//                            sb.append(v);
//                            sb.append("|");
//                        }
//                        sb.setLength(sb.length() - 1);
//                        keyValue = sb.toString();
//                    }
//                }
//            }
//
//            final Store store = new Store(keyValue, keyType, types);
//            store.map = mapNodes(node.getNodes(), structure, depth + 1, types);
//            store.values = mapValues(node.getValues(), structure, de);
//            store.min = node.getMin();
//            store.max = node.getMax();
//            store.sum = node.getSum();
//
//            map.put(keyValue, store);
//        }
//
//        return map;
//    }
//
//    private Object mapNode(final Map<String, Object> node, String keyType, final Field[] structure, final int depth, final String[] types) {
//        Object key = node.get("key");
//        Object values = node.get("values");
//        Object min = node.get("min");
//        Object max = node.get("max");
//        Object sum = node.get("sum");
//
//        // Turn node key into basic single key value that visualisations expect.
//        Object keyValue = null;
//        if (key != null) {
//            final Object[] keyValues = (Object[])((Object[]) key)[depth];
//            if (keyValues != null && keyValues.length > 0) {
//                if (keyValues.length == 1) {
//                    keyValue = keyValues[0];
//                } else {
//                    final StringBuilder sb = new StringBuilder();
//                    for (final Object v : keyValues) {
//                        sb.append(v);
//                        sb.append("|");
//                    }
//                    sb.setLength(sb.length() - 1);
//                    keyValue = sb.toString();
//                }
//            }
//        }
//
//        final Store store = new Store(keyValue, keyType, types);
//        store.values = mapValues(node.getValues(), structure, depth + 1, types);
//        store.min = node.getMin();
//        store.max = node.getMax();
//        store.sum = node.getSum();
//
//        return store;
//    }
//
//    private Object[] mapValues(final Object[][] values, final Field[] structure, final int depth, final String[] types) {
//        if (values == null) {
//            return null;
//        }
//
//        String keyType = Type.GENERAL.name();
//        Field[] fields = structure[depth];
//        if (fields.length == 1) {
//            keyType = fields[0].getFormat().getType().name();
//        }
//
//        final Object[] mapped = new Object[values.length];
//        for (int i = 0; i < values.length; i++) {
//            final Object value = values[i];
//            if (value instanceof Node) {
//                mapped[i] = mapNode((Node) value, keyType, structure, depth, types);
//            } else {
//                mapped[i] = value;
//            }
//        }
//
//        return mapped;
//    }

    private String writeValueAsString(final Object object) {
        String json = null;

        if (object != null) {
            try {
                json = getMapper().writeValueAsString(object);
            } catch (final JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return json;
    }

    private ObjectMapper getMapper() {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(Double.class, new MyDoubleSerialiser());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

    public static class Store {
        private Object key;
        private Object[] values;
        private Double[] min;
        private Double[] max;
        private Double[] sum;
        private String[] types;
        private String keyType;

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
