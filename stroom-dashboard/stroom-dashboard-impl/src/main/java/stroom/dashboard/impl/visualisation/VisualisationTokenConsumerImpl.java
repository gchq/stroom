/*
 * Copyright 2023 Crown Copyright
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

import stroom.dashboard.impl.vis.VisSettings;
import stroom.dashboard.impl.vis.VisSettings.Control;
import stroom.dashboard.impl.vis.VisSettings.Tab;
import stroom.dashboard.impl.vis.VisStructureMapper;
import stroom.dashboard.impl.vis.VisStructureMapper.VisTable;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.QLVisSettings;
import stroom.query.api.TableSettings;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.VisualisationTokenConsumer;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisInput;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VisualisationTokenConsumerImpl implements VisualisationTokenConsumer {

    private final DataSourceResolver dataSourceResolver;
    private final VisualisationStore visualisationStore;

    @Inject
    public VisualisationTokenConsumerImpl(final DataSourceResolver dataSourceResolver,
                                          final VisualisationStore visualisationStore) {
        this.dataSourceResolver = dataSourceResolver;
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
                        checkRequiredInputs(paramsToken, visName, controls, visParameters);
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
                checkRequiredInputs(functionGroup, visName, controls, visParameters);

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
                visParameters,
                parentTableSettings);
    }

    private VisualisationDoc loadVisualisation(final AbstractToken token, final String visName) {
        final VisualisationDoc visualisationDoc;

        // Load visualisation.
        final DocRef docRef = dataSourceResolver.findVisualisationDoc(visName);
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
        final Map<String, Column> columnMap = NullSafe
                .list(parentTableSettings
                        .getColumns())
                .stream()
                .collect(Collectors.toMap(Column::getName, Function.identity()));

        final Map<String, String> params = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            AbstractToken t = children.get(i);
            final String controlId;
            final String controlValue;

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
                    final Column column = columnMap.get(columnName);
                    if (column == null) {
                        throw new TokenException(t, "Unable to find selected column: " + columnName);
                    }

                    // And that the column's type is one the visualisation's input takes
                    final VisInput input = controls.getInput(controlId);
                    final Format.Type columnType = NullSafe.get(column.getFormat(), Format::getType);
                    if (input != null && !input.accepts(columnType)) {
                        throw new TokenException(t, "Column '" + columnName + "' is "
                                                    + columnType.getDisplayValue().toLowerCase(Locale.ROOT)
                                                    + " but '" + inputName(input) + "' of '" + visName
                                                    + "' takes " + typeNames(input));
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

            if (controlId != null && controlValue != null) {
                params.put(controlId, controlValue);
            }
        }
        return params;
    }

    // Checks that every input the visualisation needs has a column, given or by default
    private static void checkRequiredInputs(final AbstractToken token,
                                            final String visName,
                                            final Controls controls,
                                            final Map<String, String> params) {
        for (final VisInput input : controls.getInputs()) {
            if (input.isRequired() && NullSafe.isBlankString(params.get(input.getId()))) {
                final Control control = controls.getControl(input.getId());
                if (control == null || NullSafe.isBlankString(control.getDefaultValue())) {
                    throw new TokenException(token, "'" + visName + "' needs a column for '" + inputName(input)
                                                    + "' (" + input.getId() + ")");
                }
            }
        }
    }

    private static String inputName(final VisInput input) {
        return NullSafe.isBlankString(input.getLabel())
                ? input.getId()
                : input.getLabel();
    }

    // The types an input takes, for people to read, e.g. 'numbers or dates'
    private static String typeNames(final VisInput input) {
        return input.getInputTypes()
                .stream()
                .map(type -> switch (type) {
                    case TEXT -> "text";
                    case NUMBER -> "numbers";
                    case DATE_TIME -> "dates";
                })
                .collect(Collectors.joining(" or "));
    }

    private TableSettings mapVisSettingsToTableSettings(final VisualisationDoc visualisation,
                                                        final VisSettings visSettings,
                                                        final Map<String, String> params,
                                                        final TableSettings parentTableSettings) {
        final VisTable visTable = VisStructureMapper.map(visSettings, params, parentTableSettings.getColumns());
        final TableSettings.Builder builder = TableSettings.builder();
        if (visTable != null) {
            builder.addColumns(visTable.columns())
                    .addMaxResults(visTable.maxResults());
        }
        return builder
                .showDetail(true)
                .visSettings(createVisSettings(visualisation, params))
                .build();
    }

    private QLVisSettings createVisSettings(final VisualisationDoc visualisation,
                                            final Map<String, String> params) {
        final String json = JsonUtil.writeValueAsString(params);
        return new QLVisSettings(DocRefUtil.create(visualisation), json);
    }

    private static class Controls {

        private final Map<String, Control> controls = new HashMap<>();
        private final Map<String, VisInput> inputs = new LinkedHashMap<>();

        public Controls(final VisSettings visSettings) {
            // Create a map of controls.
            if (visSettings.getTabs() != null) {
                for (final Tab tab : visSettings.getTabs()) {
                    if (tab.getControls() != null) {
                        for (final Control control : tab.getControls()) {
                            if (control != null && control.getId() != null) {
                                controls.put(control.getId(), control);
                            }
                        }
                    }
                }
            }
            for (final VisInput input : NullSafe.list(visSettings.getInputs())) {
                if (input != null && input.getId() != null) {
                    inputs.put(input.getId(), input);
                }
            }
        }

        public Control getControl(final String controlId) {
            return controls.get(controlId);
        }

        // The input of a field control, if the visualisation declares one
        public VisInput getInput(final String controlId) {
            return inputs.get(controlId);
        }

        public Collection<VisInput> getInputs() {
            return inputs.values();
        }
    }
}
