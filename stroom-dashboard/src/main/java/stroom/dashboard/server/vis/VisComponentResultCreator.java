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

package stroom.dashboard.server.vis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import stroom.dashboard.expression.Generator;
import stroom.dashboard.expression.ObjectCompareUtil;
import stroom.dashboard.expression.TypeConverter;
import stroom.dashboard.server.ComponentResultCreator;
import stroom.dashboard.server.MyDoubleSerialiser;
import stroom.dashboard.server.vis.CompiledStructure.Direction;
import stroom.dashboard.server.vis.CompiledStructure.Field;
import stroom.dashboard.server.vis.CompiledStructure.Limit;
import stroom.dashboard.server.vis.CompiledStructure.Nest;
import stroom.dashboard.server.vis.CompiledStructure.Sort;
import stroom.dashboard.server.vis.CompiledStructure.Structure;
import stroom.dashboard.server.vis.CompiledStructure.Values;
import stroom.dashboard.shared.VisResult;
import stroom.dashboard.shared.VisResultRequest;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ResultStore;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.Format.Type;
import stroom.query.shared.VisDashboardSettings;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;
import stroom.util.shared.SharedObject;

public class VisComponentResultCreator implements ComponentResultCreator {
    private static class ObjectComparator implements Comparator<Object> {
        @Override
        public int compare(final Object o1, final Object o2) {
            return ObjectCompareUtil.compare(o1, o2);
        }
    }

    private static class StoreComparator extends ObjectComparator {
        private final CompiledStructure.Direction direction;

        public StoreComparator(final CompiledStructure.Direction direction) {
            this.direction = direction;
        }

        @Override
        public int compare(final Object o1, final Object o2) {
            if (CompiledStructure.Direction.ASCENDING.equals(direction)) {
                return super.compare(((Store) o1).getKey(), (((Store) o2).getKey()));
            } else {
                return super.compare(((Store) o2).getKey(), (((Store) o1).getKey()));
            }
        }
    }

    private static class ValuesComparator extends ObjectComparator {
        private final List<Sort> sorts;

        public ValuesComparator(final List<Sort> sorts) {
            this.sorts = sorts;
        }

