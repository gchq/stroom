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

package stroom.analytics;


import stroom.analytics.impl.AnalyticNotificationDao;
import stroom.analytics.impl.AnalyticProcessorFilterDao;
import stroom.analytics.impl.AnalyticsExecutor;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.index.mock.MockIndexShardWriterExecutorModule;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.BootstrapTestModule;
import stroom.test.StroomIntegrationTest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(UriFactoryModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(MockSecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
@IncludeModule(MockIndexShardWriterExecutorModule.class)
class TestRepeatedAnalytics extends StroomIntegrationTest {

    private static boolean doneSetup;

    @Inject
    private MetaService metaService;
    @Inject
    private Store streamStore;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private AnalyticRuleStore analyticRuleStore;
    @Inject
    private AnalyticsExecutor analyticsExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private AnalyticProcessorFilterDao analyticProcessorFilterDao;
    @Inject
    private AnalyticNotificationDao analyticNotificationDao;

    private static DocRef detections;

    @BeforeEach
    final void setup() {
        if (!doneSetup) {
            analyticsDataSetup.setup();

            // Create somewhere to put the alerts.
            detections = analyticsDataSetup.getDetections();

            doneSetup = true;
        }
        // Delete existing detections.
        final ResultPage<Meta> metaList = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.DETECTIONS));
        for (final Meta meta : metaList.getValues()) {
            metaService.delete(meta.getId());
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    @Test
    void testHavingCount() {
        // Add alert
        final String query = """
                from index_view
                where UserId = user5
                eval count = count()
                eval EventTime = floorYear(EventTime)
                group by EventTime, UserId
                having count > 3
                select EventTime, UserId, count""";

        // Create the rule.
        final DocRef alertRuleDocRef = analyticRuleStore.createDocument("Threshold Event Rule");
        AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(alertRuleDocRef);
        analyticRuleDoc = analyticRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticRuleType(AnalyticRuleType.AGGREGATE)
                .build();
        analyticRuleDoc = analyticRuleStore.writeDocument(analyticRuleDoc);
        createProcessorFilters(analyticRuleDoc);
        createNotification(analyticRuleDoc, 1);

        // Now run the search process.
        analyticsExecutor.exec();
        analyticsDataSetup.checkStreamCount(9);

        // As we have created alerts ensure we now have more streams.
        Meta newestMeta = analyticsDataSetup.getNewestMeta();
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(2);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

//        // We need to sleep for a bit so that the new data is included in the processing window.
//        ThreadUtil.sleep(5000);

        // Load more data and test again.
        // Add some data.
        final LocalDateTime localDateTime = LocalDateTime.now().minusSeconds(10);
        analyticsDataSetup.addNewData(localDateTime);
        analyticsDataSetup.checkStreamCount(11);

//        // We need to sleep for a bit so that the new data is included in the processing window.
//        ThreadUtil.sleep(5000);

        // Run alert executor.
        analyticsExecutor.exec();
        analyticsDataSetup.checkStreamCount(12);

        // As we have created alerts ensure we now have more streams.
        newestMeta = analyticsDataSetup.getNewestMeta();
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(2);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void createProcessorFilters(final AnalyticRuleDoc analyticRuleDoc) {
        final AnalyticProcessorFilter filter = AnalyticProcessorFilter.builder()
                .version(1)
                .createTimeMs(System.currentTimeMillis())
                .updateTimeMs(System.currentTimeMillis())
                .createUser("test")
                .updateUser("test")
                .analyticUuid(analyticRuleDoc.getUuid())
                .enabled(true)
                .expression(ExpressionOperator.builder().addTerm(MetaFields.FIELD_TYPE,
                        Condition.EQUALS,
                        StreamTypeNames.EVENTS).build())
                .minMetaCreateTimeMs(0L)
                .maxMetaCreateTimeMs(Long.MAX_VALUE)
                .node(nodeInfo.getThisNodeName())
                .build();
        analyticProcessorFilterDao.create(filter);
    }

    private void createNotification(final AnalyticRuleDoc analyticRuleDoc,
                                    final int timeToWaitSeconds) {
        final AnalyticNotificationConfig config = AnalyticNotificationStreamConfig.builder()
                .timeToWaitForData(SimpleDuration.builder().time(timeToWaitSeconds).timeUnit(TimeUnit.SECONDS).build())
                .destinationFeed(detections)
                .useSourceFeedIfPossible(true)
                .build();
        final AnalyticNotification notification = AnalyticNotification.builder()
                .version(1)
                .createTimeMs(System.currentTimeMillis())
                .updateTimeMs(System.currentTimeMillis())
                .createUser("test")
                .updateUser("test")
                .analyticUuid(analyticRuleDoc.getUuid())
                .enabled(true)
                .config(config)
                .build();
        analyticNotificationDao.create(notification);
    }
}
