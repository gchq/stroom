package stroom.analytics;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.index.shared.IndexDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.processor.api.ProcessorResult;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.ContentImportService;
import stroom.test.StoreCreationTool;
import stroom.test.common.ProjectPathUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

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
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalyticsDataSetup {

    private final ContentImportService contentImportService;
    private final CommonTranslationTestHelper commonTranslationTestHelper;
    private final StoreCreationTool storeCreationTool;
    private final ViewStore viewStore;
    private final MetaService metaService;
    private final FeedStore feedStore;
    private final CommonTestScenarioCreator commonTestScenarioCreator;
    private final Store store;

    private DocRef detections;

    @Inject
    public AnalyticsDataSetup(final ContentImportService contentImportService,
                              final CommonTranslationTestHelper commonTranslationTestHelper,
                              final StoreCreationTool storeCreationTool,
                              final ViewStore viewStore,
                              final MetaService metaService,
                              final FeedStore feedStore,
                              final CommonTestScenarioCreator commonTestScenarioCreator,
                              final Store store) {
        this.contentImportService = contentImportService;
        this.commonTranslationTestHelper = commonTranslationTestHelper;
        this.storeCreationTool = storeCreationTool;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.feedStore = feedStore;
        this.commonTestScenarioCreator = commonTestScenarioCreator;
        this.store = store;
    }

    final void setup() {
        contentImportService.importStandardPacks();

        final Path resourcePath = ProjectPathUtil.resolveDir("stroom-app")
                .resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("TestSingleEventAlerts");

        // Add some data.
        addData();
        checkStreamCount(8);

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
    }

    public void addData() {
        // Add some data.
        commonTranslationTestHelper.setup();
        // Translate data.
        List<ProcessorResult> results = commonTranslationTestHelper.processAll();

        // 3 ref data streams plus our data streams
        int expectedTaskCount = 3 + 1;

        assertThat(results.size())
                .isEqualTo(expectedTaskCount);
        results.forEach(this::assertProcessorResult);
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
                final Meta meta = target.getMeta();

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
        List<ProcessorResult> results = commonTranslationTestHelper.processAll();

        assertThat(results.size())
                .isEqualTo(1);
        results.forEach(this::assertProcessorResult);
    }

    public void checkStreamCount(final int expectedStreamCount) {
        // Check we have the expected number of streams.
        assertThat(metaService.find(FindMetaCriteria.unlocked()).size()).isEqualTo(expectedStreamCount);
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
