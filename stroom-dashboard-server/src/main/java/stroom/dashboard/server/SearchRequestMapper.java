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

package stroom.dashboard.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import stroom.dashboard.server.vis.VisSettings;
import stroom.dashboard.server.vis.VisSettings.Nest;
import stroom.dashboard.server.vis.VisSettings.Structure;
import stroom.dashboard.shared.ComponentResultRequest;
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
import stroom.dashboard.shared.VisResultRequest;
import stroom.query.api.DateTimeFormat;
import stroom.query.api.DocRef;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormat;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeZone.Use;
import stroom.query.api.VisField;
import stroom.query.api.VisLimit;
import stroom.query.api.VisNest;
import stroom.query.api.VisSort;
import stroom.query.api.VisStructure;
import stroom.query.api.VisValues;
import stroom.util.shared.OffsetRange;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class SearchRequestMapper {
//    private static Param[] convert(final Map<String, String> paramMap) {
//        if (paramMap == null || paramMap.size() == 0) {
//            return null;
//        }
//
//        final Param[] arr = new Param[paramMap.size()];
//        int i = 0;
//        for (final Entry<String, String> entry : paramMap.entrySet()) {
//            arr[i++] = new Param(entry.getKey(), entry.getValue());
//        }
//
//        return arr;
//    }
//
//    public SearchRequest mapRequest(final stroom.dashboard.shared.SearchRequest in) {
////        Converter<Map<String, String>, Collection<Param>> mapCollectionConverter = new Converter<Map<String, String>, Collection<Param>>() {
////            @Override
////            public Collection<Param> convert(final MappingContext<Map<String, String>, Collection<Param>> context) {
////                return null;
////            }
////        };
//
//        PropertyMap<stroom.dashboard.shared.Search, Query> searchMapping = new PropertyMap<stroom.dashboard.shared.Search, Query>() {
//            @Override
//            protected void configure() {
//                map(source.getDataSourceRef(), destination.getDataSource());
//                map(source.getExpression(), destination.getExpression());
//                skip(source.getComponentSettingsMap());
////                map().setParams(convert(source.getParamMap()));
//
//                skip(source.getParamMap());
//                skip().setParams(null);
//
//                skip(source.getIncremental());
//            }
//        };
//
//        PropertyMap<stroom.dashboard.shared.SearchRequest, SearchRequest> searchRequestMapping = new PropertyMap<stroom.dashboard.shared.SearchRequest, SearchRequest>() {
//            @Override
//            protected void configure() {
//                skip().setKey(null);
//                map(source.getSearch(), destination.getQuery());
////                map(source.getComponentResultRequests(), destination.getResultRequests());
//
//                skip(source.getComponentResultRequests());
//                skip().setResultRequests(null);
//
//                map().setDateTimeLocale(source.getDateTimeLocale());
//                map().setIncremental(source.getSearch().getIncremental());
//            }
//        };
//
//        final ModelMapper modelMapper = new ModelMapper();
////        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
//        modelMapper.addMappings(searchRequestMapping);
//        modelMapper.addMappings(searchMapping);
//        modelMapper.validate();
//
//        SearchRequest out = modelMapper.map(in, SearchRequest.class);
//        return out;
//    }


    private final VisualisationService visualisationService;

    @Inject
    public SearchRequestMapper(final VisualisationService visualisationService) {
        this.visualisationService = visualisationService;
    }

    public stroom.query.api.SearchRequest mapRequest(final QueryKey queryKey, final stroom.dashboard.shared.SearchRequest searchRequest) {
        if (searchRequest == null) {
            return null;
        }

        final stroom.query.api.SearchRequest copy = new stroom.query.api.SearchRequest();
        copy.setKey(queryKey);
        copy.setQuery(mapQuery(searchRequest));
        copy.setResultRequests(mapResultRequests(searchRequest));
        copy.setDateTimeLocale(searchRequest.getDateTimeLocale());
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
            Param[] params = new Param[searchRequest.getSearch().getParamMap().size()];
            int i = 0;
            for (final Entry<String, String> entry : searchRequest.getSearch().getParamMap().entrySet()) {
                final Param param = new Param(entry.getKey(), entry.getValue());
                params[i++] = param;
            }

            query.setParams(params);
        }

        return query;
    }

    private ResultRequest[] mapResultRequests(final stroom.dashboard.shared.SearchRequest searchRequest) {
        if (searchRequest.getComponentResultRequests() == null || searchRequest.getComponentResultRequests().size() == 0) {
            return null;
        }

        final ResultRequest[] resultRequests = new ResultRequest[searchRequest.getComponentResultRequests().size()];
        int i = 0;
        for (final Entry<String, ComponentResultRequest> entry : searchRequest.getComponentResultRequests().entrySet()) {
            final String componentId = entry.getKey();
            final ComponentResultRequest componentResultRequest = entry.getValue();
            if (componentResultRequest instanceof TableResultRequest) {
                final TableResultRequest tableResultRequest = (TableResultRequest) componentResultRequest;
                final stroom.query.api.TableResultRequest copy = new stroom.query.api.TableResultRequest();
                copy.setComponentId(componentId);
                copy.setFetchData(tableResultRequest.wantsData());
                copy.setTableSettings(mapTableSettings(tableResultRequest.getTableSettings()));
                copy.setRequestedRange(mapOffsetRange(tableResultRequest.getRequestedRange()));
                copy.setOpenGroups(mapCollection(String.class, tableResultRequest.getOpenGroups()));
                resultRequests[i++] = copy;

            } else if (componentResultRequest instanceof VisResultRequest) {
                final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;
                final stroom.query.api.VisResultRequest copy = new stroom.query.api.VisResultRequest();
                copy.setComponentId(componentId);
                copy.setFetchData(visResultRequest.wantsData());
                copy.setTableSettings(mapTableSettings(visResultRequest.getVisDashboardSettings().getTableSettings()));
                copy.setStructure(mapStructure(visResultRequest.getVisDashboardSettings().getVisualisation()));
                copy.setParams(mapVisParams(visResultRequest.getVisDashboardSettings().getJSON()));
                resultRequests[i++] = copy;
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
        tableSettings.setExtractValues(tableComponentSettings.getExtractValues());
        tableSettings.setExtractionPipeline(tableComponentSettings.getExtractionPipeline());
        tableSettings.setMaxResults(mapIntArray(tableComponentSettings.getMaxResults()));
        tableSettings.setShowDetail(tableComponentSettings.getShowDetail());

        return tableSettings;
    }

    private stroom.query.api.Field[] mapFields(final List<Field> fields) {
        if (fields == null || fields.size() == 0) {
            return null;
        }

        stroom.query.api.Field[] arr = new stroom.query.api.Field[fields.size()];
        int i = 0;
        for (final Field field : fields) {
            stroom.query.api.Field fld = new stroom.query.api.Field();
            fld.setName(field.getName());
            fld.setExpression(field.getExpression());
            fld.setSort(mapSort(field.getSort()));
            fld.setFilter(mapFilter(field.getFilter()));
            fld.setFormat(mapFormat(field.getFormat()));
            fld.setGroup(field.getGroup());
            arr[i++] = fld;
        }

        return arr;
    }

    private Integer[] mapIntArray(final int[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }

        final Integer[] copy = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            copy[i] = arr[i];
        }

        return copy;
    }

    private <T> T[] mapCollection(final Class<T> clazz, final Collection<T> collection) {
        if (collection == null || collection.size() == 0) {
            return null;
        }

        @SuppressWarnings("unchecked")
        T[] copy = (T[]) Array.newInstance(clazz, collection.size());
        int i = 0;
        for (final T t : collection) {
            copy[i++] = t;
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

        final DateTimeFormat copy = new DateTimeFormat();
        copy.setPattern(dateTimeFormatSettings.getPattern());
        copy.setTimeZone(mapTimeZone(dateTimeFormatSettings.getTimeZone()));
        return copy;
    }

    private stroom.query.api.TimeZone mapTimeZone(final TimeZone timeZone) {
        if (timeZone == null) {
            return null;
        }

        final stroom.query.api.TimeZone copy = new stroom.query.api.TimeZone();
        copy.setUse(Use.valueOf(timeZone.getUse().name()));
        copy.setId(timeZone.getId());
        copy.setOffsetHours(timeZone.getOffsetHours());
        copy.setOffsetMinutes(timeZone.getOffsetMinutes());

        return copy;
    }

    private stroom.query.api.VisStructure mapStructure(final DocRef docRef) {
        VisStructure copy = null;

        try {
            if (docRef == null) {
                return null;
            }

            final Visualisation visualisation = visualisationService.loadByUuid(docRef.getUuid());

            if (visualisation == null || visualisation.getSettings() == null || visualisation.getSettings().length() == 0) {
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            final VisSettings visSettings = objectMapper.readValue(visualisation.getSettings(), VisSettings.class);
            if (visSettings != null && visSettings.getData() != null) {


                final Structure structure = visSettings.getData().getStructure();
                if (structure != null) {
                    copy = new VisStructure();
                    copy.setNest(mapNest(structure.getNest()));
                    copy.setValues(mapVisValues(structure.getValues()));
                }
            }

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return copy;
    }

    private stroom.query.api.VisNest mapNest(final Nest nest) {
        if (nest == null) {
            return null;
        }

        final VisNest copy = new VisNest();
        copy.setKey(mapVisField(nest.getKey()));
        copy.setNest(mapNest(nest.getNest()));
        copy.setValues(mapVisValues(nest.getValues()));
        copy.setLimit(mapVisLimit(nest.getLimit()));

        return copy;
    }

    private stroom.query.api.VisField mapVisField(final VisSettings.Field field) {
        if (field == null) {
            return null;
        }

        final VisField copy = new VisField();
        copy.setId(field.getId());
        copy.setSort(mapVisSort(field.getSort()));

        return copy;
    }

    private stroom.query.api.VisSort mapVisSort(final VisSettings.Sort sort) {
        if (sort == null) {
            return null;
        }

        final VisSort copy = new VisSort();
        copy.setEnabled(sort.getEnabled());
        copy.setDirection(sort.getDirection());
        copy.setPriority(sort.getPriority());

        return copy;
    }

    private stroom.query.api.VisValues mapVisValues(final VisSettings.Values values) {
        if (values == null) {
            return null;
        }

        VisField[] fields = null;
        if (values.getFields() != null) {
            fields = new VisField[values.getFields().length];
            for (int i = 0; i < values.getFields().length; i++) {
                fields[i] = mapVisField(values.getFields()[i]);
            }
        }

        final VisValues copy = new VisValues();
        copy.setFields(fields);
        copy.setLimit(mapVisLimit(values.getLimit()));

        return copy;
    }

    private stroom.query.api.VisLimit mapVisLimit(final VisSettings.Limit limit) {
        if (limit == null) {
            return null;
        }

        final VisLimit copy = new VisLimit();
        copy.setEnabled(limit.getEnabled());
        copy.setSize(limit.getSize());

        return copy;
    }

    private stroom.query.api.Param[] mapVisParams(final String json) {
        List<Param> params = new ArrayList<>();

        try {
            if (json == null || json.length() == 0) {
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode node = objectMapper.readTree(json);

            Iterator<Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Entry<String, JsonNode> entry = iterator.next();
                JsonNode val = entry.getValue();
                if (val != null) {
                    final String str = val.textValue();
                    if (str != null) {
                        params.add(new Param(entry.getKey(), str));
                    }
                }
            }

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (params.size() == 0) {
            return null;
        }

        return params.toArray(new Param[params.size()]);
    }
}
