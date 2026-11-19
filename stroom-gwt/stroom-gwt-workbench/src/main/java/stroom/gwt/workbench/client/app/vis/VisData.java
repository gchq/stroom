/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.vis.VisTable.Type;
import stroom.query.api.Format;
import stroom.visualisation.shared.VisInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Makes a visualisation's data from a table, as Stroom's server does for a dashboard's
/// visualisation, so that a story's test data can be a plain table.
///
/// A visualisation's settings (its `.json` in a content pack) describe the data it wants in
/// `data.structure`: nested keys (`nest`), each grouping the rows by a field, and the fields of the
/// values at the bottom (`values`), each with a sort and a limit. The fields and the other settings
/// are references, e.g. `${x}`, to the dashboard's settings for the visualisation, or else to the
/// defaults of its controls. This does what Stroom's `SearchRequestMapper` (turning the structure
/// into a table's settings), its table (grouping, sorting and limiting the rows) and `VisJson`
/// (turning the grouped rows into the nested `Store`s that visualisations read) do:
/// ```
/// {"keyType": <the type of the keys below>, "keySortDirection": ..., "values": [<child stores>],
///  "types": [<each value's type>], "sortDirections": [...], "min": [...], "max": [...], "sum": [...]}
/// ```
/// where a child store also has its `key`, and the stores at the bottom have the values of each row
/// as their `values`. A key or value is a number for a `NUMBER` or `DATE_TIME` field and a string
/// for a `TEXT` one. A field that isn't set (e.g. no grid series) gives null keys, so one group,
/// of the type `GENERAL`.
public final class VisData {

    private static final String GENERAL = "GENERAL";
    private static final String ASCENDING = "Ascending";
    private static final String DESCENDING = "Descending";

    private final List<Level> levels;
    private final Level values;
    private final int[] valueColumns;

    private VisData(final List<Level> levels, final Level values) {
        this.levels = levels;
        this.values = values;
        this.valueColumns = new int[values.fields.size()];
        for (int i = 0; i < valueColumns.length; i++) {
            valueColumns[i] = values.fields.get(i).column;
        }
    }

    /// Makes a visualisation's data.
    ///
    /// @param visSettingsJson The visualisation's settings, its controls and data structure.
    /// @param settings        The dashboard's settings for it, e.g. `{x: "Hour", y: "Count"}`, may be
    ///                        empty.
    /// @param table           The table of the rows.
    /// @return The data's JSON, as a `VisResult`'s `jsonData`.
    /// @throws IllegalArgumentException If the settings have no data structure, or a field names a
    ///                                  column the table hasn't.
    public static String create(final String visSettingsJson,
                                final Map<String, String> settings,
                                final VisTable table) {
        Objects.requireNonNull(table, "table");
        final Map<?, ?> visSettings = (Map<?, ?>) JsonValues.parse(visSettingsJson);
        final Resolver resolver = new Resolver(visSettings, settings);
        final Map<?, ?> data = (Map<?, ?>) visSettings.get("data");
        final Map<?, ?> structure = data == null
                ? null
                : (Map<?, ?>) data.get("structure");
        if (structure == null) {
            throw new IllegalArgumentException("The visualisation's settings have no data.structure");
        }

        // As SearchRequestMapper, a column for each nest's key, then for each value
        final List<Level> levels = new ArrayList<>();
        Map<?, ?> nest = (Map<?, ?>) structure.get("nest");
        Map<?, ?> valuesJson = (Map<?, ?>) structure.get("values");
        while (nest != null) {
            final Field key = Field.of((Map<?, ?>) nest.get("key"), resolver, table);
            levels.add(new Level(Collections.singletonList(key), resolver.limit(nest.get("limit"))));
            valuesJson = (Map<?, ?>) nest.get("values");
            nest = (Map<?, ?>) nest.get("nest");
        }
        final List<Field> valueFields = new ArrayList<>();
        Long valuesLimit = null;
        if (valuesJson != null) {
            final List<?> fields = (List<?>) valuesJson.get("fields");
            if (fields != null) {
                for (final Object field : fields) {
                    valueFields.add(Field.of((Map<?, ?>) field, resolver, table));
                }
            }
            valuesLimit = resolver.limit(valuesJson.get("limit"));
        }
        final VisData visData = new VisData(levels, new Level(valueFields, valuesLimit));
        return JsonValues.toJson(visData.store(null, table.getRows(), 0).toJson());
    }

