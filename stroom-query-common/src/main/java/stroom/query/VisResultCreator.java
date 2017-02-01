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

package stroom.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import stroom.dashboard.expression.Generator;
import stroom.dashboard.expression.ObjectCompareUtil;
import stroom.dashboard.expression.TypeConverter;
import stroom.query.api.Format.Type;
import stroom.query.api.Key;
import stroom.query.api.Node;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.VisLimit;
import stroom.query.api.VisResult;
import stroom.query.api.VisResultRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VisResultCreator implements ResultCreator {
    private final CompiledStructure.Structure structure;
    private String error;

    public VisResultCreator(final CompiledStructure.Structure structure) {
        this.structure = structure;
    }

    public static VisResultCreator create(final ResultRequest resultRequest) {
        CompiledStructure.Structure structure;
        try {
            final VisResultRequest visResultRequest = (VisResultRequest) resultRequest;
            final StructureBuilder structureBuilder = new StructureBuilder(visResultRequest.getStructure(), visResultRequest.getTableSettings().getFields());
            structure = structureBuilder.create();
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return new VisResultCreator(structure);
    }

    @Override
    public Result create(final Data data, final ResultRequest resultRequest) {
        if (error == null) {
            try {
                // Get top level items.
                final Items<Item> items = data.getChildMap().get(null);
                final Node node = create(items);

                long dataPoints = 0;
                if (items != null && items.size() > 0) {
                    dataPoints = items.size();
                }

                final CompiledStructure.Field[] fields = getFields(structure);
                final String[] types = getTypes(fields);

//                final SimpleModule module = new SimpleModule();
//                module.addSerializer(Double.class, new MyDoubleSerialiser());
//
//                final ObjectMapper mapper = new ObjectMapper();
//                mapper.registerModule(module);
//                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//                mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
//                mapper.setSerializationInclusion(Include.NON_NULL);
//
//                String json = null;
//                if (store != null) {
//                    json = mapper.writeValueAsString(store);
//                }
                return new VisResult(resultRequest.getComponentId(), types, node.getNodes(), node.getValues(), node.getMin(), node.getMax(), node.getSum(), dataPoints, error);

            } catch (final Exception e) {
                error = e.getMessage();
            }
        }

        return new VisResult(error);
    }

    /**
     * Public method for testing purposes.
     */
    public Node create(final Items<Item> items) throws JsonProcessingException {
        return create(items, structure);
    }

    /**
     * Public method for testing purposes.
     */
    public Node create(final Items<Item> items, final CompiledStructure.Structure structure) {
        if (structure.getNest() != null) {
            return create(items, structure.getNest());
        } else if (structure.getValues() != null) {
            return create(items, structure.getValues());
        }
        return null;
    }

    /**
     * Public method for testing purposes.
     */
    public Node create(final Items<Item> items, final CompiledStructure.Nest structure) {
        if (items != null && items.size() > 0) {
            final Store store = new Store();

            // Iterate over the items.
            for (final Item item : items) {
                addNest(structure, store, item);
            }

            // Apply sorting and trimming if required.
            return sort(null, store, structure);
        }

        return null;
    }

    /**
     * Public method for testing purposes.
     */
    public Node create(final Items<Item> items, final CompiledStructure.Values structure) {
        if (items != null && items.size() > 0) {
//            final CompiledStructure.Field[] fields = structure.getFields();
//            final String[] types = getTypes(fields);
//
//            final int len = fields.length;
            final Store store = new Store();

            // Iterate over the items.
            for (final Item item : items) {
                addValues(structure, store, item);
            }

            // Apply sorting and trimming if required.
            Object[][] values = sort(store, structure);

            return new Node(null, null, values, store.min, store.max, store.sum);
        }

        return null;
    }

    private CompiledStructure.Field[] getFields(final CompiledStructure.Nest structure) {
        if (structure.getNest() != null) {
            return getFields(structure.getNest());
        }
        if (structure.getValues() != null) {
            return structure.getValues().getFields();
        }
        return null;
    }

    private CompiledStructure.Field[] getFields(final CompiledStructure.Structure structure) {
        if (structure.getNest() != null) {
            return getFields(structure.getNest());
        }
        if (structure.getValues() != null) {
            return structure.getValues().getFields();
        }
        return null;
    }

    private String[] getTypes(final CompiledStructure.Field[] fields) {
        String[] types = null;
        if (fields != null) {
            types = new String[fields.length];
            for (int i = 0; i < types.length; i++) {
                final CompiledStructure.Field field = fields[i];
                if (field != null && field.getId() != null && field.getId().getType() != null) {
                    types[i] = field.getId().getType().name();
                }
            }
        }
        return types;
    }

    private String getKeyType(final CompiledStructure.Nest structure) {
        String keyType = null;
        if (structure != null && structure.getKey() != null && structure.getKey().getId() != null
                && structure.getKey().getId().getType() != null) {
            keyType = structure.getKey().getId().getType().name();
        }
        return keyType;
    }

    private Node sort(final Key key, final Store store, final CompiledStructure.Nest nest) {
        Node[] nodes = null;
        Object[][] values = null;

        // Sort children first.
        if (nest.getNest() != null) {
            if (store.map != null) {
                nodes = new Node[store.map.size()];
                int i = 0;
                for (final Map.Entry<Key, Store> entry : store.map.entrySet()) {
                    nodes[i++] = sort(entry.getKey(), entry.getValue(), nest.getNest());
                }
            }

            // Now sort this store.
            if (nodes != null) {
                if (nest.getKey() != null && nest.getKey().getSort() != null) {
                    // Sort.
                    Arrays.sort(nodes, new NodeComparator(nest.getKey().getSort().getDirection()));
                }

                // Trim if necessary.
                if (nest.getLimit() != null) {
                    if (nodes.length > nest.getLimit().getSize()) {
                        nodes = Arrays.copyOf(nodes, nest.getLimit().getSize());
                    }
                }
            }


//            else if (store.list != null) {
//                for (final Object subStore : store.list) {
//                    sort((VisData) subStore, nest.getNest());
//                }
//            }
        } else if (nest.getValues() != null) {
            if (store.map != null) {
                values = new Object[store.map.size()][];
                int i = 0;
                for (final Store subStore : store.map.values()) {
                    values[i++] = sort(subStore, nest.getValues());
                }
            }
//            else
//            if (store.list != null) {
//                for (final Object subStore : store.list) {
//                    sort((VisData) subStore, nest.getValues());
//                }
//            }
        }


        // Calculate min, max and sum list for all sub stores.
        Double[] min = null;
        Double[] max = null;
        Double[] sum = null;


        if (store.map != null && store.map.size() > 0) {

            for (final Store subStore : store.map.values()) {
                if (min == null) {
                    min = new Double[subStore.min.length];
                }
                for (int i = 0; i < subStore.min.length; i++) {
                    min[i] = min(min[i], subStore.min[i]);
                }

                if (max == null) {
                    max = new Double[subStore.max.length];
                }
                for (int i = 0; i < subStore.max.length; i++) {
                    max[i] = max(max[i], subStore.max[i]);
                }

                if (sum == null) {
                    sum = new Double[subStore.sum.length];
                }
                for (int i = 0; i < subStore.sum.length; i++) {
                    sum[i] = sum(sum[i], subStore.sum[i]);
                }
            }
            store.min = min;
            store.max = max;
            store.sum = sum;
        }

        return new Node(key, nodes, values, min, max, sum);
    }

    private Object[][] sort(final Store store, final CompiledStructure.Values values) {
        Object[][] arr = new Object[store.list.size()][];
        arr = store.list.toArray(arr);

        if (values.getFields() != null && values.getFields().length > 0 && arr.length > 0) {
            final List<CompiledStructure.Sort> sorts = new ArrayList<>();

            // Create a list of sort options.
            for (final CompiledStructure.Field field : values.getFields()) {
                if (field.getSort() != null) {
                    sorts.add(field.getSort());
                }
            }

            if (sorts.size() > 0) {
                // Sort the sort options by sort priority.
                sorts.sort(Comparator.comparingInt(CompiledStructure.Sort::getPriority));

                Arrays.sort(arr, new ValuesComparator(sorts));
            }
        }

        // Trim if necessary.
        if (values.getLimit() != null) {
            if (arr.length > values.getLimit().getSize()) {
                arr = Arrays.copyOf(arr, values.getLimit().getSize());
            }
        }

        return arr;
    }

//    private void trimList(final List<Object[]> list, final VisLimit limit) {
//        if (list != null && limit != null) {
//            final int l = limit.getSize();
//            while (list.size() > l) {
//                list.remove(list.size() - 1);
//            }
//        }
//    }

    private void addNest(final CompiledStructure.Nest structure, final Store parent, final Item item) {
        Key key = null;
        if (structure.getKey() != null && structure.getKey().getId() != null) {
            final String keyType = getKeyType(structure);
            final Object keyValue = getValue(item, structure.getKey().getId().getIndex(), structure.getKey().getId().getType());
            key = new Key(keyType, keyValue);
        }

        if (parent.map == null) {
            parent.map = new HashMap<>();
        }

        final Store child = parent.map.computeIfAbsent(key, k -> new Store());
//        if (store == null) {
//            if (structure.getNest() != null) {
//                store = new VisData();
//                parent.map.put(key, store);
//            } else if (structure.getValues() != null) {
//                store = new VisData();
//                parent.map.put(key, store);
//            }
//        }
//
//        if (store != null) {
        if (structure.getNest() != null) {
            final CompiledStructure.Nest subStructure = structure.getNest();
            addNest(subStructure, child, item);
        } else if (structure.getValues() != null) {
            final CompiledStructure.Values subStructure = structure.getValues();
            addValues(subStructure, child, item);
        }
//        }
    }

    private void addValues(final CompiledStructure.Values structure, final Store store, final Item item) {
        final Object[] objects = new Object[structure.getFields().length];
        for (int i = 0; i < structure.getFields().length; i++) {
            final CompiledStructure.Field field = structure.getFields()[i];
            if (field != null && field.getId() != null) {
                addValue(item, field.getId().getIndex(), field.getId().getType(), store.min, store.max, store.sum,
                        objects, i);
            }
        }

        if (store.list == null) {
            store.list = new ArrayList<>();
        }
        store.list.add(objects);
    }

    private Object getValue(final Item item, final int index, final Type type) {
        if (index >= 0 && item.getValues().length > index) {
            final Object o = item.getValues()[index];
            if (o != null) {
                // Convert all list into fully resolved objects evaluating
                // functions where necessary.
                Object val = o;
                if (o instanceof Generator) {
                    final Generator generator = (Generator) o;
                    val = generator.eval();
                }

                if (val != null) {
                    if (Type.NUMBER.equals(type) || Type.DATE_TIME.equals(type)) {
                        return TypeConverter.getDouble(val);
                    } else {
                        return val.toString();
                    }
                }
            }
        }

        return null;
    }

    private void addValue(final Item item, final int index, final Type type, final Double[] min, final Double[] max,
                          final Double[] sum, final Object[] arr, final int i) {
        if (index >= 0 && item.getValues().length > index) {
            final Object o = item.getValues()[index];
            if (o != null) {
                // Convert all list into fully resolved objects evaluating
                // functions where necessary.
                Object val = o;
                if (o instanceof Generator) {
                    final Generator generator = (Generator) o;
                    val = generator.eval();
                }

                if (val != null) {
                    if (Type.NUMBER.equals(type) || Type.DATE_TIME.equals(type)) {
                        final Double v = TypeConverter.getDouble(val);

                        if (v != null) {
                            min[i] = min(min[i], v);
                            max[i] = max(max[i], v);
                            sum[i] = sum(sum[i], v);
                            arr[i] = v;
                        }
                    } else {
                        arr[i] = val.toString();
                    }
                }
            }
        }
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

    private static class ObjectComparator implements Comparator<Object> {
        @Override
        public int compare(final Object o1, final Object o2) {
            return ObjectCompareUtil.compare(o1, o2);
        }
    }

//    private static class KeyComparator implements Comparator<Key> {
//        private final CompiledStructure.Direction direction;
//
//        KeyComparator(final CompiledStructure.Direction direction) {
//            this.direction = direction;
//        }
//
//        @Override
//        public int compare(final Key o1, final Key o2) {
//            if (CompiledStructure.Direction.ASCENDING.equals(direction)) {
//                return ObjectCompareUtil.compare(o1.getValue(), o2.getValue());
//            } else {
//                return ObjectCompareUtil.compare(o2.getValue(), o1.getValue());
//            }
//        }
//    }

    private static class NodeComparator implements Comparator<Node> {
        private final SortDirection direction;

        NodeComparator(final SortDirection direction) {
            this.direction = direction;
        }

        @Override
        public int compare(final Node o1, final Node o2) {
            if (SortDirection.ASCENDING.equals(direction)) {
                return ObjectCompareUtil.compare(o1.getKey().getValue(), o2.getKey().getValue());
            } else {
                return ObjectCompareUtil.compare(o2.getKey().getValue(), o1.getKey().getValue());
            }
        }
    }

    private static class Store {
        Map<Key, Store> map;
        List<Object[]> list;
        Double[] min;
        Double[] max;
        Double[] sum;
    }

    private static class ValuesComparator extends ObjectComparator {
        private final List<CompiledStructure.Sort> sorts;

        ValuesComparator(final List<CompiledStructure.Sort> sorts) {
            this.sorts = sorts;
        }

        @Override
        public int compare(final Object o1, final Object o2) {
            for (final CompiledStructure.Sort sort : sorts) {
                final int index = sort.getIndex();
                final Object v1 = getValue(o1, index);
                final Object v2 = getValue(o2, index);

                int result;
                if (SortDirection.ASCENDING.equals(sort.getDirection())) {
                    result = super.compare(v1, v2);
                } else {
                    result = super.compare(v2, v1);
                }

                if (result != 0) {
                    return result;
                }
            }

            return 0;
        }

        private Object getValue(final Object o, final int index) {
            final Object[] arr = (Object[]) o;
            if (arr.length > index) {
                return arr[index];
            }
            return null;
        }
    }
}
