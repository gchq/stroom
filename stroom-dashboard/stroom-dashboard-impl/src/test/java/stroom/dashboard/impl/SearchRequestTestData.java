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

import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.NumberFormatSettings;
import stroom.query.api.Param;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.Sort;
import stroom.query.api.TableSettings;
import stroom.query.api.UserTimeZone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchRequestTestData {

    static SearchRequest apiSearchRequest() {
        final DashboardSearchRequest dashboardSearchRequest = dashboardSearchRequest();

        final SearchRequestMapper searchRequestMapper = new SearchRequestMapper(null);
        return searchRequestMapper.mapRequest(dashboardSearchRequest);
    }

    static DashboardSearchRequest dashboardSearchRequest() {
        final DocRef docRef = new DocRef("docRefType", "docRefUuid", "docRefName");

        final ExpressionOperator.Builder expressionOperator = ExpressionOperator.builder();
        expressionOperator.addTerm("field1", ExpressionTerm.Condition.EQUALS, "value1");
        expressionOperator.addOperator(ExpressionOperator.builder().build());
        expressionOperator.addTerm("field2", ExpressionTerm.Condition.BETWEEN, "value2");

        final String componentId = "componentSettingsMapKey";
        final TableComponentSettings tableSettings = TableComponentSettings.builder()
                .queryId("someQueryId")
                .addColumn(Column.builder()
                        .id("1")
                        .name("name1")
                        .expression("expression1")
                        .sort(new Sort(1, Sort.SortDirection.ASCENDING))
                        .filter(new IncludeExcludeFilter("include1", "exclude1", null, null))
                        .format(Format.builder()
                                .type(Format.Type.NUMBER)
                                .settings(new NumberFormatSettings(
                                        1,
                                        false))
                                .build())
                        .group(1)
                        .width(200)
                        .visible(true)
                        .special(false)
                        .build())
                .addColumn(Column.builder()
                        .id("2")
                        .name("name2")
                        .expression("expression2")
                        .sort(new Sort(2, Sort.SortDirection.DESCENDING))
                        .filter(new IncludeExcludeFilter("include2", "exclude2", null, null))
                        .format(Format.builder()
                                .type(Type.DATE_TIME)
                                .settings(createDateTimeFormat())
                                .build())
                        .group(2)
                        .width(200)
                        .visible(true)
                        .special(false)
                        .build())
                .extractValues(false)
                .extractionPipeline(
                        new DocRef("docRefType2", "docRefUuid2", "docRefName2"))
                .maxResults(List.of(1L, 2L))
                .showDetail(false)
                .build();

        final Map<String, ComponentSettings> componentSettingsMap = new HashMap<>();
        componentSettingsMap.put(componentId, tableSettings);

        final List<Param> params = List.of(new Param("param1", "val1"), new Param("param2", "val2"));

        final Search search = Search.builder()
                .dataSourceRef(docRef)
                .expression(expressionOperator.build())
                .componentSettingsMap(componentSettingsMap)
                .params(params)
                .incremental(true)
                .build();

        final List<ComponentResultRequest> componentResultRequests = new ArrayList<>();
        for (final Map.Entry<String, ComponentSettings> entry : componentSettingsMap.entrySet()) {
            final TableComponentSettings tableComponentSettings = (TableComponentSettings) entry.getValue();
            final TableSettings ts = tableComponentSettings.copy().buildTableSettings();
            final TableResultRequest tableResultRequest = TableResultRequest.builder()
                    .componentId(entry.getKey())
                    .tableSettings(ts)
                    .fetch(Fetch.CHANGES)
                    .build();
            componentResultRequests.add(tableResultRequest);
        }

        return DashboardSearchRequest
                .builder()
                .searchRequestSource(
                        SearchRequestSource
                                .builder()
                                .sourceType(SourceType.DASHBOARD_UI)
                                .ownerDocRef(DocRef
                                        .builder()
                                        .type(DashboardDoc.TYPE)
                                        .uuid("dashboardUuid")
                                        .build())
                                .componentId(componentId)
                                .build())
                .search(search)
                .componentResultRequests(componentResultRequests)
                .build();
    }

    private static DateTimeFormatSettings createDateTimeFormat() {
        final UserTimeZone timeZone = UserTimeZone.fromOffset(2, 30);
        return new DateTimeFormatSettings(true, "yyyy-MM-dd'T'HH:mm:ss", timeZone);
    }
}
