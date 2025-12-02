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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class TestRepeatedTableBuilderAnalytics extends AbstractAnalyticsTest {

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

        // Create the rule.
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
        testDetectionsStream(9, 2);

        // Load more data and test again.
        // Add some data.
        final LocalDateTime localDateTime = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10);
        analyticsDataSetup.addNewData(localDateTime);
        analyticsDataSetup.checkStreamCount(11);

        // Run alert executor.
        analyticsExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testDetectionsStream(12, 2);
    }
}
