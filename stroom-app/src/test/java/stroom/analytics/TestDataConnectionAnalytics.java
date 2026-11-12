/*
 * Copyright 2026 Crown Copyright
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
import stroom.analytics.impl.AnalyticRuleSearchRequestHelper;
import stroom.analytics.impl.AnalyticRuleStore;
import stroom.analytics.impl.ExecutionScheduleDao;
import stroom.analytics.impl.ReportExecutor;
import stroom.analytics.impl.ReportStore;
import stroom.analytics.impl.ScheduledExecutorService;
import stroom.analytics.impl.ScheduledQueryAnalyticExecutable;
import stroom.analytics.impl.StreamingAnalyticCache;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportSettings;
import stroom.analytics.shared.ScheduleBounds;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.docref.DocRef;
import stroom.docstore.impl.DocFinderModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Analytics over a CSV Data Connection: scheduled-query rules and reports run against it, and
/// streaming rules, which need a View, refuse it.
///
/// The CSV is sign-in events whose times are relative to when the test runs, so a rule's time range,
/// which is worked out against the time the rule runs, picks out the recent ones.
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
class TestDataConnectionAnalytics extends AbstractAnalyticsTest {

    // The stream type that ReportExecutor writes report output as.
    private static final String REPORT_STREAM_TYPE = "Report";

    // The start of the name of each Data Connection this class makes, so only those are removed.
    private static final String SIGN_INS = "Sign-ins ";

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("time", DataColumnType.DATE),
            new DataColumn("user", DataColumnType.TEXT),
            new DataColumn("outcome", DataColumnType.TEXT),
            new DataColumn("host", DataColumnType.TEXT));

    @Inject
    private DataConnectionStore dataConnectionStore;
    @Inject
    private AnalyticRuleStore analyticRuleStore;
    @Inject
    private ReportStore reportStore;
    @Inject
    private ScheduledQueryAnalyticExecutable analyticExecutable;
    @Inject
    private ScheduledExecutorService<AnalyticRuleDoc> ruleExecutorService;
    @Inject
    private ReportExecutor reportExecutor;
    @Inject
    private ScheduledExecutorService<ReportDoc> reportExecutorService;
    @Inject
    private ExecutionScheduleDao executionScheduleDao;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private StreamingAnalyticCache streamingAnalyticCache;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private MetaService metaService;
    @Inject
    private AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;

    private String signIns;

    /// Saves the sign-ins as a CSV Data Connection with no time column.
    @BeforeEach
    void createSignIns() {
        signIns = saveSignIns(null);
    }

    /// Removes what the tests made, as the base class expects only its own streams before each test,
    /// and the database is shared with other test classes.
    @AfterEach
    void tidyUp() {
        reportStore.list().forEach(docRef -> reportStore.deleteDocument(docRef));
        metaService.find(FindMetaCriteria.createWithType(REPORT_STREAM_TYPE))
                .getValues()
                .forEach(meta -> metaService.delete(meta.getId()));
        dataConnectionStore.list().stream()
                .filter(docRef -> docRef.getName() != null && docRef.getName().startsWith(SIGN_INS))
                .forEach(docRef -> dataConnectionStore.deleteDocument(docRef));
    }

    // Sign-in events relative to now: three failures by alice, two of them in the last hour, one
    // failure by bob two hours ago, and a success by carol.
    private String saveSignIns(final String timeField) {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        final String csv = "time,user,outcome,host\n"
                           + now.minus(Duration.ofMinutes(30)) + ",alice,failed,web-1\n"
                           + now.minus(Duration.ofMinutes(20)) + ",alice,failed,web-2\n"
                           + now.minus(Duration.ofMinutes(10)) + ",carol,success,web-1\n"
                           + now.minus(Duration.ofHours(2)) + ",bob,failed,web-1\n"
                           + now.minus(Duration.ofDays(3)) + ",alice,failed,web-3\n";
        final String name = SIGN_INS + UUID.randomUUID();
        final DocRef docRef = dataConnectionStore.createDocument(name);
        dataConnectionStore.writeDocument(dataConnectionStore.readDocument(docRef).copy()
                .sourceType(DataConnectionType.CSV)
                .csv(CsvSettings.builder()
                        .columns(COLUMNS)
                        .data(csv)
                        .build())
                .timeField(timeField)
                .build());
        return name;
    }

    private DocRef writeRuleWithTimeRange(final String query,
                                          final AnalyticProcessType processType,
                                          final TimeRange timeRange) {
        final DocRef docRef = analyticRuleStore.createDocument("Analytic Rule");
        analyticRuleStore.writeDocument(analyticRuleStore.readDocument(docRef).copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(processType)
                .notifications(new ArrayList<>(createNotificationConfig()))
                .errorFeed(analyticsDataSetup.getDetections())
                .build());
        return docRef;
    }

    // A schedule that runs once, now.
    private void scheduleNow(final DocRef docRef) {
        scheduleAt(docRef, Instant.now());
    }

    // A schedule that runs once, as if at the given time, which a rule's relative time range is
    // worked out against.
    private void scheduleAt(final DocRef docRef, final Instant runTime) {
        final long time = runTime.toEpochMilli();
        executionScheduleDao.createExecutionSchedule(ExecutionSchedule
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
                        .startTimeMs(time)
                        .endTimeMs(time)
                        .build())
                .owningDoc(docRef)
                .build());
    }

    private List<String> detectionsWritten() {
        // One stream more than the base class's eight, so the newest is the one the rule wrote.
        analyticsDataSetup.checkStreamCount(9);
        final String detections = readNewestStream();
        final List<String> records = new ArrayList<>(List.of(detections.split("<detection>")));
        // The text before the first record is the stream's header.
        records.removeFirst();
        return records;
    }

    @Test
    void stroomQlQueriesTheSignIns() {
        final SearchRequest sampleRequest = new SearchRequest(
                null, null, Query.builder().build(), null, DateTimeSettings.builder().build(), false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final SearchRequest request = searchRequestFactory.create("from \"" + signIns + "\"\n"
                                                                  + "where outcome = \"failed\"\n"
                                                                  + "group by user\n"
                                                                  + "sort by user\n"
                                                                  + "select user, count() as failures",
                sampleRequest, expressionContext);

        final SearchResponse response = resultStoreManager.search(request);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);

        assertThat(response.complete()).isTrue();
        final List<List<String>> rows = new ArrayList<>();
        for (final Result result : response.getResults()) {
            for (final Row row : ((TableResult) result).getRows()) {
                rows.add(row.getValues().subList(0, 2));
            }
        }
        assertThat(rows).containsExactly(List.of("alice", "3"), List.of("bob", "1"));
    }

    @Test
    void scheduledRuleWritesADetectionForEachMatchingRow() {
        scheduleNow(writeRuleWithTimeRange("from \"" + signIns + "\"\n"
                                           + "where outcome = \"failed\"\n"
                                           + "select time, user, host",
                AnalyticProcessType.SCHEDULED_QUERY,
                null));

        ruleExecutorService.exec(analyticExecutable);

        final List<String> detections = detectionsWritten();
        assertThat(detections).hasSize(4);
        assertThat(detections).filteredOn(detection -> detection.contains("alice")).hasSize(3);
        assertThat(detections).anySatisfy(detection -> assertThat(detection).contains("bob"));
        assertThat(detections).noneSatisfy(detection -> assertThat(detection).contains("carol"));
    }

    @Test
    void scheduledRuleTimeRangeIsWorkedOutAgainstTheRunTime() {
        // The run is scheduled as if 90 minutes ago, so its last hour is from 150 to 90 minutes ago:
        // only bob's failure two hours ago. Against the clock it would be alice's two recent ones.
        final String timedSignIns = saveSignIns("time");
        scheduleAt(writeRuleWithTimeRange("from \"" + timedSignIns + "\"\n"
                                          + "where outcome = \"failed\"\n"
                                          + "select time, user, host",
                        AnalyticProcessType.SCHEDULED_QUERY,
                        new TimeRange("Last hour", "now() - 1h", "now()")),
                Instant.now().minus(Duration.ofMinutes(90)));

        ruleExecutorService.exec(analyticExecutable);

        final List<String> detections = detectionsWritten();
        assertThat(detections).hasSize(1);
        assertThat(detections.getFirst()).contains("bob");
    }

    @Test
    void scheduledRuleTimeRangeFiltersOnTheTimeColumn() {
        // Run now, the last hour holds alice's two recent failures, not her one three days ago.
        final String timedSignIns = saveSignIns("time");
        scheduleNow(writeRuleWithTimeRange("from \"" + timedSignIns + "\"\n"
                                           + "where outcome = \"failed\"\n"
                                           + "select time, user, host",
                AnalyticProcessType.SCHEDULED_QUERY,
                new TimeRange("Last hour", "now() - 1h", "now()")));

        ruleExecutorService.exec(analyticExecutable);

        final List<String> detections = detectionsWritten();
        assertThat(detections).hasSize(2);
        assertThat(detections).allSatisfy(detection -> assertThat(detection).contains("alice"));
        assertThat(detections).anySatisfy(detection -> assertThat(detection).contains("web-1"));
        assertThat(detections).anySatisfy(detection -> assertThat(detection).contains("web-2"));
    }

    @Test
    void reportOfTheSignInsIsWritten() {
        final DocRef docRef = reportStore.createDocument("Failed sign-ins");
        reportStore.writeDocument(reportStore.readDocument(docRef).copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query("from \"" + signIns + "\"\n"
                       + "where outcome = \"failed\"\n"
                       + "group by user\n"
                       + "sort by user\n"
                       + "select user, count() as failures")
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.CSV).build())
                .notifications(new ArrayList<>(createNotificationConfig()))
                .errorFeed(analyticsDataSetup.getDetections())
                .build());
        scheduleNow(docRef);

        reportExecutorService.exec(reportExecutor);

        analyticsDataSetup.checkStreamCount(9);
        assertThat(readNewestStream().trim()).isEqualTo("""
                "user","failures"
                "alice","3"
                "bob","1\"""");
    }

    @Test
    void streamingRuleIsRefused() {
        // Streaming rules run over events as they are processed, so need a View; a Data Connection is
        // not one.
        final DocRef ruleRef = writeRuleWithTimeRange("from \"" + signIns + "\"\n"
                                                      + "where outcome = \"failed\"\n"
                                                      + "select time, user, host",
                AnalyticProcessType.STREAMING,
                null);
        // The same refusal is given for a rule whose data source is not found, so check it was found.
        assertThat(analyticRuleSearchRequestHelper.create(analyticRuleStore.readDocument(ruleRef))
                .getQuery()
                .getDataSource()
                .getType())
                .isEqualTo(DataConnectionDoc.TYPE);

        assertThatThrownBy(() -> streamingAnalyticCache.get(ruleRef))
                .hasStackTraceContaining("Rule needs to reference a view");
    }
}
