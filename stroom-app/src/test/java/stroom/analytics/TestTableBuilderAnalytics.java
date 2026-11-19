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
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
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

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // The test events of user5 are one in each of January, February, March, August and December
    // 2007, and the last of them to be read is the one in March. A rule judges a window only when it
    // is closed, so January and February are judged and March, which may still be filling, is not.
    @Test
    void testWindowCount() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                eval count = count()
                group by UserId
                having count > 0
                select windowStart() as Month, UserId, count""";
        basicTest(query, 9, 3);
        assertThat(readNewestStream())
                .contains("2007-01-01T00:00:00.000Z")
                .contains("2007-02-01T00:00:00.000Z")
                .doesNotContain("2007-03-01T00:00:00.000Z");
    }

    // The count is written in the having clause, and only January has more events than the month
    // before it.
    @Test
    void testWindowHavingExpression() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                group by UserId
                having count() > 0 and count() >= lag(count(), 1, 0) + 1
                select windowStart() as Month, UserId, count() as Events""";
        basicTest(query, 9, 2);
    }

    // The month before January is outside the range of the data, so lag gives its default, where a
    // month inside the range with no events would give zero.
    @Test
    void testWindowLag() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                eval before = lag(count(), 1, -1)
                group by UserId
                having before = -1
                select windowStart() as Month, UserId, before""";
        basicTest(query, 9, 2);
    }

    // A row that reads a later window with lead is judged only when that window is closed too.
    // January reads February, which is closed, and is judged. February reads March, which may still
    // be filling, and is not.
    @Test
    void testWindowLead() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                eval after = lead(count(), 1, -1)
                group by UserId
                having count() > 0
                select windowStart() as Month, UserId, count() as Events, after""";
        basicTest(query, 9, 2);
        assertThat(readNewestStream())
                .contains("2007-01-01T00:00:00.000Z")
                .doesNotContain("2007-02-01T00:00:00.000Z");
    }

    // A detection holds each column of the select clause once, and none of the hidden columns that
    // the window and having clauses add.
    @Test
    void testWindowDetectionValuesAreNotDuplicated() {
        final String query = """
                from index_view
                where UserId = user5
                window EventTime by 1M
                eval before = lag(count(), 1, 0)
                group by UserId
                having count() > before and before = 0
                select windowStart() as Month, UserId, count() as Events, before""";
        basicTest(query, 9, 2);

        final List<String> names = new ArrayList<>();
        final String detections = readNewestStream();
        final Matcher matcher = Pattern.compile("<value name=\"([^\"]*)\">").matcher(detections);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        assertThat(names).as(detections).containsExactly("Month", "UserId", "Events", "before");
    }

    /**
     * A table builder rule builds its detections in a different place to a scheduled query, so the level and
     * status a rule declares have to reach this path too.
     */
    @Test
    void testLevelAndStatusIncludedInDetections() {
        final String query = """
                from index_view
                where UserId = user5
                eval count = count()
                eval EventTime = floorYear(EventTime)
                group by EventTime, UserId
                having count > 3
                select EventTime, UserId, count""";
        writeRule(ruleBuilder(query)
                .level(AnalyticRuleLevel.CRITICAL)
                .status(AnalyticRuleStatus.EXPERIMENTAL)
                .build());

        analyticsExecutor.exec();

        assertThat(readNewestStream())
                .contains("<level>Critical</level>")
                .contains("<status>Experimental</status>");
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        writeRule(ruleBuilder(query).build());

        // Now run the search process.
        analyticsExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        testDetectionsStream(expectedStreams, expectedRecords);
    }

    private AnalyticRuleDoc.Builder ruleBuilder(final String query) {
        return AnalyticRuleDoc.builder()
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
                .errorFeed(analyticsDataSetup.getDetections());
    }
}