    // As VisJson.getStore: the store of a group of rows at a depth (0 for all the rows)
    private Store store(final Object key, final List<List<Object>> rows, final int depth) {
        final int valueCount = valueColumns.length;
        final Double[] min = new Double[valueCount];
        final Double[] max = new Double[valueCount];
        final Double[] sum = new Double[valueCount];
        final List<Object> children = new ArrayList<>();

        if (depth < levels.size()) {
            final Level level = levels.get(depth);
            final Field keyField = level.fields.get(0);
            final List<Group> groups = group(rows, keyField);
            if (keyField.sortDirection != null) {
                groups.sort((group1, group2) -> keyField.compare(group1.key, group2.key));
            }
            for (final Group group : limited(groups, level.limit)) {
                final Store child = store(group.key, group.rows, depth + 1);
                children.add(child.toJson());
                for (int i = 0; i < valueCount; i++) {
                    min[i] = min(min[i], child.min[i]);
                    max[i] = max(max[i], child.max[i]);
                    sum[i] = sum(sum[i], child.sum[i]);
                }
            }
        } else {
            final List<List<Object>> sorted = new ArrayList<>(rows);
            final Comparator<List<Object>> comparator = valuesComparator();
            if (comparator != null) {
                sorted.sort(comparator);
            }
            for (final List<Object> row : limited(sorted, values.limit)) {
                final List<Object> vals = new ArrayList<>();
                for (int i = 0; i < valueCount; i++) {
                    final Field field = values.fields.get(i);
                    final Object value = field.valueOf(row);
                    vals.add(value);
                    // As VisJson, a text field has no minimum, maximum or sum
                    final Double dbl = field.columnType == Type.TEXT
                            ? null
                            : toDouble(value);
                    if (dbl != null) {
                        min[i] = min(min[i], dbl);
                        max[i] = max(max[i], dbl);
                        sum[i] = sum(sum[i], dbl);
                    }
                }
                children.add(vals);
            }
        }

        // The type and sort direction of the keys of the level below, or of the first value
        final Store store = new Store(min, max, sum);
        store.json.put("key", depth > 0
                ? key
                : null);
        store.json.put("keyType", depth < levels.size()
                ? levels.get(depth).fields.get(0).type
                : firstOrNull(values.types()));
        store.json.put("keySortDirection", depth < levels.size()
                ? levels.get(depth).fields.get(0).sortDirection
                : firstOrNull(values.sortDirections()));
        store.json.put("values", children);
        store.json.put("types", values.types());
        store.json.put("sortDirections", values.sortDirections());
        return store;
    }

    // The rows grouped by a key, in the order each key is first seen
    private static List<Group> group(final List<List<Object>> rows, final Field keyField) {
        final Map<Object, Group> groups = new LinkedHashMap<>();
        for (final List<Object> row : rows) {
            final Object key = keyField.valueOf(row);
            groups.computeIfAbsent(key, k -> new Group(key)).rows.add(row);
        }
        return new ArrayList<>(groups.values());
    }

