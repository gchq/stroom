package stroom.receive;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.core.receive.ProxyAggregationExecutor;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaService;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.proxy.repo.FileSetProcessor;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore //Only meant to be run manually
public class ManualProxyAggregationTest {

    // The aim of this test class is to be able to run the proxy aggregation
    // and manually kill it part way through and then run it again to ensure
    // it recovers correctly.

    private final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestProxyAggregationTask.class);
    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyAggregationTask.class);

    @Inject
    private MetaService metaService;
    @Inject
    private Store streamStore;
    @Inject
    private FeedStore feedService;
    @Inject
    private MetaStatistics metaDataStatistic;
    @Inject
    private TaskContext taskContext;
    @Inject
    private ExecutorProvider executorProvider;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private Provider<FileSetProcessor> filePackProcessorProvider;
    @Inject
    private CommonTestControl commonTestControl;

    private static final int FEED_COUNT = 5;
    private static final int ENTRIES_PER_ZIP = 10;
    private static final int ZIP_FILE_COUNT = 500;
    private static final int MAX_ENTRIES_PER_OUTPUT_FILE = 2;
    private static final String TEST_DIR_NAME = "manual-aggregation-test";


    @Test
    public void clean() {
        commonTestControl.teardown();
        FileUtil.deleteContents(getProxyDir());
        FileUtil.deleteDir(getProxyDir());
        commonTestControl.setup();
    }


    @Test
    public void generateInputFiles() {
        // Generate the feeds to use in the test
        final List<String> eventFeeds = createFeeds(FEED_COUNT);

        final Path proxyDir = createProxyDirectory();

        generateTestFiles(proxyDir, ENTRIES_PER_ZIP, ZIP_FILE_COUNT, eventFeeds);
    }

    @Test
    public void runProxyAggregation() {
        // Generate the feeds to use in the test
        final List<String> eventFeeds = getFeedNames(FEED_COUNT);

        final Path proxyDir = getProxyDir();

        aggregateAndAssert(
                FEED_COUNT,
                ENTRIES_PER_ZIP,
                ZIP_FILE_COUNT,
                MAX_ENTRIES_PER_OUTPUT_FILE,
                proxyDir,
                eventFeeds);
    }

    private Path getProxyDir() {
        final Path tmp = Paths.get("/tmp");
        return tmp.resolve(TEST_DIR_NAME);
    }


    private Path createProxyDirectory() {
        final Path proxyDir = getProxyDir();

        try {
            Files.createDirectory(proxyDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return proxyDir;
    }

    private void generateTestFiles(final Path proxyDir, final int entriesPerZip, final int zipFileCount, final List<String> eventFeeds) {
        IntStream.rangeClosed(1, zipFileCount)
                .parallel()
                .forEach(i -> {
                    String filename = String.format("%03d.zip", i);
                    final Path testFile = proxyDir.resolve(filename);
                    try {
                        writeTestFileWithManyEntries(testFile, eventFeeds, entriesPerZip);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private List<String> getFeedNames(final int feedCount) {
        return IntStream.rangeClosed(1, feedCount)
                .boxed()
                .map(i ->
                        String.format("TEST_FEED_%03d", i)
                )
                .collect(Collectors.toList());
    }

    private List<String> createFeeds(final int feedCount) {
        return getFeedNames(feedCount)
                .stream()
                .map(name -> {
                    DocRef feedRef = feedService.createDocument(name);
                    FeedDoc feed = feedService.readDocument(feedRef);
                    feed.setDescription(name);
                    feed.setStatus(FeedStatus.RECEIVE);
                    feed.setStreamType(StreamTypeNames.RAW_EVENTS);
                    feed = feedService.writeDocument(feed);
                    LOGGER.debug("Created feed {}", feed.getName());
                    return feed.getName();
                })
                .collect(Collectors.toList());
    }

//    private List<String> getFeeds(final int feedCount) {
//        return getFeedNames(feedCount)
//                .stream()
//                .map(name -> {
//                    Feed feed = feedService.loadByName(name);
//                    return feed;
//                })
//                .collect(Collectors.toList());
//    }

    public Path getCurrentTestDir() {
        return FileUtil.getTempDir();
    }

    private void writeTestFileWithManyEntries(final Path testFile, final List<String> eventFeeds, final int count)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(testFile)));

        LAMBDA_LOGGER.debug(() ->
                LogUtil.message("Creating file {}", testFile.toAbsolutePath().toString()));

        int feedIdx = 0;

        for (int i = 1; i <= count; i++) {
            feedIdx = ++feedIdx >= eventFeeds.size() ? 0 : feedIdx;
            final String eventFeed = eventFeeds.get(feedIdx);

            LAMBDA_LOGGER.debug(() ->
                    LogUtil.message("Using feed {}", eventFeed));

            final String name = String.valueOf(i);
            zipOutputStream.putNextEntry(new ZipEntry(name + ".hdr"));
            PrintWriter printWriter = new PrintWriter(zipOutputStream);
            printWriter.println("Feed:" + eventFeed);
            printWriter.println("Proxy:ProxyTest");
            printWriter.println("StreamSize:" + name.getBytes().length);
            printWriter.println("ReceivedTime:2010-01-01T00:00:00.000Z");
            printWriter.flush();
            zipOutputStream.closeEntry();

            zipOutputStream.putNextEntry(new ZipEntry(name + ".dat"));
            printWriter = new PrintWriter(zipOutputStream);
            printWriter.print(name);
            printWriter.flush();
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();
    }

    private void aggregateAndAssert(final int feedCount,
                                    final int entriesPerInputFile,
                                    final int zipFileCount,
                                    final int maxEntriesPerOutputFile,
                                    final Path proxyDir,
                                    final List<String> eventFeeds) {
        // Do the aggregation
        aggregate(FileUtil.getCanonicalPath(proxyDir), maxEntriesPerOutputFile);

        final int inputEntriesPerFeed = (zipFileCount * entriesPerInputFile) / feedCount;

        // TODO fix this as it doesn't work if the entries per feed is below maxEntriesPerOutputFile
        final int expectedStreamsPerFeed = ((zipFileCount * entriesPerInputFile) / feedCount) / maxEntriesPerOutputFile;

        eventFeeds.forEach(feed -> {
            final FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setExpression(MetaExpressionUtil.createFeedExpression(feed));
            final List<Meta> streams = metaService.find(criteria);
            Assert.assertEquals(expectedStreamsPerFeed, streams.size());

            streams.forEach(stream -> {
                long streamId = stream.getId();
                try (final Source source = streamStore.openSource(streamId)) {
                    assertContent("expecting meta data", source, true, StreamTypeNames.META);
                    try {
                        // TODO : @66 No idea what we might get here.
                        assertThat(2).as("expecting boundary data").isEqualTo(source.count());
                    } catch (final IOException e) {
                        // Ignore.
                    }

//                    streamStore.closeStreamSource(source);
//                    source = streamStore.openStreamSource(streamId);
//
//                    final NestedInputStream nestedInputStream = new RANestedInputStream(source);
//
//                    assertThat(nestedInputStream.getEntryCount()).isLessThanOrEqualTo(maxEntriesPerOutputFile);
//                    nestedInputStream.close();
//                    streamStore.closeStreamSource(source);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void aggregate(final String proxyDir,
                           final int maxAggregation,
                           final long maxStreamSize) {
        final ProxyAggregationExecutor proxyAggregationExecutor = new ProxyAggregationExecutor(
                taskContext,
                executorProvider,
                filePackProcessorProvider,
                proxyDir,
                10,
                10000,
                10000,
                maxAggregation,
                maxStreamSize);
        proxyAggregationExecutor.exec();
    }

    private void aggregate(final String proxyDir,
                           final int maxAggregation) {
        aggregate(proxyDir, maxAggregation, DEFAULT_MAX_STREAM_SIZE);
    }

    private void assertContent(final String msg, final Source is, final boolean hasContent, final String dataType) throws IOException {
        try (final InputStreamProvider inputStreamProvider = is.get(0)) {
            if (hasContent) {
                try (final SegmentInputStream inputStream = inputStreamProvider.get(dataType)) {
                    assertThat(inputStream.size() > 0).as(msg).isTrue();
                }
            } else {
                try (final SegmentInputStream inputStream = inputStreamProvider.get(dataType)) {
                    assertThat(inputStream.size()).as(msg).as(msg).isEqualTo(0);
                }
            }
        }
    }
}
