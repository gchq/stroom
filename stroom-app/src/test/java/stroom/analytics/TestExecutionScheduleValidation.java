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
import stroom.analytics.impl.ReportStore;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ScheduleBounds;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An enabled schedule that names no node, names a node that does not exist, or belongs to a document with no
 * resolvable error feed can never produce output and cannot report why, so enabling it must be refused.
 */
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
class TestExecutionScheduleValidation extends AbstractAnalyticsTest {

    @Inject
    private ExecutionScheduleResource executionScheduleResource;
    @Inject
    private ReportStore reportStore;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;

    private final List<ExecutionSchedule> created = new ArrayList<>();

    /**
     * The node_name column is not nullable, so this is rejected whether or not the schedule is enabled. The point
     * of validating it here is to replace the constraint violation with a message a user can act on.
     */
    @Test
    void scheduleWithNoNodeIsRejected() {
        final DocRef docRef = createReport(true);
        assertThatThrownBy(() -> create(schedule(docRef, true, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("processing node must be selected");
        assertThatThrownBy(() -> create(schedule(docRef, false, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("processing node must be selected");
    }

    @Test
    void enablingWithAnUnknownNodeIsRejected() {
        final DocRef docRef = createReport(true);
        assertThatThrownBy(() -> create(schedule(docRef, true, "no-such-node")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no-such-node");
    }

    @Test
    void enablingWithNoErrorFeedIsRejected() {
        final DocRef docRef = createReport(false);
        assertThatThrownBy(() -> create(schedule(docRef, true, nodeInfo.getThisNodeName())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no error feed has been set");
    }

    /**
     * A disabled schedule never runs, so it is allowed to have no error feed. This is what lets a rule created
     * before any defaults were configured be saved and worked on until its feeds have been chosen.
     */
    @Test
    void disabledScheduleWithNoErrorFeedIsAllowed() {
        final DocRef docRef = createReport(false);
        assertThat(create(schedule(docRef, false, nodeInfo.getThisNodeName()))).isNotNull();
    }

    @Test
    void enablingWithEverythingSetIsAllowed() {
        final DocRef docRef = createReport(true);
        assertThat(create(schedule(docRef, true, nodeInfo.getThisNodeName()))).isNotNull();
    }

    @AfterEach
    void tidyUp() {
        created.forEach(schedule -> executionScheduleResource.deleteExecutionSchedule(schedule));
        created.clear();
        reportStore.list().forEach(docRef -> reportStore.deleteDocument(docRef));
    }

    private ExecutionSchedule create(final ExecutionSchedule schedule) {
        final ExecutionSchedule result = executionScheduleResource.createExecutionSchedule(schedule);
        created.add(result);
        return result;
    }

    private ExecutionSchedule schedule(final DocRef owningDoc,
                                       final boolean enabled,
                                       final String nodeName) {
        final long now = System.currentTimeMillis();
        return ExecutionSchedule
                .builder()
                .name("Test")
                .enabled(enabled)
                .nodeName(nodeName)
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
                .owningDoc(owningDoc)
                .build();
    }

    private DocRef createReport(final boolean withErrorFeed) {
        final DocRef docRef = reportStore.createDocument("Test Report " + UUID.randomUUID());
        final ReportDoc reportDoc = reportStore.readDocument(docRef)
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query("from index_view select StreamId")
                .errorFeed(withErrorFeed
                        ? analyticsDataSetup.getDetections()
                        : null)
                .build();
        reportStore.writeDocument(reportDoc);
        return docRef;
    }
}
