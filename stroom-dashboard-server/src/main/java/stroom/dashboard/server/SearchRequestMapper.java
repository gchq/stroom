/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.server;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import stroom.dashboard.server.vis.VisSettings;
import stroom.dashboard.server.vis.VisSettings.Control;
import stroom.dashboard.server.vis.VisSettings.Nest;
import stroom.dashboard.server.vis.VisSettings.Structure;
import stroom.dashboard.server.vis.VisSettings.Tab;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Filter;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.NumberFormatSettings;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.TimeZone;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dashboard.shared.VisResultRequest;
import stroom.query.api.DateTimeFormat;
import stroom.query.api.DocRef;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormat;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeZone.Use;
import stroom.util.shared.OffsetRange;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Component
public class SearchRequestMapper {
    private final VisualisationService visualisationService;

    @Inject
    public SearchRequestMapper(final VisualisationService visualisationService) {
        this.visualisationService = visualisationService;
    }

    public stroom.query.api.SearchRequest mapRequest(final DashboardQueryKey queryKey, final stroom.dashboard.shared.SearchRequest searchRequest) {
        if (searchRequest == null) {
            return null;
        }

        final stroom.query.api.SearchRequest copy = new stroom.query.api.SearchRequest();
        copy.setKey(new QueryKey(queryKey.getUuid()));
        copy.setQuery(mapQuery(searchRequest));
        copy.setResultRequests(mapResultRequests(searchRequest));
        copy.setDateTimeLocale(searchRequest.getSearch().getDateTimeLocale());
        copy.setIncremental(searchRequest.getSearch().getIncremental());

        return copy;
    }

    private Query mapQuery(final stroom.dashboard.shared.SearchRequest searchRequest) {
        if (searchRequest.getSearch() == null) {
            return null;
        }

        final Query query = new Query();
        query.setDataSource(searchRequest.getSearch().getDataSourceRef());

        query.setExpression(searchRequest.getSearch().getExpression());

        if (searchRequest.getSearch().getParamMap() != null && searchRequest.getSearch().getParamMap().size() > 0) {
            final List<Param> params = new ArrayList<>(searchRequest.getSearch().getParamMap().size());
            for (final Entry<String, String> entry : searchRequest.getSearch().getParamMap().entrySet()) {
                final Param param = new Param(entry.getKey(), entry.getValue());
                params.add(param);
            }

            query.setParams(params);
        }

        return query;
    }

