package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.dictionary.spring.DictionaryConfiguration;
import stroom.explorer.server.ExplorerConfiguration;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.index.spring.IndexConfiguration;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.io.SeekableInputStream;
import stroom.logging.spring.EventLoggingConfiguration;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.proxy.repo.FileSetProcessor;
import stroom.ruleset.spring.RuleSetConfiguration;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.spring.SecurityConfiguration;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ScopeTestConfiguration;
import stroom.spring.ServerComponentScanTestConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.NestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.shared.ExpressionUtil;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.DummyTask;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.test.StroomSpringJUnit4ClassRunner;
import stroom.visualisation.spring.VisualisationConfiguration;

import javax.annotation.Resource;
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

@RunWith(StroomSpringJUnit4ClassRunner.class)
@ActiveProfiles(value = {
        StroomSpringProfiles.PROD,
        StroomSpringProfiles.IT,
        SecurityConfiguration.MOCK_SECURITY})
@ContextConfiguration(classes = {
        DashboardConfiguration.class,
        EventLoggingConfiguration.class,
        IndexConfiguration.class,
        MetaDataStatisticConfiguration.class,
        PersistenceConfiguration.class,
        DictionaryConfiguration.class,
        PipelineConfiguration.class,
        RuleSetConfiguration.class,
        ScopeConfiguration.class,
        ScopeTestConfiguration.class,
        ScriptConfiguration.class,
        SearchConfiguration.class,
        SecurityConfiguration.class,
        ExplorerConfiguration.class,
        ServerComponentScanTestConfiguration.class,
        ServerConfiguration.class,
        StatisticsConfiguration.class,
        VisualisationConfiguration.class})
@Ignore //Only meant to be run manually
public class ManualProxyAggregationTest {

    // The aim of this test class is to be able to run the proxy aggregation
    // and manually kill it part way through and then run it again to ensure
    // it recovers correctly.

    private final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestProxyAggregationTask.class);
    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyAggregationTask.class);

    @Resource
    private StreamStore streamStore;
    @Resource
    private FeedService feedService;
    @Resource
    private MetaDataStatistic metaDataStatistic;
    @Resource
    private TaskContext taskContext;
    @Resource
    private ExecutorProvider executorProvider;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private Provider<FileSetProcessor> filePackProcessorProvider;
    @Resource
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
        final List<Feed> eventFeeds = createFeeds(FEED_COUNT);

        final Path proxyDir = createProxyDirectory();

        generateTestFiles(proxyDir, ENTRIES_PER_ZIP, ZIP_FILE_COUNT, eventFeeds);
    }

    @Test
    public void runProxyAggregation() {
        // Generate the feeds to use in the test
        final List<Feed> eventFeeds = getFeeds(FEED_COUNT);

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

    private void generateTestFiles(final Path proxyDir, final int entriesPerZip, final int zipFileCount, final List<Feed> eventFeeds) {
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

    private List<Feed> createFeeds(final int feedCount) {
        return getFeedNames(feedCount)
                .stream()
                .map(name -> {
                    Feed feed = feedService.create(name);
                    feed.setDescription(name);
                    feed.setStatus(Feed.FeedStatus.RECEIVE);
                    feed.setStreamType(StreamType.RAW_EVENTS);
                    feed = feedService.save(feed);
                    LOGGER.debug("Created feed {}", feed.getName());
                    return feed;
                })
                .collect(Collectors.toList());
    }

    private List<Feed> getFeeds(final int feedCount) {
        return getFeedNames(feedCount)
                .stream()
                .map(name -> {
                    Feed feed = feedService.loadByName(name);
                    return feed;
                })
                .collect(Collectors.toList());
    }

    public Path getCurrentTestDir() {
        return FileUtil.getTempDir();
    }

    private void writeTestFileWithManyEntries(final Path testFile, final List<Feed> eventFeeds, final int count)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(testFile)));

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("Creating file {}", testFile.toAbsolutePath().toString()));

        int feedIdx = 0;

        for (int i = 1; i <= count; i++) {
            feedIdx = ++feedIdx >= eventFeeds.size() ? 0 : feedIdx;
            final Feed eventFeed = eventFeeds.get(feedIdx);

            LAMBDA_LOGGER.debug(() ->
                    LambdaLogger.buildMessage("Using feed {}", eventFeed.getName()));

            final String name = String.valueOf(i);
            zipOutputStream.putNextEntry(new ZipEntry(name + ".hdr"));
            PrintWriter printWriter = new PrintWriter(zipOutputStream);
            printWriter.println("Feed:" + eventFeed.getName());
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
                                    final List<Feed> eventFeeds) {
        // Do the aggregation
        aggregate(FileUtil.getCanonicalPath(proxyDir), maxEntriesPerOutputFile);

        final int inputEntriesPerFeed = (zipFileCount * entriesPerInputFile) / feedCount;

        // TODO fix this as it doesn't work if the entries per feed is below maxEntriesPerOutputFile
        final int expectedStreamsPerFeed = ((zipFileCount * entriesPerInputFile) / feedCount) / maxEntriesPerOutputFile;

        eventFeeds.forEach(feed -> {
            final FindStreamCriteria criteria = new FindStreamCriteria();
            criteria.setExpression(ExpressionUtil.createFeedExpression(feed));
            final List<Stream> streams = streamStore.find(criteria);
            Assert.assertEquals(expectedStreamsPerFeed, streams.size());

            streams.forEach(stream -> {
                try {
                    long streamId = stream.getId();
                    StreamSource source = streamStore.openStreamSource(streamId);
                    assertContent("expecting meta data", source.getChildStream(StreamType.META), true);
                    assertContent("expecting NO boundary data", source.getChildStream(StreamType.BOUNDARY_INDEX), true);
                    streamStore.closeStreamSource(source);
                    source = streamStore.openStreamSource(streamId);

                    final NestedInputStream nestedInputStream = new RANestedInputStream(source);

                    assertThat(nestedInputStream.getEntryCount()).isLessThanOrEqualTo(maxEntriesPerOutputFile);
                    nestedInputStream.close();
                    streamStore.closeStreamSource(source);
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
                maxAggregation,
                10000,
                maxStreamSize);
        proxyAggregationExecutor.exec(new DummyTask());
    }

    private void aggregate(final String proxyDir,
                           final int maxAggregation) {
        aggregate(proxyDir, maxAggregation, DEFAULT_MAX_STREAM_SIZE);
    }

    private void assertContent(final String msg, final StreamSource is, final boolean hasContent) throws IOException {
        if (hasContent) {
            Assert.assertTrue(msg, ((SeekableInputStream) is.getInputStream()).getSize() > 0);
        } else {
            Assert.assertTrue(msg, ((SeekableInputStream) is.getInputStream()).getSize() == 0);
        }
        is.getInputStream().close();
    }

}
