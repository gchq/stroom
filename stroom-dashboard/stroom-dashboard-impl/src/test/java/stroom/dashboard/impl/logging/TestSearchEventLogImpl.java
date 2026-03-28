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

package stroom.dashboard.impl.logging;

import stroom.docref.DocRef;
import stroom.docrefinfo.mock.MockDocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;
import stroom.security.mock.MockSecurityContext;

import event.logging.Data;
import event.logging.EventAction;
import event.logging.Purpose;
import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TestSearchEventLogImpl {

    private static final DocRef DATA_SOURCE = new DocRef("Index", "test-uuid", "MyIndex");
    private static final String QUERY_INFO = "Testing time range logging";

    private final MockSecurityContext securityContext = new MockSecurityContext();

    @Mock
    private StroomEventLoggingService eventLoggingService;

    @Captor
    private ArgumentCaptor<EventAction> eventActionCaptor;

    private SearchEventLogImpl searchEventLog;

    @BeforeEach
    void setup() {
        searchEventLog = new SearchEventLogImpl(
                eventLoggingService,
                securityContext,
                new MockDocRefInfoService());
    }

    @Test
    void search_logsTimeRange_whenBothFromAndToAreSet() {
        final TimeRange timeRange = new TimeRange("Last week", "2025-01-01T00:00:00.000Z", "2025-01-08T00:00:00.000Z");

        callSearch(timeRange);

        final SearchEventAction action = captureSearchEventAction();
        final Optional<Data> timeRangeData = findDataByName(action.getData(), "timeRange");
        assertThat(timeRangeData).isPresent();
        assertThat(findDataByName(timeRangeData.get().getData(), "from"))
                .hasValueSatisfying(d -> assertThat(d.getValue()).isEqualTo("2025-01-01T00:00:00.000Z"));
        assertThat(findDataByName(timeRangeData.get().getData(), "to"))
                .hasValueSatisfying(d -> assertThat(d.getValue()).isEqualTo("2025-01-08T00:00:00.000Z"));
    }

    @Test
    void search_logsTimeRange_whenOnlyFromIsSet() {
        final TimeRange timeRange = new TimeRange(null, "2025-01-01T00:00:00.000Z", null);

        callSearch(timeRange);

        final SearchEventAction action = captureSearchEventAction();
        final Optional<Data> timeRangeData = findDataByName(action.getData(), "timeRange");
        assertThat(timeRangeData).isPresent();
        assertThat(findDataByName(timeRangeData.get().getData(), "from")).isPresent();
        assertThat(findDataByName(timeRangeData.get().getData(), "to")).isEmpty();
    }

    @Test
    void search_logsTimeRange_whenOnlyToIsSet() {
        final TimeRange timeRange = new TimeRange(null, null, "2025-01-08T00:00:00.000Z");

        callSearch(timeRange);

        final SearchEventAction action = captureSearchEventAction();
        final Optional<Data> timeRangeData = findDataByName(action.getData(), "timeRange");
        assertThat(timeRangeData).isPresent();
        assertThat(findDataByName(timeRangeData.get().getData(), "from")).isEmpty();
        assertThat(findDataByName(timeRangeData.get().getData(), "to")).isPresent();
    }

    @Test
    void search_omitsTimeRangeElement_whenTimeRangeIsNull() {
        callSearch(null);

        final SearchEventAction action = captureSearchEventAction();
        assertThat(findDataByName(action.getData(), "timeRange")).isEmpty();
    }

    @Test
    void search_logsQueryParams() {
        final List<Param> params = List.of(
                new Param("environment", "prod"),
                new Param("userId", "jbloggs"));

        searchEventLog.search(
                null,
                "query1",
                "StroomQL Search",
                "select * from MyIndex where ${userId}",
                DATA_SOURCE,
                ExpressionOperator.builder().build(),
                new TimeRange("Last week", "2025-01-01T00:00:00.000Z", "2025-01-08T00:00:00.000Z"),
                QUERY_INFO,
                params,
                null,
                null);

        final SearchEventAction action = captureSearchEventAction();
        final Optional<Data> paramsData = findDataByName(action.getData(), "params");
        assertThat(paramsData).isPresent();
        assertThat(findDataByName(paramsData.get().getData(), "environment"))
                .hasValueSatisfying(d -> assertThat(d.getValue()).isEqualTo("prod"));
        assertThat(findDataByName(paramsData.get().getData(), "userId"))
                .hasValueSatisfying(d -> assertThat(d.getValue()).isEqualTo("jbloggs"));
    }

    @Test
    void search_logsFailureOutcome_whenExceptionIsProvided() {
        final RuntimeException exception = new RuntimeException("Search failed");

        callSearch(new TimeRange("Last week", "2025-01-01T00:00:00.000Z", "2025-01-08T00:00:00.000Z"), exception);

        final SearchEventAction action = captureSearchEventAction();
        assertThat(action.getOutcome()).isNotNull();
        assertThat(action.getOutcome().isSuccess()).isFalse();
        assertThat(action.getOutcome().getDescription()).contains("Search failed");
    }

    @Test
    void search_setsRawQuery() {
        final String rawQuery = "select * from MyIndex where EventTime > yesterday()";

        searchEventLog.search(
                null,
                "query1",
                "StroomQL Search",
                rawQuery,
                DATA_SOURCE,
                null,
                null,
                QUERY_INFO,
                Collections.emptyList(),
                null,
                null);

        final SearchEventAction action = captureSearchEventAction();
        assertThat(action.getQuery()).isNotNull();
        assertThat(action.getQuery().getRaw()).isEqualTo(rawQuery);
    }

    private SearchEventAction captureSearchEventAction() {
        verify(eventLoggingService).log(anyString(), anyString(), any(Purpose.class), eventActionCaptor.capture());
        assertThat(eventActionCaptor.getValue()).isInstanceOf(SearchEventAction.class);
        return (SearchEventAction) eventActionCaptor.getValue();
    }

    private Optional<Data> findDataByName(final List<Data> dataList, final String name) {
        if (dataList == null) {
            return Optional.empty();
        }
        return dataList.stream()
                .filter(d -> name.equals(d.getName()))
                .findFirst();
    }

    private void callSearch(final TimeRange timeRange) {
        callSearch(timeRange, null);
    }

    private void callSearch(final TimeRange timeRange, final Exception exception) {
        searchEventLog.search(
                null,
                "query1",
                "StroomQL Search",
                "select * from MyIndex",
                DATA_SOURCE,
                null,
                timeRange,
                QUERY_INFO,
                Collections.emptyList(),
                null,
                exception);
    }
}
