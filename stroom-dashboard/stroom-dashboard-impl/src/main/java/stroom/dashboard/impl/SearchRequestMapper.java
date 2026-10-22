/*
 * Copyright 2017 Crown Copyright
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
import stroom.dashboard.impl.vis.VisStructureMapper;
import stroom.dashboard.impl.vis.VisStructureMapper.VisTable;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.GroupSelection;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Builder;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
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

    /// /        final TableSettings tableSettings = TableSettings.builder()
    /// /                .queryId(tableComponentSettings.getQueryId())
    /// /                .addFields(mapFields(tableComponentSettings.getFields()))
    /// /                .extractValues(tableComponentSettings.extractValues())
    /// /                .extractionPipeline(tableComponentSettings.getExtractionPipeline())
    /// /                .addMaxResults(mapIntArray(tableComponentSettings.getMaxResults()))
    /// /                .showDetail(tableComponentSettings.getShowDetail())
    /// /                .build();
    /// /
    /// /        return tableSettings;
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
    private TableSettings mapVisSettingsToTableSettings(final VisResultRequest visResultRequest,
                                                        final TableSettings parentTableSettings) {
        final DocRef docRef = visResultRequest.getVisualisation();
        if (docRef == null) {
            return null;
        }

        final VisualisationDoc visualisation = visualisationStore.readDocument(docRef);
        if (visualisation == null
            || visualisation.getSettings() == null
            || visualisation.getSettings().isEmpty()) {
            return null;
        }

        final VisSettings visSettings = JsonUtil.readValue(visualisation.getSettings(), VisSettings.class);
        final VisTable visTable = VisStructureMapper.map(
                visSettings,
                VisStructureMapper.chosenValuesFromJson(visResultRequest.getJson()),
                parentTableSettings.getColumns());
        if (visTable == null) {
            return null;
        }
        return TableSettings.builder()
                .addColumns(visTable.columns())
                .addMaxResults(visTable.maxResults())
                .showDetail(true)
                .build();
    }
}
