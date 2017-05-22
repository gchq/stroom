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

import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Filter;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.NumberFormatSettings;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.TimeZone;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.ExpressionBuilder;
import stroom.query.api.v1.ExpressionOperator;
import stroom.query.api.v1.ExpressionTerm;

import java.util.HashMap;
import java.util.Map;

public class SearchRequestTestData {

    static stroom.query.api.v1.SearchRequest apiSearchRequest(){
        stroom.dashboard.shared.SearchRequest dashboardSearchRequest = dashboardSearchRequest();

        SearchRequestMapper searchRequestMapper = new SearchRequestMapper(new MockVisualisationService());
        stroom.query.api.v1.SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(DashboardQueryKey.create("queryKeyUuid", 1l), dashboardSearchRequest);

        return apiSearchRequest;
    }

    static stroom.dashboard.shared.SearchRequest dashboardSearchRequest() {
        DocRef docRef = new DocRef("docRefType", "docRefUuid", "docRefName");

        ExpressionBuilder expressionOperator = new ExpressionBuilder(ExpressionOperator.Op.AND);
        expressionOperator.addTerm("field1", ExpressionTerm.Condition.EQUALS, "value1");
        expressionOperator.addOperator(ExpressionOperator.Op.AND);
        expressionOperator.addTerm("field2", ExpressionTerm.Condition.BETWEEN, "value2");

        TableComponentSettings tableSettings = new TableComponentSettings();
        tableSettings.setQueryId("someQueryId");
        tableSettings.addField(new Field("name1", "expression1",
                new Sort(1, Sort.SortDirection.ASCENDING),
                new Filter("include1", "exclude1"),
                new Format(Format.Type.NUMBER, new NumberFormatSettings(1, false)), 1, 200, true));
        tableSettings.addField(new Field("name2", "expression2",
                new Sort(2, Sort.SortDirection.DESCENDING),
                new Filter("include2", "exclude2"),
                new Format(Format.Type.DATE_TIME, createDateTimeFormat()), 2, 200, true));
        tableSettings.setExtractValues(false);
        tableSettings.setExtractionPipeline(new SharedDocRef("docRefType2", "docRefUuid2", "docRefName2"));
        tableSettings.setMaxResults(new int[]{1, 2});
        tableSettings.setShowDetail(false);

        Map<String, ComponentSettings> componentSettingsMap = new HashMap<>();
        componentSettingsMap.put("componentSettingsMapKey", tableSettings);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("param1", "val1");
        paramMap.put("param2", "val2");

        final Search search = new Search(docRef, expressionOperator.build(), componentSettingsMap, paramMap, true);

        final Map<String, ComponentResultRequest> componentResultRequestMap = new HashMap<>();
        for (final Map.Entry<String, ComponentSettings> entry : componentSettingsMap.entrySet()) {
            TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings((TableComponentSettings) entry.getValue());
            tableResultRequest.setWantsData(true);
            componentResultRequestMap.put(entry.getKey(), tableResultRequest);
        }

        stroom.dashboard.shared.SearchRequest searchRequest = new stroom.dashboard.shared.SearchRequest(search, componentResultRequestMap, "en-gb");

        return searchRequest;
    }

    private static DateTimeFormatSettings createDateTimeFormat() {
        final TimeZone timeZone = TimeZone.fromOffset(2, 30);

        final DateTimeFormatSettings dateTimeFormat = new DateTimeFormatSettings();
        dateTimeFormat.setPattern("yyyy-MM-dd'T'HH:mm:ss");
        dateTimeFormat.setTimeZone(timeZone);

        return dateTimeFormat;
    }
}