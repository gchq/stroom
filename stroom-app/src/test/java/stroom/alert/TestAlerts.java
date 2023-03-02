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

package stroom.alert;


import stroom.alert.impl.AlertManagerImpl;
import stroom.alert.impl.ResultStoreAlertSearchExecutor;
import stroom.alert.rule.impl.AlertRuleStore;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleType;
import stroom.alert.rule.shared.QueryLanguageVersion;
import stroom.alert.rule.shared.ThresholdAlertRule;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.index.VolumeTestConfigModule;
import stroom.index.mock.MockIndexShardWriterExecutorModule;
import stroom.index.shared.IndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.processor.api.ProcessorResult;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.BootstrapTestModule;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.ContentImportService;
import stroom.test.StoreCreationTool;
import stroom.test.StroomIntegrationTest;
import stroom.test.common.ProjectPathUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
class TestAlerts extends StroomIntegrationTest {

    private static boolean doneSetup;
    @Inject
    private ContentImportService contentImportService;
    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private ViewStore viewStore;
    @Inject
    private MetaService metaService;
    @Inject
    private Store streamStore;
    @Inject
    private AlertManagerImpl alertManager;
    @Inject
    private AlertRuleStore alertRuleStore;
    @Inject
    private FeedStore feedStore;
    @Inject
    private ResultStoreAlertSearchExecutor resultStoreAlertSearchExecutor;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;

    private static DocRef detections;

