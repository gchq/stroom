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

import stroom.analytics.impl.StreamingAnalyticExecutor;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.index.VolumeTestConfigModule;
import stroom.index.mock.MockIndexShardWriterExecutorModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.BootstrapTestModule;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
class TestStreamingAnalytics extends AbstractAnalyticsTest {

    @Inject
    private StreamingAnalyticExecutor analyticsExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void testSingleEvent() {
        final String query = """
                from index_view
                where UserId = user5
                select StreamId, EventId, UserId""";
        basicTest(query, 9, 6);
    }

    @Test
    void testMissingField() {
        final String query = """
                from index_view
                where UserId = user5
                and MissingField = bob
                select StreamId, EventId, UserId""";
        basicTest(query, 8, 1);
    }

    @Test
    void testNoWhere() {
        final String query = """
                from index_view
                select StreamId, EventId, UserId""";
        basicTest(query, 9, 27);
    }

    @Test
    void testCompoundWhere() {
        final String query = """
                from index_view
                where UserId = user5 and  (UserId = user5 or MissingField = bob)
                select StreamId, EventId, UserId""";
        basicTest(query, 9, 6);
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.STREAMING)
                .analyticProcessConfig(new StreamingAnalyticProcessConfig(
                        true,
                        nodeInfo.getThisNodeName(),
                        analyticsDataSetup.getDetections(),
                        null,
                        null))
                .analyticNotificationConfig(createNotificationConfig())
                .build();
        writeRule(analyticRuleDoc);

        // Now run the search process.
        analyticsExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testDetectionsStream(expectedStreams, expectedRecords);
    }
}
