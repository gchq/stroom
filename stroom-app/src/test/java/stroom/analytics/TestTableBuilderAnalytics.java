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

import stroom.analytics.impl.TableBuilderAnalyticExecutor;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

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
class TestTableBuilderAnalytics extends AbstractAnalyticsTest {

    @Inject
    private TableBuilderAnalyticExecutor analyticsExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void testHavingCount() {
        final String query = """
                from index_view
                where UserId = user5
                eval count = count()
                eval EventTime = floorYear(EventTime)
                group by EventTime, UserId
                having count > 3
                select EventTime, UserId, count""";
        basicTest(query, 9, 2);
    }

    @Test
    void testHavingEquals() {
        final String query = """
                from index_view
                where UserId = user5
                eval count = count()
                eval EventTime = floorYear(EventTime)
                eval my_num = min(3)
                group by EventTime, UserId
                having count > 3 and my_num = 3
                select EventTime, UserId, count""";
        basicTest(query, 9, 2);
    }

    @Test
    void testWindowCount() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1y advance 1y
                group by UserId
                select UserId""";
        basicTest(query, 9, 3);
    }

    @Test
    void testWindowCountHaving() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1y advance 1y
                group by UserId
                having "period0" = 0
                select UserId""";
        basicTest(query, 9, 2);
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        final AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.TABLE_BUILDER)
                .analyticProcessConfig(TableBuilderAnalyticProcessConfig.builder()
                        .enabled(true)
                        .node(nodeInfo.getThisNodeName())
                        .timeToWaitForData(INSTANT)
                        .build())
                .notifications(createNotificationConfig())
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        writeRule(analyticRuleDoc);

        // Now run the search process.
        analyticsExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testDetectionsStream(expectedStreams, expectedRecords);
    }
}
