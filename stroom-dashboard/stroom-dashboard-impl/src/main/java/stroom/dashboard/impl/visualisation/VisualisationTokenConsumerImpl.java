/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.impl.visualisation;

import stroom.dashboard.impl.VisField;
import stroom.dashboard.impl.VisLimit;
import stroom.dashboard.impl.VisNest;
import stroom.dashboard.impl.VisValues;
import stroom.dashboard.impl.vis.VisSettings;
import stroom.dashboard.impl.vis.VisSettings.Control;
import stroom.dashboard.impl.vis.VisSettings.Data;
import stroom.dashboard.impl.vis.VisSettings.Nest;
import stroom.dashboard.impl.vis.VisSettings.Structure;
import stroom.dashboard.impl.vis.VisSettings.Tab;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.QLVisSettings;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.language.DocResolver;
import stroom.query.language.VisualisationTokenConsumer;
import stroom.query.language.token.AbstractToken;
import stroom.query.language.token.FunctionGroup;
import stroom.query.language.token.KeywordGroup;
import stroom.query.language.token.TokenException;
import stroom.query.language.token.TokenGroup;
import stroom.query.language.token.TokenType;
import stroom.util.NullSafe;
import stroom.util.json.JsonUtil;
import stroom.util.shared.string.CIKey;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VisualisationTokenConsumerImpl implements VisualisationTokenConsumer {

    private final DocResolver docResolver;
    private final VisualisationStore visualisationStore;

    @Inject
    public VisualisationTokenConsumerImpl(final DocResolver docResolver,
                                          final VisualisationStore visualisationStore) {
        this.docResolver = docResolver;
        this.visualisationStore = visualisationStore;
    }

    @Override
    public TableSettings processVis(final KeywordGroup keywordGroup,
                                    final TableSettings parentTableSettings) {
        Objects.requireNonNull(parentTableSettings, "Null parent table settings");

        final VisualisationDoc visualisationDoc;
        final VisSettings visSettings;
        final Controls controls;
        final Map<String, String> visParameters;
        final List<AbstractToken> children = keywordGroup.getChildren();

        int childIndex = 0;

        // Ignore AS due to deprecation.
        if (!children.isEmpty()) {
            if (TokenType.AS.equals(children.get(childIndex).getTokenType())) {
                childIndex++;
            }
        }

        // Get visualisation by name.
        final String visName;
        if (children.size() > childIndex) {
            final AbstractToken token = children.get(childIndex);
            if (TokenType.isString(token)) {
                visName = token.getUnescapedText();
                visualisationDoc = loadVisualisation(token, visName);
                childIndex++;

                // Get the vis structure.
                if (!NullSafe.isBlankString(visualisationDoc.getSettings())) {
                    visSettings = JsonUtil.readValue(visualisationDoc.getSettings(), VisSettings.class);
                } else {
                    visSettings = new VisSettings();
                }
                controls = new Controls(visSettings);

                // Get visualisation parameters.
                if (children.size() > childIndex) {
                    final AbstractToken paramsToken = children.get(childIndex);
                    if (TokenType.TOKEN_GROUP.equals(paramsToken.getTokenType())) {
                        final TokenGroup tokenGroup = (TokenGroup) paramsToken;
                        visParameters = getVisParameters(
                                tokenGroup.getChildren(),
                                visName,
                                controls,
                                parentTableSettings);
                        childIndex++;
                    } else {
                        throw new TokenException(paramsToken, "Expected visualisation parameters in brackets");
                    }
                } else {
                    throw new TokenException(keywordGroup, "Expected visualisation parameters in brackets");
                }

            } else if (TokenType.FUNCTION_GROUP.equals(token.getTokenType())) {
                final FunctionGroup functionGroup = (FunctionGroup) token;
                // Be forgiving of missing space and treat resulting function as vis spec.
                visName = functionGroup.getName();
                visualisationDoc = loadVisualisation(token, visName);
                childIndex++;

                // Get the vis structure.
                if (!NullSafe.isBlankString(visualisationDoc.getSettings())) {
                    visSettings = JsonUtil.readValue(visualisationDoc.getSettings(), VisSettings.class);
                } else {
                    visSettings = new VisSettings();
                }
                controls = new Controls(visSettings);

                // Get visualisation parameters.
                visParameters = getVisParameters(
                        functionGroup.getChildren(),
                        visName,
                        controls,
                        parentTableSettings);

            } else {
                throw new TokenException(token, "Expected visualisation name");
            }
        } else {
            throw new TokenException(keywordGroup, "Expected visualisation name");
        }

        // TODO : Add 'AS' naming behaviour, see gh-4150

        // Check we don't have any more tokens.
        if (children.size() > childIndex) {
            throw new TokenException(children.get(childIndex), "Unexpected token");
        }

        // Add final field if we have one.
        return mapVisSettingsToTableSettings(
                visualisationDoc,
                visSettings,
                controls,
                visParameters,
                parentTableSettings);
    }

    private VisualisationDoc loadVisualisation(final AbstractToken token, final String visName) {
        VisualisationDoc visualisationDoc;

        // Load visualisation.
        final DocRef docRef = docResolver.resolveDocRef(VisualisationDoc.DOCUMENT_TYPE, visName);
        try {
            visualisationDoc = visualisationStore.readDocument(docRef);
            if (visualisationDoc == null) {
                throw new TokenException(token,
                        "Unable to load visualisation with name '" + visName + "'");
            }
        } catch (final RuntimeException e) {
            throw new TokenException(token,
                    "Unable to load visualisation with name '" + visName + "', " + e.getMessage());
        }
        return visualisationDoc;
    }

    private Map<String, String> getVisParameters(final List<AbstractToken> children,
                                                 final String visName,
                                                 final Controls controls,
                                                 final TableSettings parentTableSettings) {
        final Map<CIKey, Column> columnMap = NullSafe.stream(parentTableSettings.getColumns())
                .collect(Collectors.toMap(Column::getNameAsCIKey, Function.identity()));

        final Map<String, String> params = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            AbstractToken t = children.get(i);
            String controlId;
            String controlValue;

            // Get param name.
            if (!TokenType.isString(t)) {
                throw new TokenException(t, "Expected string token");
            }
            controlId = t.getUnescapedText();
            if (params.containsKey(controlId)) {
                throw new TokenException(t, "Duplicate parameter found");
            }

            // Validate that the vis parameter is expected.
            final Control control = controls.getControl(controlId);
            if (control == null) {
                throw new TokenException(t,
                        "Unknown visualisation control id '" +
                                controlId +
                                "' found for '" +
                                visName +
                                "'");
            }

            // Get equals.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (!TokenType.EQUALS.equals(t.getTokenType())) {
                    throw new TokenException(t, "Expected equals");
                }
            } else {
                throw new TokenException(t, "Expected equals");
            }

            // Get column.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (TokenType.STRING.equals(t.getTokenType()) || TokenType.PARAM.equals(t.getTokenType())) {
                    final String columnName = t.getUnescapedText();

                    // Validate the column name.
                    final Column column = columnMap.get(CIKey.of(columnName));
                    if (column == null) {
                        throw new TokenException(t, "Unable to find selected column: " + columnName);
                    }

                    controlValue = columnName;

                } else if (TokenType.isString(t) || TokenType.NUMBER.equals(t.getTokenType())) {
                    controlValue = t.getUnescapedText();

                } else {
                    throw new TokenException(t, "Expected column name or value");
                }
            } else {
                throw new TokenException(t, "Expected column name or value");
            }

            // Strip comma if there is one.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (!TokenType.COMMA.equals(t.getTokenType())) {
                    throw new TokenException(t, "Expected comma");
                }
            }

            // Need to use resolvedControlId as that is from the will be the correct case
            // as controlId came from the user so could be any case
            final String resolvedControlId = control.getId();
            if (resolvedControlId != null && controlValue != null) {
                params.put(resolvedControlId, controlValue);
            }
        }
        return params;
    }

    private stroom.query.api.v2.TableSettings mapVisSettingsToTableSettings(
            final VisualisationDoc visualisation,
            final VisSettings visSettings,
            final Controls controls,
            final Map<String, String> params,
            final TableSettings parentTableSettings) {

        final Structure structure = Optional
                .ofNullable(visSettings.getData())
                .map(Data::getStructure)
                .orElseGet(Structure::new);

        final SettingResolver settingResolver = new SettingResolver(controls, params);
        final Map<String, Format> formatMap = new HashMap<>();
        if (parentTableSettings.getColumns() != null) {
            for (final Column column : parentTableSettings.getColumns()) {
                if (column != null) {
                    formatMap.put(column.getName(), column.getFormat());
                }
            }
        }

        List<Column> columns = new ArrayList<>();
        List<Long> limits = new ArrayList<>();

        VisNest nest = mapNest(structure.getNest(), settingResolver);
        VisValues values = mapVisValues(structure.getValues(), settingResolver);

        int group = 0;
        while (nest != null) {
            final Column.Builder builder = convertField(nest.getKey(), formatMap);
            builder.group(group++);

            columns.add(builder.build());

            // Get limit from nest.
            Long limit = null;
            if (nest.getLimit() != null) {
                limit = nest.getLimit().getSize();
            }
            limits.add(limit);

            values = nest.getValues();
            nest = nest.getNest();
        }

        if (values != null) {
            // Get limit from values.
            Long limit = Long.MAX_VALUE;
            if (values.getLimit() != null) {
                limit = values.getLimit().getSize();
            }
            limits.add(limit);

            if (values.getFields() != null) {
                for (final VisField visField : values.getFields()) {
                    columns.add(convertField(visField, formatMap).build());
                }
            }
        }

        return TableSettings.builder()
                .addColumns(columns)
                .addMaxResults(limits)
                .showDetail(true)
                .visSettings(createVisSettings(visualisation, params))
                .build();
    }

    private QLVisSettings createVisSettings(final VisualisationDoc visualisation,
                                            final Map<String, String> params) {
        final String json = JsonUtil.writeValueAsString(params);
        return new QLVisSettings(DocRefUtil.create(visualisation), json);
    }

    private Column.Builder convertField(final VisField visField,
                                        final Map<String, stroom.query.api.v2.Format> formatMap) {
        final Column.Builder builder = Column.builder();

        builder.format(Format.GENERAL);

        if (visField.getId() != null) {
            final stroom.query.api.v2.Format format = formatMap.get(visField.getId());
            if (format != null) {
                builder.format(format);
            }

            builder.expression("${" + visField.getId() + "}");
        }
        builder.sort(visField.getSort());

        return builder;
    }


    private VisNest mapNest(final Nest nest, final SettingResolver settingResolver) {
        if (nest == null) {
            return null;
        }

        final VisNest copy = new VisNest();
        copy.setKey(mapVisField(nest.getKey(), settingResolver));
        copy.setNest(mapNest(nest.getNest(), settingResolver));
        copy.setValues(mapVisValues(nest.getValues(), settingResolver));
        copy.setLimit(mapVisLimit(nest.getLimit(), settingResolver));

        return copy;
    }

    private VisField mapVisField(final VisSettings.Field field, final SettingResolver settingResolver) {
        if (field == null) {
            return null;
        }

        final VisField copy = new VisField();
        copy.setId(settingResolver.resolveField(field.getId()));
        copy.setSort(mapVisSort(field.getSort(), settingResolver));

        return copy;
    }

    private stroom.query.api.v2.Sort mapVisSort(final VisSettings.Sort sort, final SettingResolver settingResolver) {
        if (sort == null) {
            return null;
        }

        Boolean enabled = settingResolver.resolveBoolean(sort.getEnabled());
        if (enabled != null && enabled) {
            String dir = settingResolver.resolveString(sort.getDirection());

            if (dir != null) {
                final SortDirection direction;
                if (dir.equalsIgnoreCase(SortDirection.ASCENDING.getDisplayValue())) {
                    direction = SortDirection.ASCENDING;
                } else if (dir.equalsIgnoreCase(SortDirection.DESCENDING.getDisplayValue())) {
                    direction = SortDirection.DESCENDING;
                } else {
                    return null;
                }
                return new stroom.query.api.v2.Sort(settingResolver.resolveInteger(sort.getPriority()), direction);
            }
        }
        return null;
    }

    private VisValues mapVisValues(final VisSettings.Values values, final SettingResolver settingResolver) {
        if (values == null) {
            return null;
        }

        VisField[] fields = null;
        if (values.getFields() != null) {
            fields = new VisField[values.getFields().length];
            for (int i = 0; i < values.getFields().length; i++) {
                fields[i] = mapVisField(values.getFields()[i], settingResolver);
            }
        }

        final VisValues copy = new VisValues();
        copy.setFields(fields);
        copy.setLimit(mapVisLimit(values.getLimit(), settingResolver));

        return copy;
    }

    private VisLimit mapVisLimit(final VisSettings.Limit limit, final SettingResolver settingResolver) {
        if (limit != null) {
            Boolean enabled = settingResolver.resolveBoolean(limit.getEnabled());
            if (enabled == null || enabled) {
                final VisLimit copy = new VisLimit();
                copy.setSize(settingResolver.resolveLong(limit.getSize()));
                return copy;
            }
        }

        return null;
    }


    // --------------------------------------------------------------------------------


    private static class Controls {

        // column id => control
        private final Map<CIKey, Control> controlsById = new HashMap<>();

        public Controls(final VisSettings visSettings) {
            // Create a map of controls.
            if (visSettings.getTabs() != null) {
                for (final Tab tab : visSettings.getTabs()) {
                    if (tab.getControls() != null) {
                        for (final Control control : tab.getControls()) {
                            NullSafe.consume(control, Control::getId, id ->
                                    controlsById.put(CIKey.of(id), control));
                        }
                    }
                }
            }
        }

        public Control getControl(final String controlId) {
            return controlsById.get(CIKey.of(controlId));
        }
    }


    // --------------------------------------------------------------------------------


    private static class SettingResolver {

        private final Controls controls;
        private final Map<String, String> dashboardSettings;

        public SettingResolver(final Controls controls,
                               final Map<String, String> dashboardSettings) {
            this.controls = controls;
            this.dashboardSettings = dashboardSettings;
        }

        public String resolveField(final String value) {
            if (value == null) {
                throw new RuntimeException("Structure element with missing field name");
            }

            return resolveString(value);
        }

        public String resolveString(final String value) {
            String val = value;
            if (val != null) {
                if (isReference(val)) {
                    final String controlId = getReference(val);
                    val = dashboardSettings.get(controlId);

                    if (val == null) {
                        final Control control = controls.getControl(controlId);
                        if (control != null) {
                            val = control.getDefaultValue();
                        }
                    }
                }
            }

            return val;
        }

        public Boolean resolveBoolean(final String value) {
            String str = resolveString(value);
            if (str == null) {
                return null;
            }
            return Boolean.valueOf(str);
        }

        public Integer resolveInteger(final String value) {
            String str = resolveString(value);
            if (str == null) {
                return null;
            }
            return Integer.valueOf(str);
        }

        public Long resolveLong(final String value) {
            String str = resolveString(value);
            if (str == null) {
                return null;
            }
            return Long.valueOf(str);
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
    }
}