    // Compares rows by the sorted value fields, in the order of their priorities
    private Comparator<List<Object>> valuesComparator() {
        final List<Integer> sorted = new ArrayList<>();
        for (int i = 0; i < values.fields.size(); i++) {
            if (values.fields.get(i).sortDirection != null) {
                sorted.add(i);
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        sorted.sort(Comparator.comparingInt(i -> values.fields.get(i).priority));
        return (row1, row2) -> {
            for (final int index : sorted) {
                final Field field = values.fields.get(index);
                final int result = field.column < 0
                        ? 0
                        : field.compare(field.valueOf(row1), field.valueOf(row2));
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        };
    }

    private static <T> List<T> limited(final List<T> list, final Long limit) {
        return limit == null || list.size() <= limit
                ? list
                : list.subList(0, (int) (long) limit);
    }

    // As VisTypeConverter.getDouble: a number, or text that is a number
    private static Double toDouble(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String && ((String) value).matches("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) {
            return Double.parseDouble((String) value);
        }
        return null;
    }

    private static Double min(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        }
        return m2 == null
                ? m1
                : Math.min(m1, m2);
    }

    private static Double max(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        }
        return m2 == null
                ? m1
                : Math.max(m1, m2);
    }

    private static Double sum(final Double m1, final Double m2) {
        if (m1 == null) {
            return m2;
        }
        return m2 == null
                ? m1
                : m1 + m2;
    }

    private static <T> T firstOrNull(final List<T> list) {
        return list.isEmpty()
                ? null
                : list.get(0);
    }


    // --------------------------------------------------------------------------------


    // A field of the structure: the table column it names (-1 for none), its type and sort. A field
    // the visualisation declares an input for has its column's values converted to the input's type
    // (VisStructureMapper)
    private static final class Field {

        private final int column;
        private final String type;
        private final String sortDirection;
        private final int priority;
        private final Type columnType;

        private Field(final int column,
                      final Type columnType,
                      final String sortDirection,
                      final int priority) {
            this.column = column;
            this.columnType = columnType;
            this.type = columnType == null
                    ? GENERAL
                    : columnType.name();
            this.sortDirection = sortDirection;
            this.priority = priority;
        }

        static Field of(final Map<?, ?> json, final Resolver resolver, final VisTable table) {
            final String name = json == null
                    ? null
                    : resolver.resolve(json.get("id"));
            int column = -1;
            Type columnType = null;
            if (name != null && !name.isEmpty()) {
                column = table.indexOf(name);
                if (column < 0) {
                    throw new IllegalArgumentException("The visualisation's field '" + json.get("id")
                                                       + "' is the column '" + name
                                                       + "', which the table hasn't");
                }
                columnType = table.getColumns().get(column).getType();
                final VisInput input = resolver.input(json.get("id"));
                if (input != null) {
                    final Format.Type conversion = input.conversionFor(Format.Type.valueOf(columnType.name()));
                    if (conversion != null) {
                        columnType = Type.valueOf(conversion.name());
                    }
                }
            }
            String sortDirection = null;
            int priority = 0;
            final Map<?, ?> sort = json == null
                    ? null
                    : (Map<?, ?>) json.get("sort");
            if (sort != null && "true".equalsIgnoreCase(resolver.resolve(sort.get("enabled")))) {
                final String direction = resolver.resolve(sort.get("direction"));
                if (ASCENDING.equalsIgnoreCase(direction)) {
                    sortDirection = ASCENDING;
                } else if (DESCENDING.equalsIgnoreCase(direction)) {
                    sortDirection = DESCENDING;
                }
                final String priorityText = resolver.resolve(sort.get("priority"));
                if (priorityText != null && !priorityText.isEmpty()) {
                    priority = (int) Double.parseDouble(priorityText);
                }
            }
            return new Field(column, columnType, sortDirection, priority);
        }

        // The field's value in a row, of its type
        Object valueOf(final List<Object> row) {
            if (column < 0) {
                return null;
            }
            final Object value = row.get(column);
            if (value == null || columnType == null) {
                return value;
            }
            if (columnType == Type.TEXT) {
                return value instanceof Double && ((Double) value) == Math.rint((Double) value)
                        ? String.valueOf(((Double) value).longValue())
                        : String.valueOf(value);
            }
            return toDouble(value);
        }

        // Compares two of the field's values in its sort direction, nulls first when ascending
        int compare(final Object value1, final Object value2) {
            final int result;
            if (value1 == null || value2 == null) {
                result = value1 == null
                        ? (value2 == null
                                ? 0
                                : -1)
                        : 1;
            } else if (columnType == Type.TEXT) {
                final int ignoringCase = String.valueOf(value1).compareToIgnoreCase(String.valueOf(value2));
                result = ignoringCase != 0
                        ? ignoringCase
                        : String.valueOf(value1).compareTo(String.valueOf(value2));
            } else {
                result = Double.compare(((Number) value1).doubleValue(), ((Number) value2).doubleValue());
            }
            return DESCENDING.equals(sortDirection)
                    ? -result
                    : result;
        }
    }


    // --------------------------------------------------------------------------------


    // A nest's key, or the values, and the limit on how many of them each parent has
    private static final class Level {

        private final List<Field> fields;
        private final Long limit;

        private Level(final List<Field> fields, final Long limit) {
            this.fields = fields;
            this.limit = limit;
        }

        List<String> types() {
            final List<String> types = new ArrayList<>();
            for (final Field field : fields) {
                types.add(field.type);
            }
            return types;
        }

        List<String> sortDirections() {
            final List<String> sortDirections = new ArrayList<>();
            for (final Field field : fields) {
                sortDirections.add(field.sortDirection);
            }
            return sortDirections;
        }
    }


    // --------------------------------------------------------------------------------


    // The rows with a key
    private static final class Group {

        private final Object key;
        private final List<List<Object>> rows = new ArrayList<>();

        private Group(final Object key) {
            this.key = key;
        }
    }


    // --------------------------------------------------------------------------------


    // A store (VisResult.Store): its members, and the min, max and sum of each value below it
    private static final class Store {

        private final Map<String, Object> json = new LinkedHashMap<>();
        private final Double[] min;
        private final Double[] max;
        private final Double[] sum;

        private Store(final Double[] min, final Double[] max, final Double[] sum) {
            this.min = min;
            this.max = max;
            this.sum = sum;
        }

        // In the order of VisResult.Store's properties. Null members are left out, as Stroom's
        // JSON leaves them out
        Map<String, Object> toJson() {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (final Map.Entry<String, Object> entry : json.entrySet()) {
                if (entry.getValue() != null) {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
            map.put("min", Arrays.asList(min));
            map.put("max", Arrays.asList(max));
            map.put("sum", Arrays.asList(sum));
            return map;
        }
    }


    // --------------------------------------------------------------------------------


    // Resolves a setting: a reference such as ${x} to the dashboard's setting for the control x,
    // or else the control's default value, as SearchRequestMapper's SettingResolver does
    private static final class Resolver {

        private final Map<String, String> defaults = new LinkedHashMap<>();
        private final Map<String, VisInput> inputs = new LinkedHashMap<>();
        private final Map<String, String> settings;

        private Resolver(final Map<?, ?> visSettings, final Map<String, String> settings) {
            this.settings = settings == null
                    ? Collections.emptyMap()
                    : settings;
            final List<?> tabs = (List<?>) visSettings.get("tabs");
            if (tabs != null) {
                for (final Object tab : tabs) {
                    final List<?> controls = (List<?>) ((Map<?, ?>) tab).get("controls");
                    if (controls != null) {
                        for (final Object control : controls) {
                            final Map<?, ?> map = (Map<?, ?>) control;
                            if (map.get("id") != null) {
                                defaults.put(String.valueOf(map.get("id")), map.get("defaultValue") == null
                                        ? null
                                        : String.valueOf(map.get("defaultValue")));
                            }
                        }
                    }
                }
            }
            final List<?> inputList = (List<?>) visSettings.get("inputs");
            if (inputList != null) {
                for (final Object inputJson : inputList) {
                    final Map<?, ?> map = (Map<?, ?>) inputJson;
                    if (map.get("id") != null) {
                        final List<String> types = new ArrayList<>();
                        if (map.get("types") instanceof List) {
                            for (final Object type : (List<?>) map.get("types")) {
                                types.add(String.valueOf(type));
                            }
                        }
                        final String id = String.valueOf(map.get("id"));
                        inputs.put(id, new VisInput(id, null, null, types, null, null));
                    }
                }
            }
        }

        // The input a structure's field refers to, e.g. the input x for ${x}, if there is one
        VisInput input(final Object fieldId) {
            final String text = fieldId == null
                    ? ""
                    : String.valueOf(fieldId);
            return text.startsWith("${") && text.endsWith("}")
                    ? inputs.get(text.substring(2, text.length() - 1))
                    : null;
        }

        String resolve(final Object value) {
            if (value == null) {
                return null;
            }
            final String text = String.valueOf(value);
            if (text.startsWith("${") && text.endsWith("}")) {
                final String id = text.substring(2, text.length() - 1);
                final String setting = settings.get(id);
                return setting != null
                        ? setting
                        : defaults.get(id);
            }
            return text;
        }

        // A limit's size, if it is enabled
        Long limit(final Object limitJson) {
            if (!(limitJson instanceof Map)) {
                return null;
            }
            final Map<?, ?> limit = (Map<?, ?>) limitJson;
            if (!"true".equalsIgnoreCase(resolve(limit.get("enabled")))) {
                return null;
            }
            final String size = resolve(limit.get("size"));
            return size == null || size.isEmpty()
                    ? null
                    : (long) Double.parseDouble(size);
        }
    }
}