    private List<ResultRequest> mapResultRequests(final stroom.dashboard.shared.SearchRequest searchRequest) {
        if (searchRequest.getComponentResultRequests() == null || searchRequest.getComponentResultRequests().size() == 0) {
            return null;
        }

        final List<ResultRequest> resultRequests = new ArrayList<>(searchRequest.getComponentResultRequests().size());
        for (final Entry<String, ComponentResultRequest> entry : searchRequest.getComponentResultRequests().entrySet()) {
            final String componentId = entry.getKey();
            final ComponentResultRequest componentResultRequest = entry.getValue();
            if (componentResultRequest instanceof TableResultRequest) {
                final TableResultRequest tableResultRequest = (TableResultRequest) componentResultRequest;
                final stroom.query.api.ResultRequest copy = new stroom.query.api.ResultRequest();
                copy.setComponentId(componentId);
                copy.setFetchData(tableResultRequest.wantsData());
                final TableSettings tableSettings = mapTableSettings(tableResultRequest.getTableSettings());
                copy.setMappings(Collections.singletonList(tableSettings));
                copy.setRequestedRange(mapOffsetRange(tableResultRequest.getRequestedRange()));
                copy.setOpenGroups(mapCollection(String.class, tableResultRequest.getOpenGroups()));
                copy.setResultStyle(ResultStyle.TABLE);
                resultRequests.add(copy);

            } else if (componentResultRequest instanceof VisResultRequest) {
                final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;
                final stroom.query.api.ResultRequest copy = new stroom.query.api.ResultRequest();
                copy.setComponentId(componentId);
                copy.setFetchData(visResultRequest.wantsData());

//                final VisStructure visStructure = mapStructure(visResultRequest.getVisDashboardSettings());

                final TableSettings parentTableSettings = mapTableSettings(visResultRequest.getVisDashboardSettings().getTableSettings());
                final TableSettings childTableSettings = mapVisSettingsToTableSettings(visResultRequest.getVisDashboardSettings(), parentTableSettings);

                copy.setMappings(Arrays.asList(parentTableSettings, childTableSettings));
                copy.setRequestedRange(null);
                copy.setOpenGroups(null);
                copy.setResultStyle(ResultStyle.FLAT);
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

    private TableSettings mapTableSettings(final TableComponentSettings tableComponentSettings) {
        if (tableComponentSettings == null) {
            return null;
        }

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setQueryId(tableComponentSettings.getQueryId());
        tableSettings.setFields(mapFields(tableComponentSettings.getFields()));
        tableSettings.setExtractValues(tableComponentSettings.extractValues());
        tableSettings.setExtractionPipeline(tableComponentSettings.getExtractionPipeline());
        tableSettings.setMaxResults(mapIntArray(tableComponentSettings.getMaxResults()));
        tableSettings.setShowDetail(tableComponentSettings.getShowDetail());

        return tableSettings;
    }

    private List<stroom.query.api.Field> mapFields(final List<Field> fields) {
        if (fields == null || fields.size() == 0) {
            return null;
        }

        final List<stroom.query.api.Field> list = new ArrayList<>(fields.size());
        for (final Field field : fields) {
            stroom.query.api.Field fld = new stroom.query.api.Field();
            fld.setName(field.getName());
            fld.setExpression(field.getExpression());
            fld.setSort(mapSort(field.getSort()));
            fld.setFilter(mapFilter(field.getFilter()));
            fld.setFormat(mapFormat(field.getFormat()));
            fld.setGroup(field.getGroup());
            list.add(fld);
        }

        return list;
    }

    private List<Integer> mapIntArray(final int[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }

        final List<Integer> copy = new ArrayList<>(arr.length);
        for (int i = 0; i < arr.length; i++) {
            copy.add(arr[i]);
        }

        return copy;
    }

    private <T> List<T> mapCollection(final Class<T> clazz, final Collection<T> collection) {
        if (collection == null || collection.size() == 0) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<T> copy = new ArrayList<>(collection.size());
        int i = 0;
        for (final T t : collection) {
            copy.add(t);
        }

        return copy;
    }

    private stroom.query.api.OffsetRange mapOffsetRange(final OffsetRange<Integer> offsetRange) {
        if (offsetRange == null) {
            return null;
        }

        return new stroom.query.api.OffsetRange(offsetRange.getOffset(), offsetRange.getLength());
    }

    private stroom.query.api.Sort mapSort(final Sort sort) {
        if (sort == null) {
            return null;
        }

        SortDirection sortDirection = null;
        if (sort.getDirection() != null) {
            sortDirection = SortDirection.valueOf(sort.getDirection().name());
        }

        final stroom.query.api.Sort copy = new stroom.query.api.Sort();
        copy.setDirection(sortDirection);
        copy.setOrder(sort.getOrder());

        return copy;
    }

    private stroom.query.api.Filter mapFilter(final Filter filter) {
        if (filter == null) {
            return null;
        }

        final stroom.query.api.Filter copy = new stroom.query.api.Filter();
        copy.setIncludes(filter.getIncludes());
        copy.setExcludes(filter.getExcludes());

        return copy;
    }

    private stroom.query.api.Format mapFormat(final Format format) {
        if (format == null) {
            return null;
        }

        final stroom.query.api.Format copy = new stroom.query.api.Format();
        if (format.getType() != null) {
            copy.setType(Type.valueOf(format.getType().name()));
        }
        copy.setNumberFormat(mapNumberFormat(format.getSettings()));
        copy.setDateTimeFormat(mapDateTimeFormat(format.getSettings()));

        return copy;
    }

    private stroom.query.api.NumberFormat mapNumberFormat(final FormatSettings formatSettings) {
        if (formatSettings == null || !(formatSettings instanceof NumberFormatSettings)) {
            return null;
        }

        final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) formatSettings;

        final NumberFormat copy = new NumberFormat(numberFormatSettings.getDecimalPlaces(), numberFormatSettings.getUseSeparator());
        return copy;
    }

    private stroom.query.api.DateTimeFormat mapDateTimeFormat(final FormatSettings formatSettings) {
        if (formatSettings == null || !(formatSettings instanceof DateTimeFormatSettings)) {
            return null;
        }

        final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) formatSettings;

        final DateTimeFormat copy = new DateTimeFormat(dateTimeFormatSettings.getPattern(), mapTimeZone(dateTimeFormatSettings.getTimeZone()));
        return copy;
    }

    private stroom.query.api.TimeZone mapTimeZone(final TimeZone timeZone) {
        if (timeZone == null) {
            return null;
        }

        final stroom.query.api.TimeZone copy = new stroom.query.api.TimeZone(Use.valueOf(timeZone.getUse().name()), timeZone.getId(), timeZone.getOffsetHours(), timeZone.getOffsetMinutes());

        return copy;
    }

//    private TableSettings visStructureToTableSettings(final VisStructure visStructure, final TableSettings parentTableSettings) {
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
//        tableSettings.setFields(fields.toArray(new stroom.query.api.Field[fields.size()]));
//        tableSettings.setMaxResults(limits.toArray(new Integer[limits.size()]));
//        tableSettings.setShowDetail(true);
//
//        return tableSettings;
//    }

    private stroom.query.api.Field convertField(final VisField visField, final Map<String, stroom.query.api.Format> formatMap) {
        final stroom.query.api.Field field = new stroom.query.api.Field();
        field.setFormat(new stroom.query.api.Format(Type.GENERAL));

        if (visField.getId() != null) {
            final stroom.query.api.Format format = formatMap.get(visField.getId());
            if (format != null) {
                field.setFormat(format);
            }

            field.setExpression("${" + visField.getId() + "}");
        }
        field.setSort(visField.getSort());

        return field;
    }


    private stroom.query.api.TableSettings mapVisSettingsToTableSettings(final VisComponentSettings visComponentSettings, final TableSettings parentTableSettings) {
        DocRef docRef = visComponentSettings.getVisualisation();
        TableSettings tableSettings = null;

        try {
            if (docRef == null) {
                return null;
            }

            final Visualisation visualisation = visualisationService.loadByUuid(docRef.getUuid());

            if (visualisation == null || visualisation.getSettings() == null || visualisation.getSettings().length() == 0) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
            mapper.setSerializationInclusion(Include.NON_NULL);

            final VisSettings visSettings = mapper.readValue(visualisation.getSettings(), VisSettings.class);
            if (visSettings != null && visSettings.getData() != null) {
                final SettingResolver settingResolver = new SettingResolver(visSettings, visComponentSettings.getJSON());
                final Structure structure = visSettings.getData().getStructure();
                if (structure != null) {

                    final Map<String, stroom.query.api.Format> formatMap = new HashMap<>();
                    if (parentTableSettings.getFields() != null) {
                        for (final stroom.query.api.Field field : parentTableSettings.getFields()) {
                            if (field != null) {
                                formatMap.put(field.getName(), field.getFormat());
                            }
                        }
                    }

                    List<stroom.query.api.Field> fields = new ArrayList<>();
                    List<Integer> limits = new ArrayList<>();

                    VisNest nest = mapNest(structure.getNest(), settingResolver);
                    VisValues values = mapVisValues(structure.getValues(), settingResolver);

                    int group = 0;
                    while (nest != null) {
                        stroom.query.api.Field field = convertField(nest.getKey(), formatMap);
                        field.setGroup(group++);

                        fields.add(field);

                        // Get limit from nest.
                        Integer limit = null;
                        if (nest.getLimit() != null) {
                            limit = nest.getLimit().getSize();
                        }
                        limits.add(limit);

                        values = nest.getValues();
                        nest = nest.getNest();
                    }

                    if (values != null) {
                        // Get limit from values.
                        Integer limit = null;
                        if (values.getLimit() != null) {
                            limit = values.getLimit().getSize();
                        }
                        limits.add(limit);

                        if (values.getFields() != null) {
                            for (final VisField visField : values.getFields()) {
                                fields.add(convertField(visField, formatMap));
                            }
                        }
                    }

                    tableSettings = new TableSettings();
                    tableSettings.setFields(fields);
                    tableSettings.setMaxResults(limits);
                    tableSettings.setShowDetail(true);
                }
            }

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
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
//            final Visualisation visualisation = visualisationService.loadByUuid(docRef.getUuid());
//
//            if (visualisation == null || visualisation.getSettings() == null || visualisation.getSettings().length() == 0) {
//                return null;
//            }
//
//            ObjectMapper mapper = new ObjectMapper();
//            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//            mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
//            mapper.setSerializationInclusion(Include.NON_NULL);
//
//            final VisSettings visSettings = mapper.readValue(visualisation.getSettings(), VisSettings.class);
//            if (visSettings != null && visSettings.getData() != null) {
//                final SettingResolver settingResolver = new SettingResolver(visSettings, visComponentSettings.getJSON());
//                final Structure structure = visSettings.getData().getStructure();
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

        Boolean enabled = settingResolver.resolveBoolean(sort.getEnabled());
        if (enabled != null && enabled) {
            final stroom.query.api.Sort copy = new stroom.query.api.Sort();
            SortDirection direction = SortDirection.ASCENDING;

            String dir = settingResolver.resolveString(sort.getDirection());
            if (dir != null) {
                if (dir.equalsIgnoreCase(SortDirection.DESCENDING.getDisplayValue())) {
                    direction = SortDirection.DESCENDING;
                }
            }

            copy.setDirection(direction);
            copy.setOrder(settingResolver.resolveInteger(sort.getPriority()));

            return copy;
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
                copy.setSize(settingResolver.resolveInteger(limit.getSize()));
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
                            if (control.getId() != null && control != null) {
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

        private Map<String, String> getDashboardSettingsMap(final String json) {
            Map<String, String> map = new HashMap<>();

            try {
                if (json != null && json.length() > 0) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    final JsonNode node = objectMapper.readTree(json);

                    Iterator<Entry<String, JsonNode>> iterator = node.fields();
                    while (iterator.hasNext()) {
                        Entry<String, JsonNode> entry = iterator.next();
                        JsonNode val = entry.getValue();
                        if (val != null) {
                            final String str = val.textValue();
                            if (str != null) {
                                map.put(entry.getKey(), str);
                            }
                        }
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            return map;
        }
    }
}