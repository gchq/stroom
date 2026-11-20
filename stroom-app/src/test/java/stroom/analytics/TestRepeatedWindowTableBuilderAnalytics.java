/*
 * Copyright 2023 Crown Copyright
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
import stroom.analytics.impl.TableBuilderAnalyticExecutor;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.docstore.impl.DocFinderModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
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
class TestRepeatedWindowTableBuilderAnalytics extends AbstractAnalyticsTest {

    @Inject
    private TableBuilderAnalyticExecutor analyticsExecutor;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;

    // The test events of user5 are one in each of January, February, March, August and December
    // 2007, and the last of them to be read is the one in March. Data is kept for a day, which would
    // delete the row of March before the rule has judged it, as March is only closed by later
    // events. A window is kept until it has been judged, so March is judged when they arrive.
    @Test
    void testWindowIsKeptUntilItIsJudged() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                group by UserId
                having count() > 0
                select windowStart() as Month, UserId, count() as Events""";

        final AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.TABLE_BUILDER)
                .analyticProcessConfig(TableBuilderAnalyticProcessConfig.builder()
                        .enabled(true)
                        .node(nodeInfo.getThisNodeName())
                        .timeToWaitForData(INSTANT)
                        .dataRetention(SimpleDuration.builder().time(1).timeUnit(TimeUnit.DAYS).build())
                        .build())
                .notifications(createNotificationConfig())
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        writeRule(analyticRuleDoc);

        // January and February are closed, and are judged.
        analyticsExecutor.exec();
        testDetectionsStream(9, 3);
        assertThat(readNewestStream())
                .contains("2007-01-01T00:00:00.000Z")
                .contains("2007-02-01T00:00:00.000Z")
                .doesNotContain("2007-03-01T00:00:00.000Z");

        // Events of June 2008 close every month before it.
        analyticsDataSetup.addNewData(LocalDateTime.of(2008, 6, 15, 12, 0, 0));
        analyticsDataSetup.checkStreamCount(11);
        analyticsExecutor.exec();

        testDetectionsStream(12, 4);
        assertThat(readNewestStream())
                .contains("2007-03-01T00:00:00.000Z")
                .contains("2007-08-01T00:00:00.000Z")
                .contains("2007-12-01T00:00:00.000Z")
                .doesNotContain("2008-06-01T00:00:00.000Z");
    }
}