    @BeforeEach
    final void setup() {
        if (!doneSetup) {
            contentImportService.importStandardPacks();

            final Path resourcePath = ProjectPathUtil.resolveDir("stroom-app")
                    .resolve("src")
                    .resolve("test")
                    .resolve("resources")
                    .resolve("TestSingleEventAlerts");

            // Add some data.
            commonTranslationTestHelper.setup();
            // Translate data.
            List<ProcessorResult> results = commonTranslationTestHelper.processAll();

            // 3 ref data streams plus our data streams
            int expectedTaskCount = 3 + 1;

            assertThat(results.size())
                    .isEqualTo(expectedTaskCount);
            results.forEach(this::assertProcessorResult);

            // Check we have the expected number of streams.
            assertThat(metaService.find(FindMetaCriteria.unlocked()).size()).isEqualTo(8);

            // Add the index.
            final DocRef indexDocRef = commonTestScenarioCreator.createIndex(
                    "Test index",
                    List.of(),
                    IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);

            // Create index pipeline.
            final DocRef indexPipeline = storeCreationTool.getIndexPipeline(
                    "Dynamic Index",
                    resourcePath.resolve("indexing-pipeline.xml"),
                    resourcePath.resolve("dynamic-index.xsl"),
                    indexDocRef);

            // Add extraction pipeline.
            final DocRef searchResultPipeline = storeCreationTool.getSearchResultPipeline(
                    "Search result",
                    resourcePath.resolve("dynamic-result-pipeline.xml"),
                    resourcePath.resolve("dynamic-index.xsl"));

            // Add view.
            final DocRef viewDocRef = viewStore.createDocument("index_view");
            ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
            viewDoc.setDataSource(indexDocRef);
            viewDoc.setPipeline(searchResultPipeline);
            viewDoc.setFilter(ExpressionOperator.builder()
                    .addTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.EVENTS)
                    .build());
            viewStore.writeDocument(viewDoc);

            // Create somewhere to put the alerts.
            detections = feedStore.createDocument("DETECTIONS");

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
    void testSingleEvent() {
        // Add alert
        final String query = """
                "index_view"
                | where UserId = user5
                | table StreamId, EventId, UserId""";

        final DocRef alertRuleDocRef = alertRuleStore.createDocument("Threshold Event Rule");
        AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(alertRuleDocRef);
        alertRuleDoc = alertRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .enabled(true)
                .alertRuleType(AlertRuleType.EVENT)
                .build();
        alertRuleStore.writeDocument(alertRuleDoc);

        // Now run the search process.
        resultStoreAlertSearchExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        final ResultPage<Meta> resultPage = metaService.find(FindMetaCriteria.unlocked());
        assertThat(resultPage.size()).isEqualTo(9);

        final Meta newestMeta = resultPage.getValues().get(resultPage.size() - 1);
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(6);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testHavingCount() {
        // Add alert
        final String query = """
                "index_view"
                | where UserId = user5
                | eval count = count()
                | eval EventTime = floorYear(EventTime)
                | group by EventTime, UserId
                | having count > 3
                | table EventTime, UserId, count""";

        // Create the rule.
        final ThresholdAlertRule thresholdAlertRule = ThresholdAlertRule.builder()
                .timeField("EventTime")
                .executionDelay("PT1S")
                .executionFrequency("PT1S")
                .destinationFeed(detections)
                .build();

        final DocRef alertRuleDocRef = alertRuleStore.createDocument("Threshold Event Rule");
        AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(alertRuleDocRef);
        alertRuleDoc = alertRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .enabled(true)
                .alertRuleType(AlertRuleType.AGGREGATE)
                .alertRule(thresholdAlertRule)
                .build();
        alertRuleStore.writeDocument(alertRuleDoc);

        // Now run the search process.
        resultStoreAlertSearchExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        final ResultPage<Meta> resultPage = metaService.find(FindMetaCriteria.unlocked());
        final Meta newestMeta = resultPage.getValues().get(resultPage.size() - 1);
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(2);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testWindowCount() {
        // Add alert
        final String query = """
                "index_view"
                | where UserId = user5
                | window EventTime by P12M advance P12M
                | group by UserId
                //| having count > countPrevious
                | table UserId""";

        // Create the rule.
        final ThresholdAlertRule thresholdAlertRule = ThresholdAlertRule.builder()
                .timeField("EventTime")
                .executionDelay("PT1S")
                .executionFrequency("PT1S")
                .destinationFeed(detections)
                .build();

        final DocRef alertRuleDocRef = alertRuleStore.createDocument("Threshold Event Rule");
        AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(alertRuleDocRef);
        alertRuleDoc = alertRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .enabled(true)
                .alertRuleType(AlertRuleType.AGGREGATE)
                .alertRule(thresholdAlertRule)
                .build();
        alertRuleStore.writeDocument(alertRuleDoc);

        // Now run the search process.
        resultStoreAlertSearchExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        final ResultPage<Meta> resultPage = metaService.find(FindMetaCriteria.unlocked());
        assertThat(resultPage.size()).isEqualTo(9);

        final Meta newestMeta = resultPage.getValues().get(resultPage.size() - 1);
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(3);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testWindowCountHaving() {
        // Add alert
        final String query = """
                "index_view"
                | where UserId = user5
                | window EventTime by P12M advance P12M
                | group by UserId
                | having "period-0" = 0
                | table UserId""";

        // Create the rule.
        final ThresholdAlertRule thresholdAlertRule = ThresholdAlertRule.builder()
                .timeField("EventTime")
                .executionDelay("PT1S")
                .executionFrequency("PT1S")
                .destinationFeed(detections)
                .build();

        final DocRef alertRuleDocRef = alertRuleStore.createDocument("Threshold Event Rule");
        AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(alertRuleDocRef);
        alertRuleDoc = alertRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .enabled(true)
                .alertRuleType(AlertRuleType.AGGREGATE)
                .alertRule(thresholdAlertRule)
                .build();
        alertRuleStore.writeDocument(alertRuleDoc);

        // Now run the search process.
        resultStoreAlertSearchExecutor.exec();

        // As we have created alerts ensure we now have more streams.
        final ResultPage<Meta> resultPage = metaService.find(FindMetaCriteria.unlocked());
        assertThat(resultPage.size()).isEqualTo(9);

        final Meta newestMeta = resultPage.getValues().get(resultPage.size() - 1);
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<record>").length).isEqualTo(2);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertProcessorResult(final ProcessorResult result) {
        Assertions.assertThat(result.getMarkerCount(Severity.SEVERITIES))
                .withFailMessage("Found errors")
                .isEqualTo(0);
        Assertions.assertThat(result.getWritten())
                .withFailMessage("Write count should be > 0")
                .isGreaterThan(0);
        Assertions.assertThat(result.getRead())
                .withFailMessage("Read could should be <= write count")
                .isLessThanOrEqualTo(result.getWritten());
    }
}
