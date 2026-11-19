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

package stroom.dashboard.impl.vis;

import stroom.dashboard.impl.vis.VisSettings.Control;
import stroom.dashboard.impl.vis.VisSettings.Field;
import stroom.dashboard.impl.vis.VisSettings.Limit;
import stroom.dashboard.impl.vis.VisSettings.Nest;
import stroom.dashboard.impl.vis.VisSettings.Structure;
import stroom.dashboard.impl.vis.VisSettings.Tab;
import stroom.dashboard.impl.vis.VisSettings.Values;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.ParamUtil;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisInput;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Turns the structure of a visualisation's data (the `data.structure` of its settings, whose
/// values may refer to its controls, e.g. `${x}`) into the columns and limits of the table that
/// gives the visualisation its data, as both dashboards and StroomQL's `vis as` need.
///
/// A control's value is the one chosen for it (a dashboard's settings for the visualisation, or the
/// parameters of a `vis as`), or the control's default. A field that the visualisation declares an
/// input for (its typed `inputs`) is converted to the input's type, so that, e.g., a Y axis that
/// takes numbers gets numbers from a column of text.
public final class VisStructureMapper {

    private final Map<String, Control> controls = new HashMap<>();
    private final Map<String, VisInput> inputs = new HashMap<>();
    private final Map<String, String> chosenValues;
    private final Map<String, Format> parentFormats = new HashMap<>();

    private VisStructureMapper(final VisSettings visSettings,
                               final Map<String, String> chosenValues,
                               final List<Column> parentColumns) {
        this.chosenValues = NullSafe.map(chosenValues);
        for (final Tab tab : NullSafe.asList(visSettings.getTabs())) {
            if (tab != null) {
                for (final Control control : NullSafe.asList(tab.getControls())) {
                    if (control != null && control.getId() != null) {
                        controls.put(control.getId(), control);
                    }
                }
            }
        }
        for (final VisInput input : NullSafe.list(visSettings.getInputs())) {
            if (input != null && input.getId() != null) {
                inputs.put(input.getId(), input);
            }
        }
        for (final Column column : NullSafe.list(parentColumns)) {
            if (column != null) {
                parentFormats.put(column.getName(), column.getFormat());
            }
        }
    }

    /// Maps a visualisation's data structure to the columns and limits of the table of its data.
    ///
    /// @param visSettings   The visualisation's settings.
    /// @param chosenValues  The values chosen for its controls, by control id.
    /// @param parentColumns The columns of the table the visualisation takes its data from.
    /// @return The columns (the keys of the nested levels, grouped, then the values) and the
    /// limit of each level, or null if the settings have no data structure.
    /// @throws RuntimeException If a field of the structure has no id, or a setting that should
    ///                          be a number isn't.
    public static VisTable map(final VisSettings visSettings,
                               final Map<String, String> chosenValues,
                               final List<Column> parentColumns) {
        if (visSettings == null || visSettings.getData() == null || visSettings.getData().getStructure() == null) {
            return null;
        }
        return new VisStructureMapper(visSettings, chosenValues, parentColumns)
                .map(visSettings.getData().getStructure());
    }

    /// Reads the values chosen for a visualisation's controls from a dashboard's settings for it.
    ///
    /// @param json The settings, a JSON object of control id to value, may be null or empty.
    /// @return The values, by control id.
    public static Map<String, String> chosenValuesFromJson(final String json) {
        final Map<String, String> map = new HashMap<>();
        if (!NullSafe.isBlankString(json)) {
            final JsonNode node = JsonUtil.getNoIndentMapper().readTree(json);
            node.properties().forEach(entry -> {
                final JsonNode value = entry.getValue();
                if (value != null) {
                    final String string = value.stringValue();
                    if (string != null) {
                        map.put(entry.getKey(), string);
                    }
                }
            });
        }
        return map;
    }

