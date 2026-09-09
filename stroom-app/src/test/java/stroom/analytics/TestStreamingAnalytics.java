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
import stroom.analytics.impl.AnalyticRuleProcessors;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.docstore.impl.DocFinderModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.resource.impl.ResourceModule;
import stroom.test.BootstrapTestModule;
import stroom.test.CommonTranslationTestHelper;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
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
class TestStreamingAnalytics extends AbstractAnalyticsTest {

    @Inject
    private AnalyticsDataSetup analyticsDataSetup;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private AnalyticRuleProcessors analyticRuleProcessors;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private MetaService metaService;

    private static final String QUERY = """
            from index_view
            where UserId = user5
            select StreamId, EventId, UserId""";

    @Test
    void testSingleEvent() {
        basicTest(QUERY, 9, 6);
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

    /**
     * A streaming rule asked to use the source feed writes its detections back to the feed the events came
     * from, rather than to the destination feed configured on the notification.
     */
    @Test
    void testUseSourceFeedWritesDetectionsToTheSourceFeed() {
        runRule(QUERY, true);

        assertThat(newestDetectionsFeedName())
                .isEqualTo(CommonTranslationTestHelper.FEED_NAME);
    }

    /**
     * The control case. Without the option the detections go to the destination feed as normal.
     */
    @Test
    void testWithoutUseSourceFeedWritesDetectionsToTheDestinationFeed() {
        runRule(QUERY, false);

        assertThat(newestDetectionsFeedName())
                .isEqualTo(analyticsDataSetup.getDetections().getName());
    }

    /**
     * @return The feed the most recent detections stream was written to.
     */
    private String newestDetectionsFeedName() {
        final ResultPage<Meta> detections = metaService.find(
                FindMetaCriteria.createWithType(StreamTypeNames.DETECTIONS));
        assertThat(detections.size())
                .withFailMessage("Expected exactly one detections stream")
                .isOne();
        return detections.getValues().getFirst().getFeedName();
    }

    private void runRule(final String query,
                         final boolean useSourceFeedIfPossible) {
        final NotificationConfig notificationConfig = NotificationConfig
                .builder()
                .destinationType(NotificationDestinationType.STREAM)
                .destination(NotificationStreamDestination
                        .builder()
                        .destinationFeed(analyticsDataSetup.getDetections())
                        .useSourceFeedIfPossible(useSourceFeedIfPossible)
                        .build())
                .build();
        basicTest(query, List.of(notificationConfig));
    }

    private void basicTest(final String query,
                           final int expectedStreams,
                           final int expectedRecords) {
        basicTest(query, createNotificationConfig());
        testDetectionsStream(expectedStreams, expectedRecords);
    }

    private void basicTest(final String query,
                           final List<NotificationConfig> notifications) {
        final AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .analyticProcessType(AnalyticProcessType.STREAMING)
                .notifications(notifications)
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
        final DocRef analyticRuleDocRef = writeRule(analyticRuleDoc);
        final ExpressionOperator expressionOperator = analyticRuleProcessors
                .getDefaultProcessingFilterExpression(query);
        final QueryData queryData = QueryData
                .builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(expressionOperator)
                .build();

        // Now create the processor filter using the find stream criteria.
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .processorType(ProcessorType.STREAMING_ANALYTIC)
                .pipeline(analyticRuleDocRef)
                .queryData(queryData)
                .autoPriority(true)
                .enabled(true)
                .minMetaCreateTimeMs(0L)
                .maxMetaCreateTimeMs(Long.MAX_VALUE)
                .build();
        processorFilterService.create(request);

        // Now run the processing.
        commonTranslationTestHelper.processAll();
    }
}
