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

package stroom.analytics;

import stroom.analytics.impl.ExecutionScheduleDao;
import stroom.analytics.impl.ScheduledQueryAnalyticExecutor;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ScheduleBounds;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.langchain.impl.MockOpenAIModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(GuiceExtension.class)
@IncludeModule(UriFactoryModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
@IncludeModule(MockOpenAIModule.class)
class TestScheduledQueryAnalytics extends AbstractAnalyticsTest {

    @Inject
    private ScheduledQueryAnalyticExecutor analyticsExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private ExecutionScheduleDao executionScheduleDao;

    @Test
    void testSingleEventScheduledQuery() {
        final String query = """
                from index_view
                where UserId = user5
                select StreamId, EventId, UserId""";
        basicTest(query, 9, 6);
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        final AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .notifications(createNotificationConfig())
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        final DocRef docRef = writeRule(analyticRuleDoc);
        final long now = System.currentTimeMillis();
        final ExecutionSchedule executionSchedule = executionScheduleDao.createExecutionSchedule(ExecutionSchedule
                .builder()
                .name("Test")
                .enabled(true)
                .nodeName(nodeInfo.getThisNodeName())
                .schedule(Schedule
                        .builder()
                        .type(ScheduleType.CRON)
                        .expression("* * * * * ?")
                        .build())
                .contiguous(true)
                .scheduleBounds(ScheduleBounds
                        .builder()
                        .startTimeMs(now)
                        .endTimeMs(now)
                        .build())
                .owningDoc(docRef)
                .build());

        // Now run the search process.
        analyticsExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testDetectionsStream(expectedStreams, expectedRecords);

        // Get execution history.
        final ExecutionHistoryRequest request = new ExecutionHistoryRequest(
                PageRequest.createDefault(),
                Collections.emptyList(),
                executionSchedule);
        ResultPage<ExecutionHistory> resultPage = executionScheduleDao.fetchExecutionHistory(request);
        assertThat(resultPage.size()).isOne();

        // Delete execution history.

        // First make sure we don't delete the current history if the age is the same as the current time.
        executionScheduleDao.deleteOldExecutionHistory(
                Instant.ofEpochMilli(resultPage.getValues().getFirst().getExecutionTimeMs()));
        resultPage = executionScheduleDao.fetchExecutionHistory(request);
        assertThat(resultPage.size()).isOne();

        // Now delete all old history items.
        executionScheduleDao.deleteOldExecutionHistory(Instant.now().plusMillis(1));
        resultPage = executionScheduleDao.fetchExecutionHistory(request);
        assertThat(resultPage.size()).isZero();
    }
}
