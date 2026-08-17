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

import stroom.ai.impl.mock.MockAiModule;
import stroom.analytics.impl.ExecutionScheduleDao;
import stroom.analytics.impl.ReportExecutor;
import stroom.analytics.impl.ReportStore;
import stroom.analytics.impl.ScheduledExecutorService;
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
import stroom.docstore.impl.DocFinderModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.query.api.Column;
import stroom.query.shared.QueryTablePreferences;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
@IncludeModule(MockAiModule.class)
@IncludeModule(DocFinderModule.class)
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
    @Inject
    private ScheduledExecutorService<ReportDoc> scheduledExecutorService;
    @Inject
    private MetaService metaService;

    /**
     * The stream type that {@link ReportExecutor} writes report output as.
     */
    private static final String REPORT_STREAM_TYPE = "Report";

    /**
     * Placeholder for the id of the source events stream in the expected report content. Meta ids are not reset
     * between test classes that share a database, so the id the setup data gets depends on what ran before this
     * class and must not be hard coded.
     */
    private static final String STREAM_ID = "${streamId}";

    private static final String QUERY = """
            from index_view
            where UserId = user5
            select StreamId, EventId, UserId""";

    @Test
    void test() {
        basicTest(QUERY, null, 9, """
                "StreamId","EventId","UserId"
                "${streamId}","5","user5"
                "${streamId}","9","user5"
                "${streamId}","14","user5"
                "${streamId}","20","user5"
                "${streamId}","23","user5"
                """);
    }

    /**
     * A column that the user has hidden in the report editor must not be written to the report output.
     * See https://github.com/gchq/stroom/issues/4621.
     */
    @Test
    void testHiddenColumnIsNotWritten() {
        // Hide the middle column, as a user would with the 'Hide' option on the column header. The id is the one
        // that the query parser generates for that column. Hiding a middle rather than a trailing column also
        // proves that the remaining values stay aligned with their headings, as row values are produced for every
        // column, visible or not.
        final QueryTablePreferences queryTablePreferences = QueryTablePreferences
                .builder()
                .columns(List.of(Column
                        .builder()
                        .id("eventid-1")
                        .name("EventId")
                        .visible(false)
                        .build()))
                .build();

        basicTest(QUERY, queryTablePreferences, 9, """
                "StreamId","UserId"
                "${streamId}","user5"
                "${streamId}","user5"
                "${streamId}","user5"
                "${streamId}","user5"
                "${streamId}","user5"
                """);
    }

    /**
     * A report with no error feed, and no default error feed configured, cannot report its own failure through the
     * error feed. The failure must instead be recorded against the execution history so that it is visible in the
     * UI, and the schedule disabled so that it does not repeat silently on every scheduled execution.
     */
    @Test
    void testMissingErrorFeedIsRecordedAndDisablesTheSchedule() {
        final ReportDoc reportDoc = ReportDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(QUERY)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.CSV).build())
                .notifications(createNotificationConfig())
                .errorFeed(null)
                .build();
        final DocRef docRef = writeReport(reportDoc);
        final ExecutionSchedule executionSchedule = createExecutionSchedule(docRef);

        scheduledExecutorService.exec(reportExecutor);

        // No report should have been produced.
        analyticsDataSetup.checkStreamCount(8);

        // The failure must be visible in the execution history rather than only in the logs.
        final ResultPage<ExecutionHistory> history = executionScheduleDao.fetchExecutionHistory(
                new ExecutionHistoryRequest(
                        PageRequest.createDefault(),
                        Collections.emptyList(),
                        executionSchedule));
        assertThat(history.size()).isOne();
        assertThat(history.getValues().getFirst().getStatus()).isEqualTo(ExecutionHistory.STATUS_ERROR);

        // And the schedule must have been disabled so that it does not fail identically forever.
        final Optional<ExecutionSchedule> reloaded =
                executionScheduleDao.fetchScheduleByUuid(executionSchedule.getUuid());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().isEnabled()).isFalse();
    }

    /**
     * The base class only tidies up analytic rules and detections, so each test here must remove the report doc and
     * the report stream it created, else the doc count and stream count assertions fail for the next test.
     */
    @AfterEach
    void tidyUpReports() {
        reportStore.list().forEach(docRef -> reportStore.deleteDocument(docRef));
        metaService.find(FindMetaCriteria.createWithType(REPORT_STREAM_TYPE))
                .getValues()
                .forEach(meta -> metaService.delete(meta.getId()));
    }

    private void basicTest(final String query,
                           final QueryTablePreferences queryTablePreferences,
                           final int expectedStreams,
                           final String expectedContent) {
        final ReportDoc reportDoc = ReportDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.CSV).build())
                .notifications(createNotificationConfig())
                .errorFeed(analyticsDataSetup.getDetections())
                .queryTablePreferences(queryTablePreferences)
                .build();
        final DocRef docRef = writeReport(reportDoc);
        final ExecutionSchedule executionSchedule = createExecutionSchedule(docRef);

        assertThat(executionSchedule).isNotNull();

        // Now run the search process.
        scheduledExecutorService.exec(reportExecutor);

        // As we have created alerts ensure we now have more streams.
        testReportStream(expectedStreams, expectedContent);

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

    private ExecutionSchedule createExecutionSchedule(final DocRef docRef) {
        final long now = System.currentTimeMillis();
        return executionScheduleDao.createExecutionSchedule(ExecutionSchedule
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
    }

    private void testReportStream(final int expectedStreams,
                                  final String expectedContent) {
        analyticsDataSetup.checkStreamCount(expectedStreams);

        // As we have created alerts ensure we now have more streams.
        final Meta newestMeta = analyticsDataSetup.getNewestMeta();
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.trim()).isEqualTo(resolveStreamId(expectedContent).trim());

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

    /**
     * The report selects StreamId from the single events stream created by the setup, so replace the placeholder
     * in the expected content with the id that stream actually got.
     */
    private String resolveStreamId(final String expectedContent) {
        final ResultPage<Meta> events = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.EVENTS));
        assertThat(events.size()).isOne();
        return expectedContent.replace(STREAM_ID, Long.toString(events.getValues().getFirst().getId()));
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
                .errorFeed(sample.getErrorFeed())
                .queryTablePreferences(sample.getQueryTablePreferences())
                .build();
        reportStore.writeDocument(reportDoc);

        assertThat(reportStore.list().size()).isOne();
        assertThat(reportStore.list().getFirst().getUuid()).isEqualTo(docRef.getUuid());

        return docRef;
    }
}
