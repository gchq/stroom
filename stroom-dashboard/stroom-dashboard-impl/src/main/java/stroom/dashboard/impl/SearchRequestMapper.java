/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.impl;

import stroom.dashboard.impl.vis.VisSettings;
import stroom.dashboard.impl.vis.VisSettings.Control;
import stroom.dashboard.impl.vis.VisSettings.Nest;
import stroom.dashboard.impl.vis.VisSettings.Structure;
import stroom.dashboard.impl.vis.VisSettings.Tab;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.GroupSelection;
import stroom.query.api.Param;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Builder;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableSettings;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class SearchRequestMapper {

    public static final String EXPRESSION_JSON_PARAM_KEY = "expressionJson";

    private final VisualisationStore visualisationStore;

    @Inject
    public SearchRequestMapper(final VisualisationStore visualisationStore) {
        this.visualisationStore = visualisationStore;
    }

    public SearchRequest mapRequest(final DashboardSearchRequest searchRequest) {
        if (searchRequest == null) {
            return null;
        }

        return SearchRequest.builder()
                .searchRequestSource(searchRequest.getSearchRequestSource())
                .key(searchRequest.getQueryKey())
                .query(mapQuery(searchRequest))
                .resultRequests(mapResultRequests(searchRequest))
                .dateTimeSettings(searchRequest.getDateTimeSettings())
                .incremental(searchRequest.getSearch().isIncremental())
                .timeout(searchRequest.getTimeout())
                .build();
    }

    private Query mapQuery(final DashboardSearchRequest searchRequest) {
        if (searchRequest.getSearch() == null) {
            return null;
        }

        Param searchExpressionParam = null;
        List<Param> params = null;
        if (searchRequest.getSearch().getParams() != null && searchRequest.getSearch().getParams().size() > 0) {
            params = new ArrayList<>(searchRequest.getSearch().getParams().size());
            for (final Param param : searchRequest.getSearch().getParams()) {
                if (EXPRESSION_JSON_PARAM_KEY.equals(param.getKey())) {
                    searchExpressionParam = param;
                } else {
                    params.add(param);
                }
            }
        }

        if (searchExpressionParam != null) {
            String expressionJson = "null";
            try {
                expressionJson = StringEscapeUtils.unescapeXml(searchExpressionParam.getValue());
                final ExpressionOperator suppliedExpression = JsonUtil.readValue(expressionJson,
                        ExpressionOperator.class);
                final ExpressionOperator expression = ExpressionOperator
                        .builder()
                        .addOperator(searchRequest.getSearch().getExpression())
                        .addOperator(suppliedExpression)
                        .build();
                return new Query(
                        searchRequest.getSearch().getDataSourceRef(),
                        expression,
                        params,
                        searchRequest.getSearch().getTimeRange());

            } catch (final RuntimeException ex) {
                throw new RuntimeException("Invalid JSON for expression.  Got: " + expressionJson, ex);
            }

        } else {
            return new Query(searchRequest.getSearch().getDataSourceRef(),
                    searchRequest.getSearch().getExpression(),
                    params,
                    searchRequest.getSearch().getTimeRange());
        }
    }

    private List<ResultRequest> mapResultRequests(final DashboardSearchRequest searchRequest) {
        if (searchRequest.getComponentResultRequests() == null
                || searchRequest.getComponentResultRequests().size() == 0) {
            return null;
        }

        final List<ResultRequest> resultRequests = new ArrayList<>(searchRequest.getComponentResultRequests().size());
        for (final ComponentResultRequest componentResultRequest : searchRequest.getComponentResultRequests()) {
            if (componentResultRequest instanceof final TableResultRequest tableResultRequest) {

                final GroupSelection groupSelection = Optional.ofNullable(tableResultRequest.getGroupSelection())
                        .orElse(GroupSelection.builder().openGroups(tableResultRequest.getOpenGroups()).build());

                final ResultRequest copy = ResultRequest.builder()
                        .componentId(tableResultRequest.getComponentId())
                        .searchRequestSource(searchRequest.getSearchRequestSource())
                        .tableName(tableResultRequest.getTableName())
                        .addMappings(tableResultRequest.getTableSettings())
                        .requestedRange(tableResultRequest.getRequestedRange())
                        .resultStyle(ResultStyle.TABLE)
                        .fetch(tableResultRequest.getFetch())
                        .groupSelection(groupSelection)
                        .build();
                resultRequests.add(copy);

            } else if (componentResultRequest instanceof final VisResultRequest visResultRequest) {
                final TableSettings parentTableSettings = visResultRequest.getTableSettings();
                final TableSettings childTableSettings = mapVisSettingsToTableSettings(
                        visResultRequest, parentTableSettings);

                final Builder builder = ResultRequest.builder()
                        .componentId(visResultRequest.getComponentId())
                        .addMappings(parentTableSettings);

                if (childTableSettings != null) {
                    // e.g. the vis has not been specified, i.e. user just added a vis pane and did nothing to it
                    builder.addMappings(childTableSettings);
                }

                final ResultRequest copy = builder
                        .requestedRange(visResultRequest.getRequestedRange())
                        .resultStyle(ResultStyle.VIS)
                        .fetch(visResultRequest.getFetch())
                        .build();
                resultRequests.add(copy);

//
//
//
//
//
//
//
//
//
//                final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;
//                final stroom.query.api.VisResultRequest copy = new stroom.query.api.VisResultRequest();
//                copy.setComponentId(componentId);
//                copy.setFetchData(visResultRequest.wantsData());
//                copy.setMappings(mapTableSettings(visResultRequest.getVisDashboardSettings().getMappings()));
//                copy.setStructure(mapStructure(visResultRequest.getVisDashboardSettings()));
//                resultRequests[i++] = copy;
            }
        }

        return resultRequests;
    }

//    private TableSettings mapTableSettings(final TableComponentSettings tableComponentSettings) {
//        if (tableComponentSettings == null) {
//            return null;
//        }
//
//        return tableComponentSettings;
//
////        final TableSettings tableSettings = TableSettings.builder()
////                .queryId(tableComponentSettings.getQueryId())
////                .addFields(mapFields(tableComponentSettings.getFields()))
////                .extractValues(tableComponentSettings.extractValues())
////                .extractionPipeline(tableComponentSettings.getExtractionPipeline())
////                .addMaxResults(mapIntArray(tableComponentSettings.getMaxResults()))
////                .showDetail(tableComponentSettings.getShowDetail())
////                .build();
////
////        return tableSettings;
//    }

//    private List<Field> mapFields(final List<Field> fields) {
//        if (fields == null || fields.size() == 0) {
//            return Collections.emptyList();
//        }
//
//        final List<Field> list = new ArrayList<>(fields.size());
//        for (final Field field : fields) {
//            final stroom.query.api.Field.Builder builder = new stroom.query.api.Field.Builder()
//                    .id(field.getId())
//                    .name(field.getName())
//                    .expression(field.getExpression())
//                    .sort(mapSort(field.getSort()))
//                    .filter(mapFilter(field.getFilter()))
//                    .format(mapFormat(field.getFormat()))
//                    .group(field.getGroup());
//
//            list.add(builder.build());
//        }
//
//        return list;
//    }
//
//    private List<Integer> mapIntArray(final int[] arr) {
//        if (arr == null || arr.length == 0) {
//            return null;
//        }
//
//        final List<Integer> copy = new ArrayList<>(arr.length);
//        for (int i = 0; i < arr.length; i++) {
//            copy.add(arr[i]);
//        }
//
//        return copy;
//    }
//
//    private <T> List<T> mapCollection(final Class<T> clazz, final Collection<T> collection) {
//        if (collection == null || collection.size() == 0) {
//            return null;
//        }
//
//        @SuppressWarnings("unchecked")
//        List<T> copy = new ArrayList<>(collection.size());
//        int i = 0;
//        for (final T t : collection) {
//            copy.add(t);
//        }
//
//        return copy;
//    }
//
//    private stroom.query.api.OffsetRange mapOffsetRange(final OffsetRange<Integer> offsetRange) {
//        if (offsetRange == null) {
//            return null;
//        }
//
//        return new stroom.query.api.OffsetRange(offsetRange.getOffset(), offsetRange.getLength());
//    }
//
//    private stroom.query.api.Sort mapSort(final Sort sort) {
//        if (sort == null) {
//            return null;
//        }
//
//        SortDirection sortDirection = null;
//        if (sort.getDirection() != null) {
//            sortDirection = SortDirection.valueOf(sort.getDirection().name());
//        }
//
//        return new stroom.query.api.Sort(sort.getOrder(), sortDirection);
//    }
//
//    private stroom.query.api.Filter mapFilter(final Filter filter) {
//        if (filter == null) {
//            return null;
//        }
//
//        return new stroom.query.api.Filter(filter.getIncludes(), filter.getExcludes());
//    }
//
//    private stroom.query.api.Format mapFormat(final Format format) {
//        if (format == null) {
//            return null;
//        }
//
//        Type type = null;
//
//        if (format.getType() != null) {
//            type = Type.valueOf(format.getType().name());
//        }
//
//        return new stroom.query.api.Format(type, mapNumberFormat(
//        format.getSettings()), mapDateTimeFormat(format.getSettings()));
//    }
//
//    private NumberFormatSettings mapNumberFormat(final FormatSettings formatSettings) {
//        if (formatSettings == null || !(formatSettings instanceof stroom.dashboard.shared.NumberFormatSettings)) {
//            return null;
//        }
//
//        final stroom.dashboard.shared.NumberFormatSettings numberFormatSettings =
//        (stroom.dashboard.shared.NumberFormatSettings) formatSettings;
//
//        return new NumberFormatSettings(
//        numberFormatSettings.getDecimalPlaces(), numberFormatSettings.getUseSeparator());
//    }
//
//    private DateTimeFormatSettings mapDateTimeFormat(final FormatSettings formatSettings) {
//        if (formatSettings == null || !(formatSettings instanceof stroom.dashboard.shared.DateTimeFormatSettings)) {
//            return null;
//        }
//
//        final stroom.dashboard.shared.DateTimeFormatSettings dateTimeFormatSettings =
//        (stroom.dashboard.shared.DateTimeFormatSettings) formatSettings;
//
//        return new DateTimeFormatSettings(
//        dateTimeFormatSettings.getPattern(), mapTimeZone(dateTimeFormatSettings.getTimeZone()));
//    }
//
//    private stroom.expression.api.TimeZone mapTimeZone(final TimeZone timeZone) {
//        if (timeZone == null) {
//            return null;
//        }
//
//        return new stroom.expression.api.TimeZone(
//        Use.valueOf(timeZone.getUse().name()), timeZone.getId(), timeZone.getOffsetHours(),
//        timeZone.getOffsetMinutes());
//    }

//    private TableSettings visStructureToTableSettings(
//    final VisStructure visStructure, final TableSettings parentTableSettings) {
//        final Map<String, stroom.query.api.Format> formatMap = new HashMap<>();
//        if (parentTableSettings.getFields() != null) {
//            for (final stroom.query.api.Field field : parentTableSettings.getFields()) {
//                if (field != null) {
//                    formatMap.put(field.getName(), field.getFormat());
//                }
//            }
//        }
//
//        List<stroom.query.api.Field> fields = new ArrayList<>();
//        List<Integer> limits = new ArrayList<>();
//
//        VisNest nest = visStructure.getNest();
//        VisValues values = visStructure.getValues();
//
//        int group = 0;
//        while (nest != null) {
//            stroom.query.api.Field field = convertField(nest.getKey(), formatMap);
//            field.setGroup(group++);
//
//            fields.add(field);
//
//            // Get limit from nest.
//            Integer limit = null;
//            if (nest.getLimit() != null) {
//                limit = nest.getLimit().getSize();
//            }
//            limits.add(limit);
//
//            values = nest.getValues();
//            nest = nest.getNest();
//        }
//
//        if (values != null) {
//            // Get limit from values.
//            Integer limit = null;
//            if (values.getLimit() != null) {
//                limit = values.getLimit().getSize();
//            }
//            limits.add(limit);
//
//            if (values.getFields() != null) {
//                for (final VisField visField : values.getFields()) {
//                    fields.add(convertField(visField, formatMap));
//                }
//            }
//        }
//
//        final TableSettings tableSettings = new TableSettings();
//        tableSettings.setFields(fields.toArray(new stroom.query.api.Field[0]));
//        tableSettings.setMaxResults(limits.toArray(new Integer[0]));
//        tableSettings.setShowDetail(true);
//
//        return tableSettings;
//    }

    private Column.Builder convertField(final VisField visField,
                                        final Map<String, stroom.query.api.Format> formatMap) {
        final Column.Builder builder = Column.builder();

        builder.format(Format.GENERAL);

        if (visField.getId() != null) {
            final stroom.query.api.Format format = formatMap.get(visField.getId());
            if (format != null) {
                builder.format(format);
            }

            builder.expression(ParamUtil.create(visField.getId()));
        }
        builder.sort(visField.getSort());

        return builder;
    }


    private stroom.query.api.TableSettings mapVisSettingsToTableSettings(
            final VisResultRequest visResultRequest,
            final TableSettings parentTableSettings) {

        final DocRef docRef = visResultRequest.getVisualisation();
        TableSettings tableSettings = null;

        if (docRef == null) {
            return null;
        }

        final VisualisationDoc visualisation = visualisationStore.readDocument(docRef);

        if (visualisation == null
                || visualisation.getSettings() == null
                || visualisation.getSettings().length() == 0) {
            return null;
        }

        final VisSettings visSettings = JsonUtil.readValue(visualisation.getSettings(), VisSettings.class);
        if (visSettings != null && visSettings.getData() != null) {
            final SettingResolver settingResolver = new SettingResolver(visSettings,
                    visResultRequest.getJson());
            final Structure structure = visSettings.getData().getStructure();
            if (structure != null) {

                final Map<String, stroom.query.api.Format> formatMap = new HashMap<>();
                if (parentTableSettings.getColumns() != null) {
                    for (final Column column : parentTableSettings.getColumns()) {
                        if (column != null) {
                            formatMap.put(column.getName(), column.getFormat());
                        }
                    }
                }

                final List<Column> columns = new ArrayList<>();
                final List<Long> limits = new ArrayList<>();

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
                        .build();
            }
        }

        return tableSettings;
    }


//    private VisStructure mapStructure(final VisComponentSettings visComponentSettings) {
//        DocRef docRef = visComponentSettings.getVisualisation();
//        VisStructure copy = null;
//
//        try {
//            if (docRef == null) {
//                return null;
//            }
//
//            final Visualisation visualisation = visualisationStore.loadByUuid(docRef.getUuid());
//
//            if (visualisation == null
//            || visualisation.getSettings() == null || visualisation.getSettings().length() == 0) {
//                return null;
//            }
//
//            final VisSettings visSettings = mapper.readValue(visualisation.getSettings(), VisSettings.class);
//            if (visSettings != null && visSettings.getMeta() != null) {
//                final SettingResolver settingResolver = new SettingResolver(
//                visSettings, visComponentSettings.getJSON());
//                final Structure structure = visSettings.getMeta().getStructure();
//                if (structure != null) {
//                    copy = new VisStructure();
//                    copy.setNest(mapNest(structure.getNest(), settingResolver));
//                    copy.setValues(mapVisValues(structure.getValues(), settingResolver));
//                }
//            }
//
//        } catch (final IOException e) {
//            throw new RuntimeException(e.getMessage(), e);
//        }
//
//        return copy;
//    }

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

    private stroom.query.api.Sort mapVisSort(final VisSettings.Sort sort, final SettingResolver settingResolver) {
        if (sort == null) {
            return null;
        }

        final Boolean enabled = settingResolver.resolveBoolean(sort.getEnabled());
        if (enabled != null && enabled) {
            final String dir = settingResolver.resolveString(sort.getDirection());

            if (dir != null) {
                final SortDirection direction;
                if (dir.equalsIgnoreCase(SortDirection.ASCENDING.getDisplayValue())) {
                    direction = SortDirection.ASCENDING;
                } else if (dir.equalsIgnoreCase(SortDirection.DESCENDING.getDisplayValue())) {
                    direction = SortDirection.DESCENDING;
                } else {
                    return null;
                }
                return new stroom.query.api.Sort(settingResolver.resolveInteger(sort.getPriority()), direction);
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
            final Boolean enabled = settingResolver.resolveBoolean(limit.getEnabled());
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

        public SettingResolver(final VisSettings visSettings, final String json) {
            dashboardSettings = getDashboardSettingsMap(json);

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
            final String str = resolveString(value);
            if (str == null) {
                return null;
            }
            return Boolean.valueOf(str);
        }

        public Integer resolveInteger(final String value) {
            final String str = resolveString(value);
            if (str == null) {
                return null;
            }
            return Integer.valueOf(str);
        }

        public Long resolveLong(final String value) {
            final String str = resolveString(value);
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

        private Map<String, String> getDashboardSettingsMap(final String json) {
            final Map<String, String> map = new HashMap<>();

            try {
                if (json != null && !json.isEmpty()) {
                    final ObjectMapper objectMapper = JsonUtil.getNoIndentMapper();
                    final JsonNode node = objectMapper.readTree(json);

                    final Iterator<Entry<String, JsonNode>> iterator = node.fields();
                    while (iterator.hasNext()) {
                        final Entry<String, JsonNode> entry = iterator.next();
                        final JsonNode val = entry.getValue();
                        if (val != null) {
                            final String str = val.textValue();
                            if (str != null) {
                                map.put(entry.getKey(), str);
                            }
                        }
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

            return map;
        }
    }
}
