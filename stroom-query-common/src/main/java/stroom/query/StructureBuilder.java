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

import stroom.query.api.Field;
import stroom.query.api.Format.Type;
import stroom.query.api.Param;
import stroom.query.api.VisField;
import stroom.query.api.VisLimit;
import stroom.query.api.VisNest;
import stroom.query.api.VisSort;
import stroom.query.api.VisStructure;
import stroom.query.api.VisValues;

import java.util.HashMap;
import java.util.Map;

public class StructureBuilder {
    //    private final Map<String, Control> controls = new HashMap<String, Control>();
    private final Field[] fields;
    private final VisStructure structure;
    private final Map<String, String> settings;

    public StructureBuilder(final VisStructure structure, final Param[] params, final Field[] fields)
            throws Exception {
        this.fields = fields;
        this.structure = structure;
        this.settings = new HashMap<>();

        if (params != null) {
            for (final Param param : params) {
                settings.put(param.getKey(), param.getValue());
            }
        }

//        final ObjectMapper mapper = new ObjectMapper();
//        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
//        mapper.setSerializationInclusion(Include.NON_NULL);
//
//        if (settingsJSON != null && settingsJSON.length() > 0) {
//            final VisSettings settings = VisSettingsMapper.read(settingsJSON);
//
//            // Create a map of controls.
//            if (settings.getTabs() != null) {
//                for (final Tab tab : settings.getTabs()) {
//                    if (tab.getControls() != null) {
//                        for (final Control control : tab.getControls()) {
//                            if (control.getId() != null && control != null) {
//                                controls.put(control.getId(), control);
//                            }
//                        }
//                    }
//                }
//            }
//
//            final Data data = settings.getData();
//            structure = data.getStructure();
//        } else {
//            structure = null;
//        }
//
//        if (dashboardSettingsJSON != null && dashboardSettingsJSON.length() > 0) {
//            dashboardSettings = mapper.readTree(dashboardSettingsJSON);
//        }
    }

    public CompiledStructure.Structure create() {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Nest nest = buildNest(structure.getNest());
        final CompiledStructure.Values values = buildValues(structure.getValues());

        return new CompiledStructure.Structure(nest, values);
    }

    private CompiledStructure.Nest buildNest(final VisNest structure) {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Field key = buildField(0, structure.getKey());
        final CompiledStructure.Limit limit = buildLimit(structure.getLimit());
        final CompiledStructure.Nest nest = buildNest(structure.getNest());
        final CompiledStructure.Values values = buildValues(structure.getValues());

        return new CompiledStructure.Nest(key, limit, nest, values);
    }

    private CompiledStructure.Values buildValues(final VisValues structure) {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Field fields[] = buildFields(structure.getFields());
        final CompiledStructure.Limit limit = buildLimit(structure.getLimit());

        return new CompiledStructure.Values(fields, limit);
    }

    private CompiledStructure.Sort buildSort(final int index, final VisSort sort) {
        if (sort == null) {
            return null;
        }

        final String enabledString = resolveValue(settings, sort.getEnabled());
        final String priorityString = resolveValue(settings, sort.getPriority());
        final String directionString = resolveValue(settings, sort.getDirection());

        final Boolean enabled = getBoolean(enabledString);
        final Integer priority = getInteger(priorityString);
        final CompiledStructure.Direction direction = getDirection(directionString);

        if (!enabled) {
            return null;
        }

        int p = -1;
        if (priority != null) {
            p = priority;
        }

        return new CompiledStructure.Sort(index, p, direction);
    }

    private CompiledStructure.Limit buildLimit(final VisLimit limit) {
        if (limit == null) {
            return null;
        }

        final String enabledString = resolveValue(settings, limit.getEnabled());
        final String sizeString = resolveValue(settings, limit.getSize());

        final Boolean enabled = getBoolean(enabledString);
        final Integer size = getInteger(sizeString);

        if (!enabled || size == null) {
            return null;
        }

        return new CompiledStructure.Limit(size);
    }

    private CompiledStructure.Field[] buildFields(final VisField[] fields) {
        if (fields == null) {
            return null;
        }

        final CompiledStructure.Field arr[] = new CompiledStructure.Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            arr[i] = buildField(i, fields[i]);
        }

        return arr;
    }

    private CompiledStructure.Field buildField(final int index, final VisField field) {
        final CompiledStructure.FieldRef id = getFieldRef(field.getId());
        final CompiledStructure.Sort sort = buildSort(index, field.getSort());

        return new CompiledStructure.Field(id, sort);
    }

    private Integer getInteger(final String string) {
        try {
            return Integer.valueOf(string);
        } catch (final NumberFormatException e) {
        }

        return null;
    }

    private Boolean getBoolean(final String string) {
        return Boolean.valueOf(string);
    }

    private CompiledStructure.Direction getDirection(final String string) {
        if (string != null && string.equalsIgnoreCase(CompiledStructure.Direction.DESCENDING.toString())) {
            return CompiledStructure.Direction.DESCENDING;
        }

        return CompiledStructure.Direction.ASCENDING;
    }

    private boolean isReference(final String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private String getReference(final String value) {
        String ref = value;
        ref = ref.substring(2);
        ref = ref.substring(0, ref.length() - 1);
        return ref;
    }

    private CompiledStructure.FieldRef getFieldRef(String fieldName) {
        if (fieldName == null) {
            throw new RuntimeException("Structure element with missing field name");
        }

//        if (isReference(fieldName)) {
//            final String id = getReference(fieldName);
////            final Control control = settings.containsKey(id);
//            if (!settings.containsKey(id)) {
//                throw new RuntimeException("No setting found with id = '" + id + "'");
//            }
//
//            fieldName = getSetting(settings, id);
//        }

        fieldName = resolveValue(settings, fieldName);

        final int fieldIndex = getFieldIndex(fields, fieldName);

        Type fieldType = null;
        if (fieldIndex != -1) {
            final Field field = fields[fieldIndex];
            fieldType = getType(field);
        }

        return new CompiledStructure.FieldRef(fieldType, fieldIndex);
    }

    private String resolveValue(final Map<String, String> settings, final String value) {
        String val = value;
        if (val != null) {
            if (isReference(val)) {
                final String controlId = getReference(val);
                if (!settings.containsKey(controlId)) {
                    throw new RuntimeException("No setting found with id = '" + controlId + "'");
                }
                val = getSetting(settings, controlId);

//                if (val == null) {
//                    final Control control = controls.get(controlId);
//                    if (control != null) {
//                        val = control.getDefaultValue();
//                    }
//                }
            }
        }

        return val;
    }

    private String getSetting(final Map<String, String> settings, final String id) {
        if (settings != null) {
            return settings.get(id);
//            if (item != null) {
//                final String fieldName = item.textValue();
//                return fieldName;
//            }
        }

        return null;
    }

    private int getFieldIndex(final Field[] fields, final String fieldName) {
        if (fieldName != null && fieldName.trim().length() > 0) {
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                if (fieldName.equals(field.getName())) {
                    return i;
                }
            }
        }

        return -1;
    }

    private Type getType(final Field field) {
        Type type = Type.GENERAL;
        if (field != null && field.getFormat() != null && field.getFormat().getType() != null) {
            type = field.getFormat().getType();
        }

        return type;
    }
}