    private VisTable map(final Structure structure) {
        final List<Column> columns = new ArrayList<>();
        final List<Long> limits = new ArrayList<>();

        Nest nest = structure.getNest();
        Values values = structure.getValues();
        int group = 0;
        while (nest != null) {
            columns.add(column(nest.getKey()).group(group++).build());
            limits.add(limitSize(nest.getLimit()));
            values = nest.getValues();
            nest = nest.getNest();
        }

        if (values != null) {
            final Long limit = limitSize(values.getLimit());
            limits.add(limit != null
                    ? limit
                    : Long.MAX_VALUE);
            for (final Field field : NullSafe.asList(values.getFields())) {
                if (field != null) {
                    columns.add(column(field).build());
                }
            }
        }

        return new VisTable(Collections.unmodifiableList(columns), Collections.unmodifiableList(limits));
    }

    // The column of a field, formatted as the parent table's column of the same name is, or as the
    // input the field refers to takes
    private Column.Builder column(final Field field) {
        if (field == null) {
            throw new RuntimeException("Structure element with missing field");
        }
        if (field.getId() == null) {
            throw new RuntimeException("Structure element with missing field name");
        }
        final String columnName = resolve(field.getId());

        final Column.Builder builder = Column.builder();
        Format format = Format.GENERAL;
        if (columnName != null) {
            final Format parentFormat = parentFormats.get(columnName);
            if (parentFormat != null) {
                format = parentFormat;
            }
            final VisInput input = isReference(field.getId())
                    ? inputs.get(referencedId(field.getId()))
                    : null;
            if (input != null) {
                final Format.Type conversion = input.conversionFor(format.getType());
                if (conversion != null) {
                    format = formatOf(conversion);
                }
            }
            builder.expression(ParamUtil.create(columnName));
        }
        builder.format(format);
        builder.sort(sort(field.getSort()));
        return builder;
    }

    private static Format formatOf(final Format.Type type) {
        return switch (type) {
            case NUMBER -> Format.NUMBER;
            case DATE_TIME -> Format.DATE_TIME;
            case TEXT -> Format.TEXT;
            case GENERAL -> Format.GENERAL;
        };
    }

    private Sort sort(final VisSettings.Sort sort) {
        if (sort == null) {
            return null;
        }
        final String enabled = resolve(sort.getEnabled());
        if (enabled == null || !Boolean.parseBoolean(enabled)) {
            return null;
        }
        final String direction = resolve(sort.getDirection());
        if (direction == null) {
            return null;
        }
        final SortDirection sortDirection;
        if (direction.equalsIgnoreCase(SortDirection.ASCENDING.getDisplayValue())) {
            sortDirection = SortDirection.ASCENDING;
        } else if (direction.equalsIgnoreCase(SortDirection.DESCENDING.getDisplayValue())) {
            sortDirection = SortDirection.DESCENDING;
        } else {
            return null;
        }
        final String priority = resolve(sort.getPriority());
        return new Sort(priority == null
                ? null
                : Integer.valueOf(priority), sortDirection);
    }

    // The size of a limit, or null if it is disabled or has none
    private Long limitSize(final Limit limit) {
        if (limit == null) {
            return null;
        }
        final String enabled = resolve(limit.getEnabled());
        if (enabled != null && !Boolean.parseBoolean(enabled)) {
            return null;
        }
        final String size = resolve(limit.getSize());
        return size == null
                ? null
                : Long.valueOf(size);
    }

    // A value of the structure, which is either itself or, as `${id}`, the value chosen for the
    // control with the id (or its default)
    private String resolve(final String value) {
        if (!isReference(value)) {
            return value;
        }
        final String controlId = referencedId(value);
        final String chosen = chosenValues.get(controlId);
        if (chosen != null) {
            return chosen;
        }
        final Control control = controls.get(controlId);
        return control != null
                ? control.getDefaultValue()
                : null;
    }

    private static boolean isReference(final String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private static String referencedId(final String reference) {
        return reference.substring(2, reference.length() - 1);
    }


    // --------------------------------------------------------------------------------


    /// The columns and limits of the table that gives a visualisation its data.
    ///
    /// @param columns   The keys of the nested levels (grouped, in level order), then the values.
    /// @param maxResults The limit of each level, null for none, the values' last.
    public record VisTable(List<Column> columns, List<Long> maxResults) {

    }
}