        @Override
        public int compare(final Object o1, final Object o2) {
            for (final Sort sort : sorts) {
                final int index = sort.getIndex();
                final Object v1 = getValue(o1, index);
                final Object v2 = getValue(o2, index);

                int result = 0;
                if (Direction.ASCENDING.equals(sort.getDirection())) {
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

    private final Structure structure;
    private String error;

    public static VisComponentResultCreator create(final VisualisationService visualisationService,
            final ComponentResultRequest componentResultRequest) {
        Structure structure = null;
        try {
            final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;

            final VisDashboardSettings visDashboardSettings = visResultRequest.getVisDashboardSettings();
            final Visualisation visualisation = visualisationService
                    .loadByUuid(visDashboardSettings.getVisualisation().getUuid());
            if (visualisation != null) {
                final StructureBuilder structureBuilder = new StructureBuilder(visualisation.getSettings(),
                        visDashboardSettings.getJSON(), visDashboardSettings.getTableSettings().getFields());
                structure = structureBuilder.create();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return new VisComponentResultCreator(structure);
    }

    public VisComponentResultCreator(final Structure structure) {
        this.structure = structure;
    }

    @Override
    public SharedObject create(final ResultStore resultStore, final ComponentResultRequest componentResultRequest) {
        if (error == null) {
            try {
                // Get top level items.
                final Items<Item> items = resultStore.getChildMap().get(null);
                final Store store = create(items);

                int dataPoints = 0;
                if (items != null && items.size() > 0) {
                    dataPoints = items.size();
                }

                final SimpleModule module = new SimpleModule();
                module.addSerializer(Double.class, new MyDoubleSerialiser());

                final ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(module);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
                mapper.setSerializationInclusion(Include.NON_NULL);

                String json = null;
                if (store != null) {
                    json = mapper.writeValueAsString(store);
                }
                return new VisResult(json, dataPoints, error);

            } catch (final Exception e) {
                error = e.getMessage();
            }
        }

        return new VisResult(null, 0, error);
    }

    /**
     * Public method for testing purposes.
     */
    public Store create(final Items<Item> items) throws JsonProcessingException {
        return create(items, structure);
    }

    /**
     * Public method for testing purposes.
     */
    public Store create(final Items<Item> items, final Structure structure) {
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
    public Store create(final Items<Item> items, final Nest structure) {
        if (items != null && items.size() > 0) {
            final String keyType = getKeyType(structure);
            final Field[] fields = getFields(structure);
            final String[] types = getTypes(fields);

            final Map<Object, Store> map = new HashMap<Object, Store>();
            final Store store = new Store(null, keyType, types);
            store.setMap(map);

            // Iterate over the items.
            for (final Item item : items) {
                addNest(structure, map, item);
            }

            // Apply sorting and trimming if required.
            sort(store, structure);

            return store;
        }

        return null;
    }

    /**
     * Public method for testing purposes.
     */
    public Store create(final Items<Item> items, final Values structure) {
        if (items != null && items.size() > 0) {
            final Field[] fields = structure.getFields();
            final String[] types = getTypes(fields);

            final int len = fields.length;
            final Store store = new Store(null, new ArrayList<Object>(), types, len);

            // Iterate over the items.
            for (final Item item : items) {
                addValues(structure, store, item);
            }

            // Apply sorting and trimming if required.
            sort(store, structure);

            return store;
        }

        return null;
    }

    private Field[] getFields(final Nest structure) {
        if (structure.getNest() != null) {
            return getFields(structure.getNest());
        }
        if (structure.getValues() != null) {
            return structure.getValues().getFields();
        }
        return null;
    }

    private String[] getTypes(final Field[] fields) {
        String[] types = null;
        if (fields != null) {
            types = new String[fields.length];
            for (int i = 0; i < types.length; i++) {
                final Field field = fields[i];
                if (field != null && field.getId() != null && field.getId().getType() != null) {
                    types[i] = field.getId().getType().name();
                }
            }
        }
        return types;
    }

    private String getKeyType(final Nest structure) {
        String keyType = null;
        if (structure != null && structure.getKey() != null && structure.getKey().getId() != null
                && structure.getKey().getId().getType() != null) {
            keyType = structure.getKey().getId().getType().name();
        }
        return keyType;
    }

    private void sort(final Store store, final Nest nest) {
        // Sort children first.
        if (nest.getNest() != null) {
            if (store.getMap() != null) {
                for (final Store subStore : store.getMap().values()) {
                    sort(subStore, nest.getNest());
                }
            } else if (store.getList() != null) {
                for (final Object subStore : store.getList()) {
                    sort((Store) subStore, nest.getNest());
                }
            }
        } else if (nest.getValues() != null) {
            if (store.getMap() != null) {
                for (final Store subStore : store.getMap().values()) {
                    sort(subStore, nest.getValues());
                }
            } else if (store.getList() != null) {
                for (final Object subStore : store.getList()) {
                    sort((Store) subStore, nest.getValues());
                }
            }
        }

        // Now sort this store.
        if ((nest.getKey() != null && nest.getKey().getSort() != null) || nest.getLimit() != null) {
            if (store.getMap() != null && store.getMap().size() > 0) {
                final List<Object> list = new ArrayList<Object>(store.getMap().values());
                store.list = list;
                store.map = null;

                if (nest.getKey().getSort() != null) {
                    Collections.sort(list, new StoreComparator(nest.getKey().getSort().getDirection()));
                }
                trimList(list, nest.getLimit());
            }
        }

        // Calculate min, max and sum values for all sub stores.
        if (store.getList() != null && store.getList().size() > 0) {
            Double[] min = null;
            Double[] max = null;
            Double[] sum = null;
            for (final Object obj : store.getList()) {
                final Store subStore = (Store) obj;
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
                    sum = new Double[subStore.max.length];
                }
                for (int i = 0; i < subStore.sum.length; i++) {
                    sum[i] = sum(sum[i], subStore.sum[i]);
                }
            }
            store.min = min;
            store.max = max;
            store.sum = sum;
        }
    }

    private void sort(final Store store, final Values values) {
        if (values.getFields() != null && values.getFields().length > 0 && store.getList().size() > 0) {
            final List<Sort> sorts = new ArrayList<Sort>();

            // Create a list of sort options.
            for (final Field field : values.getFields()) {
                if (field.getSort() != null) {
                    sorts.add(field.getSort());
                }
            }

            if (sorts.size() > 0) {
                // Sort the sort options by sort priority.
                Collections.sort(sorts, new Comparator<Sort>() {
                    @Override
                    public int compare(final Sort o1, final Sort o2) {
                        return Integer.compare(o1.getPriority(), o2.getPriority());
                    }
                });

                final Comparator<Object> comparator = new ValuesComparator(sorts);
                Collections.sort(store.getList(), comparator);
            }
        }

        trimList(store.getList(), values.getLimit());
    }

    private void trimList(final List<Object> list, final Limit limit) {
        if (list != null && limit != null) {
            final int l = limit.getSize();
            while (list.size() > l) {
                list.remove(list.size() - 1);
            }
        }
    }

    private void addNest(final Nest structure, final Map<Object, Store> map, final Item item) {
        Object key = null;
        if (structure.getKey() != null && structure.getKey().getId() != null) {
            key = getValue(item, structure.getKey().getId().getIndex(), structure.getKey().getId().getType());
        }

        Store store = map.get(key);
        if (store == null) {
            if (structure.getNest() != null) {
                final String keyType = getKeyType(structure);
                store = new Store(key, keyType, null);
                store.setMap(new HashMap<Object, Store>());
                map.put(key, store);
            } else if (structure.getValues() != null) {
                final int len = structure.getValues().getFields().length;
                store = new Store(key, new ArrayList<Object>(), null, len);
                map.put(key, store);
            }
        }

        if (structure.getNest() != null) {
            final Nest subStructure = structure.getNest();
            addNest(subStructure, store.getMap(), item);
        } else if (structure.getValues() != null) {
            final Values subStructure = structure.getValues();
            addValues(subStructure, store, item);
        }
    }

    private void addValues(final Values structure, final Store store, final Item item) {
        final Object[] objects = new Object[structure.getFields().length];
        for (int i = 0; i < structure.getFields().length; i++) {
            final Field field = structure.getFields()[i];
            if (field != null && field.getId() != null) {
                addValue(item, field.getId().getIndex(), field.getId().getType(), store.min, store.max, store.sum,
                        objects, i);
            }
        }
        store.add(objects);
    }

    private Object getValue(final Item item, final int index, final Type type) {
        if (index >= 0 && item.getValues().length > index) {
            final Object o = item.getValues()[index];
            if (o != null) {
                // Convert all values into fully resolved objects evaluating
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
                // Convert all values into fully resolved objects evaluating
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
}
