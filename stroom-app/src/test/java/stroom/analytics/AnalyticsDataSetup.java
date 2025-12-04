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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.api.FeedStore;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorExpressionUtil;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.ContentStoreTestSetup;
import stroom.test.StoreCreationTool;
import stroom.test.common.ProjectPathUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.assertj.core.api.Assertions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class AnalyticsDataSetup {

    private final ContentStoreTestSetup contentStoreTestSetup;
    private final CommonTranslationTestHelper commonTranslationTestHelper;
    private final StoreCreationTool storeCreationTool;
    private final ViewStore viewStore;
    private final MetaService metaService;
    private final FeedStore feedStore;
    private final CommonTestScenarioCreator commonTestScenarioCreator;
    private final Store store;
    private final ProcessorService processorService;
    private final ProcessorFilterService processorFilterService;
    private final IndexShardManager indexShardManager;

    private DocRef detections;

    @Inject
    public AnalyticsDataSetup(final ContentStoreTestSetup contentStoreTestSetup,
                              final CommonTranslationTestHelper commonTranslationTestHelper,
                              final StoreCreationTool storeCreationTool,
                              final ViewStore viewStore,
                              final MetaService metaService,
                              final FeedStore feedStore,
                              final CommonTestScenarioCreator commonTestScenarioCreator,
                              final Store store,
                              final ProcessorService processorService,
                              final ProcessorFilterService processorFilterService,
                              final IndexShardManager indexShardManager) {
        this.contentStoreTestSetup = contentStoreTestSetup;
        this.commonTranslationTestHelper = commonTranslationTestHelper;
        this.storeCreationTool = storeCreationTool;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.feedStore = feedStore;
        this.commonTestScenarioCreator = commonTestScenarioCreator;
        this.store = store;
        this.processorService = processorService;
        this.processorFilterService = processorFilterService;
        this.indexShardManager = indexShardManager;
    }

    final void setup() {
        contentStoreTestSetup.installStandardPacks();

        final Path resourcePath = ProjectPathUtil.resolveDir("stroom-app")
                .resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("TestSingleEventAlerts");

        // Add some data.
        addData();
        checkStreamCount(8);

        // Add the index.
        final DocRef indexDocRef = addIndex(resourcePath);
        checkStreamCount(8);

        // Add extraction pipeline.
        final DocRef searchResultPipeline = storeCreationTool.getSearchResultPipeline(
                "Search result",
                resourcePath.resolve("dynamic-result-pipeline.json"),
                resourcePath.resolve("dynamic-index.xsl"));

        // Add view.
        final DocRef viewDocRef = viewStore.createDocument("index_view");
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        viewDoc.setDataSource(indexDocRef);
        viewDoc.setPipeline(searchResultPipeline);
        viewDoc.setFilter(ExpressionOperator.builder()
                .addTextTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.EVENTS)
                .build());
        viewStore.writeDocument(viewDoc);

        // Create somewhere to put the alerts.
        detections = feedStore.createDocument("DETECTIONS");
    }

    public void addData() {
        // Add some data.
        commonTranslationTestHelper.setup();
        // Translate data.
        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();

        // 3 ref data streams plus our data streams
        final int expectedTaskCount = 3 + 1;

        assertThat(results.size())
                .isEqualTo(expectedTaskCount);
        results.forEach(this::assertProcessorResult);
    }

    public DocRef addIndex(final Path resourcePath) {
        // Add the index.
        final DocRef indexDocRef = commonTestScenarioCreator.createIndex(
                "Test index",
                List.of(),
                LuceneIndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);

        // Create index pipeline.
        final DocRef indexPipeline = storeCreationTool.getIndexPipeline(
                "Dynamic Index",
                resourcePath.resolve("indexing-pipeline.json"),
                resourcePath.resolve("dynamic-index.xsl"),
                indexDocRef);

        final Processor streamProcessor = processorService.find(new ExpressionCriteria(
                        ProcessorExpressionUtil.createPipelineExpression(indexPipeline)))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor filter.
            final QueryData findStreamQueryData = QueryData.builder()
                    .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                    .expression(ExpressionOperator.builder()
                            .addTextTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.EVENTS)
                            .build())
                    .build();
            processorFilterService.create(
                    CreateProcessFilterRequest
                            .builder()
                            .pipeline(indexPipeline)
                            .queryData(findStreamQueryData)
                            .priority(1)
                            .build());
        }

        // Translate data.
        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();
        assertThat(results.size()).isEqualTo(1);

        results.forEach(this::assertProcessorResult);

        // Flush all newly created index shards.
        indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.FLUSH);

        return indexDocRef;
    }

    public void addNewData(final LocalDateTime localDateTime) {
        try {
            final String dateTime = localDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy,HH:mm:ss"));
            final Path path = CommonTranslationTestHelper.VALID_RESOURCE_NAME;
            final String data = Files.readString(path);
            final Pattern pattern = Pattern.compile("\n\\d{2}/\\d{2}/\\d{4},\\d{2}:\\d{2}:\\d{2}");
            final String newData = pattern.matcher(data).replaceAll("\n" + dateTime);

            // Add the associated data to the stream store.
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(CommonTranslationTestHelper.FEED_NAME)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .build();
            try (final Target target = store.openTarget(metaProperties)) {
                try (final OutputStreamProvider outputStreamProvider = target.next()) {
                    try (final InputStream inputStream =
                            new ByteArrayInputStream(newData.getBytes(StandardCharsets.UTF_8));
                            final SegmentOutputStream outputStream = outputStreamProvider.get()) {
                        StreamUtil.streamToStream(inputStream, outputStream);
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        // Translate data.
        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();

        assertThat(results.size())
                .isEqualTo(1);
        results.forEach(this::assertProcessorResult);
    }

    public void checkStreamCount(final int expectedStreamCount) {
        // Check we have the expected number of streams.
        assertThat(getStreamCount()).isEqualTo(expectedStreamCount);
    }

    public int getStreamCount() {
        return metaService.find(FindMetaCriteria.unlocked()).size();
    }

    public Meta getNewestMeta() {
        final ResultPage<Meta> resultPage = metaService.find(FindMetaCriteria.unlocked());
        return resultPage.getValues().get(resultPage.size() - 1);
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

    public DocRef getDetections() {
        return detections;
    }
}
