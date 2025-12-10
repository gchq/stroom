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
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchRequestMapper {
//    @Mock
//    private VisualisationStore visualisationStore;

    @InjectMocks
    private SearchRequestMapper searchRequestMapper;

    private static void verify_Search_to_Query_mapping(final Search search, final Query query) {
        assertThat(search.getDataSourceRef())
                .isEqualTo(query.getDataSource());
        assertThat(search.getExpression())
                .isEqualTo(query.getExpression());
        assertThat(search.getParams().size())
                .isEqualTo(query.getParams().size());
        assertThat(search.getParams().get(0).getValue())
                .isEqualTo(query.getParams().get(0).getValue());
        // 'param1' is the key from SearchRequestTestData.dashboardSearchRequest()
    }

    private static void verify_ComponentResultRequest_to_ResultRequest_mappings(
            final List<ComponentResultRequest> componentResultRequests,
            final List<ResultRequest> resultRequests) {

        assertThat(componentResultRequests.size())
                .isEqualTo(resultRequests.size());
        assertThat("componentSettingsMapKey")
                .isEqualTo(resultRequests.get(0).getComponentId());

        final Optional<ComponentResultRequest> optional = componentResultRequests
                .stream()
                .filter(request -> request.getComponentId().equals("componentSettingsMapKey"))
                .findFirst();

        assertThat(optional).isNotEmpty();
        assertThat(optional.get().getFetch())
                .isEqualTo(resultRequests.get(0).getFetch());

        final TableResultRequest tableResultRequest = ((TableResultRequest) optional.get());
        final ResultRequest resultRequest = resultRequests.get(0);
        assertThat(tableResultRequest.getRequestedRange().getOffset())
                .isEqualTo(resultRequest.getRequestedRange().getOffset());
        assertThat(tableResultRequest.getRequestedRange().getLength())
                .isEqualTo(resultRequest.getRequestedRange().getLength());
        assertThat(tableResultRequest.getOpenGroups())
                .isEqualTo(resultRequest.getOpenGroups());
        // No test data for this at the moment
        //TODO many more properties to check
    }

    @Test
    void testSearchRequestMapper() {
        // Given
        final DashboardSearchRequest dashboardSearchRequest = SearchRequestTestData.dashboardSearchRequest();

        // When
        final SearchRequest mappedApiSearchRequest = searchRequestMapper.mapRequest(dashboardSearchRequest);

        // Then
        verify_Search_to_Query_mapping(
                dashboardSearchRequest.getSearch(),
                mappedApiSearchRequest.getQuery());
        verify_ComponentResultRequest_to_ResultRequest_mappings(
                dashboardSearchRequest.getComponentResultRequests(),
                mappedApiSearchRequest.getResultRequests());
        //TODO many more properties to check
    }

}
