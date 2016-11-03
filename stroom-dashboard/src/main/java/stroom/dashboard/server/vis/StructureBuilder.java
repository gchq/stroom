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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import stroom.dashboard.server.vis.CompiledStructure.FieldRef;
import stroom.dashboard.server.vis.VisSettings.Control;
import stroom.dashboard.server.vis.VisSettings.Data;
import stroom.dashboard.server.vis.VisSettings.Nest;
import stroom.dashboard.server.vis.VisSettings.Structure;
import stroom.dashboard.server.vis.VisSettings.Tab;
import stroom.query.shared.Field;
import stroom.query.shared.Format.Type;

public class StructureBuilder {
    private final Map<String, Control> controls = new HashMap<String, Control>();
    private final List<Field> fields;
    private final Structure structure;
    private JsonNode dashboardSettings;

    public StructureBuilder(final String settingsJSON, final String dashboardSettingsJSON, final List<Field> fields)
            throws Exception {
        this.fields = fields;

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        if (settingsJSON != null && settingsJSON.length() > 0) {
            final VisSettings settings = VisSettingsUtil.read(settingsJSON);

            // Create a map of controls.
            if (settings.getTabs() != null) {
                for (final Tab tab : settings.getTabs()) {
                    if (tab.getControls() != null) {
                        for (final Control control : tab.getControls()) {
                            if (control.getId() != null && control != null) {
                                controls.put(control.getId(), control);
                            }
                        }
                    }
                }
            }

            final Data data = settings.getData();
            structure = data.getStructure();
        } else {
            structure = null;
        }

        if (dashboardSettingsJSON != null && dashboardSettingsJSON.length() > 0) {
            dashboardSettings = mapper.readTree(dashboardSettingsJSON);
        }
    }

    public CompiledStructure.Structure create() {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Nest nest = buildNest(structure.getNest());
        final CompiledStructure.Values values = buildValues(structure.getValues());

        return new CompiledStructure.Structure(nest, values);
    }

    private CompiledStructure.Nest buildNest(final Nest structure) {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Field key = buildField(0, structure.getKey());
        final CompiledStructure.Limit limit = buildLimit(structure.getLimit());
        final CompiledStructure.Nest nest = buildNest(structure.getNest());
        final CompiledStructure.Values values = buildValues(structure.getValues());

        return new CompiledStructure.Nest(key, limit, nest, values);
    }

    private CompiledStructure.Values buildValues(final VisSettings.Values structure) {
        if (structure == null) {
            return null;
        }

        final CompiledStructure.Field fields[] = buildFields(structure.getFields());
        final CompiledStructure.Limit limit = buildLimit(structure.getLimit());

        return new CompiledStructure.Values(fields, limit);
    }

    private CompiledStructure.Sort buildSort(final int index, final VisSettings.Sort sort) {
        if (sort == null) {
            return null;
        }

        final String enabledString = resolveValue(dashboardSettings, controls, sort.getEnabled());
        final String priorityString = resolveValue(dashboardSettings, controls, sort.getPriority());
        final String directionString = resolveValue(dashboardSettings, controls, sort.getDirection());

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

    private CompiledStructure.Limit buildLimit(final VisSettings.Limit limit) {
        if (limit == null) {
            return null;
        }

        final String enabledString = resolveValue(dashboardSettings, controls, limit.getEnabled());
        final String sizeString = resolveValue(dashboardSettings, controls, limit.getSize());

        final Boolean enabled = getBoolean(enabledString);
        final Integer size = getInteger(sizeString);

        if (!enabled || size == null) {
            return null;
        }

        return new CompiledStructure.Limit(size);
    }

    private CompiledStructure.Field[] buildFields(final VisSettings.Field[] fields) {
        if (fields == null) {
            return null;
        }

        final CompiledStructure.Field arr[] = new CompiledStructure.Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            arr[i] = buildField(i, fields[i]);
        }

        return arr;
    }

    private CompiledStructure.Field buildField(final int index, final VisSettings.Field field) {
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
        if (value != null) {
            return value.startsWith("${") && value.endsWith("}");
        }

        return false;
    }

    private String getReference(final String value) {
        String ref = value;
        ref = ref.substring(2);
        ref = ref.substring(0, ref.length() - 1);
        return ref;
    }

    private FieldRef getFieldRef(String fieldName) {
        if (fieldName == null) {
            throw new RuntimeException("Structure element with missing field name");
        }

        if (isReference(fieldName)) {
            final String id = getReference(fieldName);
            final Control control = controls.get(id);
            if (control == null) {
                throw new RuntimeException("No control found with id = '" + id + "'");
            }

            fieldName = getSetting(dashboardSettings, id);
        }

        final int fieldIndex = getFieldIndex(fields, fieldName);

        Type fieldType = null;
        if (fieldIndex != -1) {
            final Field field = fields.get(fieldIndex);
            fieldType = getType(field);
        }

        return new FieldRef(fieldType, fieldIndex);
    }

    private String resolveValue(final JsonNode settings, final Map<String, Control> controls, final String value) {
        String val = value;
        if (val != null) {
            if (isReference(val)) {
                final String controlId = getReference(val);
                val = getSetting(settings, controlId);

                if (val == null) {
                    final Control control = controls.get(controlId);
                    if (control != null) {
                        val = control.getDefaultValue();
                    }
                }
            }
        }

        return val;
    }

    private String getSetting(final JsonNode node, final String id) {
        if (node != null) {
            final JsonNode item = node.get(id);
            if (item != null) {
                final String fieldName = item.textValue();
                return fieldName;
            }
        }

        return null;
    }

    private int getFieldIndex(final List<Field> fields, final String fieldName) {
        if (fieldName != null && fieldName.trim().length() > 0) {
            for (int i = 0; i < fields.size(); i++) {
                final Field field = fields.get(i);
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
