package stroom.dashboard.impl.visualisation;

import stroom.dashboard.impl.VisField;
import stroom.dashboard.impl.VisLimit;
import stroom.dashboard.impl.VisNest;
import stroom.dashboard.impl.VisValues;
import stroom.dashboard.impl.vis.VisSettings;
import stroom.dashboard.impl.vis.VisSettings.Control;
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
import stroom.query.language.token.KeywordGroup;
import stroom.query.language.token.TokenException;
import stroom.query.language.token.TokenGroup;
import stroom.query.language.token.TokenType;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        final VisualisationDoc visualisationDoc;
        Map<String, String> params = Collections.emptyMap();

        final List<AbstractToken> children = keywordGroup.getChildren();

        // Get AS.
        if (children.size() > 0) {
            if (!TokenType.AS.equals(children.get(0).getTokenType())) {
                throw new TokenException(children.get(0), "Expected AS");
            }
        } else {
            throw new TokenException(keywordGroup, "Expected AS");
        }

        // Get visualisation by name.
        if (children.size() > 1) {
            final AbstractToken token = children.get(1);
            if (TokenType.isString(token)) {
                final String visName = token.getUnescapedText();
                visualisationDoc = loadVisualisation(token, visName);
            } else {
                throw new TokenException(token, "Expected visualisation name");
            }
        } else {
            throw new TokenException(keywordGroup, "Expected visualisation name");
        }

        // Get visualisation parameters.
        if (children.size() > 2) {
            final AbstractToken token = children.get(2);
            if (TokenType.TOKEN_GROUP.equals(token.getTokenType())) {
                final TokenGroup tokenGroup = (TokenGroup) token;
                params = getParams(tokenGroup.getChildren());
            } else {
                throw new TokenException(token, "Expected visualisation parameters in brackets");
            }
        }

        // Check we don't have more than 3 tokens.
        if (children.size() > 3) {
            throw new TokenException(children.get(3), "Unexpected token");
        }

        // Add final field if we have one.
        return mapVisSettingsToTableSettings(visualisationDoc, params, parentTableSettings);
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

    private Map<String, String> getParams(final List<AbstractToken> children) {
        final Map<String, String> params = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            AbstractToken t = children.get(i);
            String paramName = null;
            String fieldName = null;

            // Get param name.
            if (!TokenType.isString(t)) {
                throw new TokenException(t, "Expected string token");
            }
            paramName = t.getUnescapedText();
            if (params.containsKey(paramName)) {
                throw new TokenException(t, "Duplicate parameter found");
            }

            // Get equals.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (!TokenType.EQUALS.equals(t.getTokenType())) {
                    throw new TokenException(t, "Expected equals");
                }
            }

            // Get field.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (!TokenType.isString(t)) {
                    throw new TokenException(t, "Expected string token");
                }
                fieldName = t.getUnescapedText();
            }

            // Strip comma if there is one.
            i++;
            if (i < children.size()) {
                t = children.get(i);
                if (!TokenType.COMMA.equals(t.getTokenType())) {
                    throw new TokenException(t, "Expected comma");
                }
            }

            if (paramName != null && fieldName != null) {
                params.put(paramName, fieldName);
            }
        }
        return params;
    }

    private stroom.query.api.v2.TableSettings mapVisSettingsToTableSettings(
            final VisualisationDoc visualisation,
            final Map<String, String> params,
            final TableSettings parentTableSettings) {

        TableSettings tableSettings = null;

        if (visualisation == null
                || visualisation.getSettings() == null
                || visualisation.getSettings().length() == 0) {
            return null;
        }

        final VisSettings visSettings = JsonUtil.readValue(visualisation.getSettings(), VisSettings.class);
        if (visSettings != null && visSettings.getData() != null) {
            final SettingResolver settingResolver = new SettingResolver(visSettings, params);
            final Structure structure = visSettings.getData().getStructure();
            if (structure != null) {

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

                tableSettings = TableSettings.builder()
                        .addColumns(columns)
                        .addMaxResults(limits)
                        .showDetail(true)
                        .visSettings(createVisSettings(visualisation, params))
                        .build();
            }
        }


        return tableSettings;
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

    private static class SettingResolver {

        private final Map<String, Control> controls = new HashMap<>();
        private final Map<String, String> dashboardSettings;

        public SettingResolver(final VisSettings visSettings,
                               final Map<String, String> dashboardSettings) {
            this.dashboardSettings = dashboardSettings;

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
                        final Control control = controls.get(controlId);
                        if (control != null) {
                            val = control.getDefaultValue();
                        } else {
//                            throw new RuntimeException("No control found with id = '" + controlId + "'");
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

//        private Map<String, String> getDashboardSettingsMap(final String json) {
//            Map<String, String> map = new HashMap<>();
//
//            try {
//                if (json != null && !json.isEmpty()) {
//                    ObjectMapper objectMapper = JsonUtil.getNoIndentMapper();
//                    final JsonNode node = objectMapper.readTree(json);
//
//                    Iterator<Entry<String, JsonNode>> iterator = node.fields();
//                    while (iterator.hasNext()) {
//                        Entry<String, JsonNode> entry = iterator.next();
//                        JsonNode val = entry.getValue();
//                        if (val != null) {
//                            final String str = val.textValue();
//                            if (str != null) {
//                                map.put(entry.getKey(), str);
//                            }
//                        }
//                    }
//                }
//            } catch (final IOException e) {
//                throw new UncheckedIOException(e);
//            }
//
//            return map;
//        }
    }

}
