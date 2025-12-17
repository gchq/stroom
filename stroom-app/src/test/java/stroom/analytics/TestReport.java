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
import stroom.analytics.impl.ReportExecutor;
import stroom.analytics.rule.impl.ReportStore;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportSettings;
import stroom.analytics.shared.ScheduleBounds;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.shared.Meta;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.util.io.StreamUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
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
class TestReport extends AbstractAnalyticsTest {

    @Inject
    private ReportExecutor reportExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private ExecutionScheduleDao executionScheduleDao;
    @Inject
    private ReportStore reportStore;
    @Inject
    private Store streamStore;

    @Test
    void test() {
        final String query = """
                from index_view
                where UserId = user5
                select StreamId, EventId, UserId""";
        basicTest(query, 9, 6);
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        final ReportDoc reportDoc = ReportDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.CSV).build())
                .notifications(createNotificationConfig())
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        final DocRef docRef = writeReport(reportDoc);
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
        reportExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testReportStream(expectedStreams, expectedRecords);

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

    private void testReportStream(final int expectedStreams,
                                  final int expectedRecords) {
        analyticsDataSetup.checkStreamCount(expectedStreams);

        // As we have created alerts ensure we now have more streams.
        final Meta newestMeta = analyticsDataSetup.getNewestMeta();
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.trim()).isEqualTo("""
                    "StreamId","EventId","UserId"
                    "8","5","user5"
                    "8","9","user5"
                    "8","14","user5"
                    "8","20","user5"
                    "8","23","user5"
                    """.trim());

            try (final InputStreamProvider inputStreamProvider = source.get(0)) {
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                    final String meta = StreamUtil.streamToString(inputStream);
                    assertThat(meta).contains("ReportName:Test Report");
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DocRef writeReport(final ReportDoc sample) {
        final DocRef docRef = reportStore.createDocument("Test Report");
        ReportDoc reportDoc = reportStore.readDocument(docRef);
        reportDoc = reportDoc.copy()
                .languageVersion(sample.getLanguageVersion())
                .query(sample.getQuery())
                .analyticProcessType(sample.getAnalyticProcessType())
                .reportSettings(sample.getReportSettings())
                .analyticProcessConfig(sample.getAnalyticProcessConfig())
                .notifications(new ArrayList<>(sample.getNotifications()))
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        reportStore.writeDocument(reportDoc);

        assertThat(reportStore.list().size()).isOne();
        assertThat(reportStore.list().getFirst().getUuid()).isEqualTo(docRef.getUuid());

        return docRef;
    }
}
