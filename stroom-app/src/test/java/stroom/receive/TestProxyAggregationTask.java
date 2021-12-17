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

package stroom.receive;


import stroom.core.receive.ProxyAggregationExecutor;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipFile;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoFileNames;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.RepoSources;
import stroom.security.api.SecurityContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyAggregationTask extends AbstractCoreIntegrationTest {

    private static final long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyAggregationTask.class);

    @Inject
    private Store store;
    @Inject
    private MetaService metaService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private ProxyAggregationExecutor proxyAggregationExecutor;
    @Inject
    private AggregatorConfig aggregatorConfig;
    @Inject
    private Aggregator aggregator;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoDirProvider repoDirProvider;
    @Inject
    private AggregateForwarder aggregateForwarder;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private TempDirProvider tempDirProvider;

    private Path proxyDir;

    /**
     * Stop the proxy repo DB being deleted between tests as we will delete table contents manually.
     */
    @Override
    protected boolean setupBetweenTests() {
        return false;
    }

    private void aggregate(final int maxAggregation,
                           final long maxStreamSize) {
        clearConfigValueMapper();
        setConfigValueMapper(AggregatorConfig.class, aggregatorConfig -> aggregatorConfig.copy()
                .withMaxItemsPerAggregate(maxAggregation)
                .withMaxUncompressedByteSize(maxStreamSize)
                .build());
        proxyAggregationExecutor.exec(true, true);
    }

    private void aggregate(final int maxAggregation) {
        aggregate(maxAggregation, DEFAULT_MAX_STREAM_SIZE);
    }

    @BeforeEach
    void beforeEach() {
        proxyDir = repoDirProvider.get();
        aggregateForwarder.clear();
        aggregator.clear();
        proxyRepoSourceEntries.clear();
        proxyRepoSources.clear();


        securityContext.asProcessingUser(() -> {
            commonTestControl.cleanup();
            commonTestControl.setup(tempDirProvider.get());
        });
    }

    @Test
    void testImport() throws IOException {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1, feedName2);

        Files.createDirectories(proxyDir);

        final Path testFile1 = proxyDir.resolve(getFileName(1));
        writeTestFile(testFile1, feedName1, "data1\ndata1\n");

        final Path testFile2 = proxyDir.resolve(getFileName(2) + ".lock");
        writeTestFile(testFile2, feedName2, "data2\ndata2\n");

        final Path testFile3 = proxyDir.resolve(getFileName(3));
        writeTestFile(testFile3, feedName1, "data3\ndata3\n");

        final Path testFile4 = proxyDir.resolve("some/nested/dir/" + getFileName(4));
        writeTestFile(testFile4, feedName2, "data4\ndata4\n");

        assertThat(Files.isRegularFile(testFile1))
                .as("Built test zip file")
                .isTrue();
        assertThat(Files.isRegularFile(testFile2))
                .as("Built test zip file")
                .isTrue();
        assertThat(Files.isRegularFile(testFile3))
                .as("Built test zip file")
                .isTrue();
        assertThat(Files.isRegularFile(testFile4))
                .as("Built test zip file")
                .isTrue();

        aggregate(10);

        assertThat(Files.isRegularFile(testFile1))
                .as("Expecting task to delete file once loaded into stream store")
                .isFalse();
        assertThat(Files.isRegularFile(testFile2))
                .as("Expecting task to not delete file as it was still locked")
                .isTrue();
        assertThat(Files.isRegularFile(testFile3))
                .as("Expecting task to delete file once loaded into stream store")
                .isFalse();
        assertThat(Files.isRegularFile(testFile4))
                .as("Expecting task to delete file once loaded into stream store")
                .isFalse();

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName1));
        final ResultPage<Meta> resultPage1 = metaService.find(findMetaCriteria);
        assertThat(resultPage1.size())
                .as("Expecting 2 files to get merged")
                .isEqualTo(1);

        try (final Source streamSource = store.openSource(resultPage1.getFirst().getId())) {
            assertThat(streamSource.count())
                    .isEqualTo(2);

            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                    assertThat(inputStream).isNotNull();
                }
                try (final InputStream inputStream = inputStreamProvider.get()) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("data1\ndata1\n");
                }
            }

            try (final InputStreamProvider inputStreamProvider = streamSource.get(1)) {
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                    assertThat(inputStream).isNotNull();
                }
                try (final InputStream inputStream = inputStreamProvider.get()) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("data3\ndata3\n");
                }
            }
        }

        final FindMetaCriteria findMetaCriteria2 = new FindMetaCriteria();
        findMetaCriteria2.setExpression(MetaExpressionUtil.createFeedExpression(feedName2));

        final ResultPage<Meta> resultPage2 = metaService.find(findMetaCriteria2);

        assertThat(resultPage2.size())
                .as("Expecting file 1 ")
                .isEqualTo(1);
    }

    @Test
    void testImportLots() throws IOException {
        final int feedCount = 1;
        final int zipFilesPerFeed = 4;
        final int entriesPerZip = 5;

        // Generate the feeds to use in the test
        final List<String> eventFeeds = generateFeeds(feedCount);

        generateTestFiles(proxyDir, zipFilesPerFeed, entriesPerZip, eventFeeds);

        aggregate(10);

        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(MetaExpressionUtil.createFeedExpression(eventFeeds.get(0)));
        final List<Meta> list = metaService.find(criteria).getValues();
        assertThat(list.size())
                .isEqualTo(2);

        try (final Source source = store.openSource(list.get(0).getId())) {
            assertContent("expecting meta data", source, true, StreamTypeNames.META);
        }
        try (final Source source = store.openSource(list.get(0).getId())) {
            assertThat(source.count())
                    .isEqualTo(10);
        }
    }

    @Test
    void testBulkLoad_singleFeed() {
        doBulkTest(1, 10, 40, 10);
    }

    @Disabled // manual only as takes too long
    @Test
    void testBulkLoad_multipleFeeds() {
        doBulkTest(4, 2000, 4, 10);
    }

    @Test
    void testBulkLoad_smallScale() {
        doBulkTest(2, 6, 4, 2);
    }

    @Test
    void testFileGeneration() {
        // Generate the feeds to use in the test
        final List<String> eventFeeds = generateFeeds(2);
        generateTestFiles(proxyDir, 3, 2, eventFeeds);
    }

    private void doBulkTest(
            final int feedCount,
            final int zipFilesPerFeed,
            final int entriesPerZip,
            final int maxEntriesPerOutputFile) {
        // Generate the source zip files
        createData(proxyDir, feedCount, zipFilesPerFeed, entriesPerZip);
        // Do the aggregation
        aggregate(maxEntriesPerOutputFile);
        checkStore(feedCount, entriesPerZip, zipFilesPerFeed, maxEntriesPerOutputFile);
    }

    private void createData(final Path proxyDir,
                            final int feedCount,
                            final int zipFilesPerFeed,
                            final int entriesPerZip) {
        // Generate the feeds to use in the test
        final List<String> eventFeeds = generateFeeds(feedCount);

        // Generate the source zip files
        generateTestFiles(proxyDir, zipFilesPerFeed, entriesPerZip, eventFeeds);
    }

    private void checkStore(final int feedCount,
                            final int entriesPerInputFile,
                            final int zipFilesPerFeed,
                            final int maxEntriesPerOutputFile) {
        final int inputEntriesPerFeed = (zipFilesPerFeed * entriesPerInputFile) / feedCount;

        // TODO fix this as it doesn't work if the entries per feed is below maxEntriesPerOutputFile
        final int expectedStreamsPerFeed = (zipFilesPerFeed * entriesPerInputFile) / maxEntriesPerOutputFile;

        final List<DocRef> feeds = feedStore.list();
        feeds.forEach(feed -> {
            final FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setExpression(MetaExpressionUtil.createFeedExpression(feed.getName()));
            final List<Meta> metas = metaService.find(criteria).getValues();
            assertThat(metas.size())
                    .isEqualTo(expectedStreamsPerFeed);

            metas.forEach(meta -> {
                try {
                    long metaId = meta.getId();
                    Source source = store.openSource(metaId);
                    assertThat(source.count())
                            .isEqualTo(maxEntriesPerOutputFile);
                    try (final InputStreamProvider inputStreamProvider = source.get(0)) {
                        assertContent("expecting meta data", source, true, StreamTypeNames.META);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void generateTestFiles(final Path proxyDir,
                                   final int zipFilesPerFeed,
                                   final int entriesPerZip,
                                   final List<String> eventFeeds) {
        int i = 0;
        for (final String feed : eventFeeds) {
            for (int j = 0; j < zipFilesPerFeed; j++) {
                i++;

                String filename = getFileName(i);
                final Path testFile = proxyDir.resolve(filename);
                try {
                    writeTestFileWithManyEntries(testFile, feed, entriesPerZip);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private List<String> generateFeeds(final int feedCount) {
        return IntStream.rangeClosed(1, feedCount)
                .boxed()
                .map(i -> {
                    String name = "TEST_FEED_" + i;
                    final DocRef feedRef = feedStore.createDocument(name);
                    FeedDoc feedDoc = feedStore.readDocument(feedRef);
                    feedDoc.setDescription(name);
                    feedDoc.setStatus(FeedStatus.RECEIVE);
                    feedDoc.setStreamType(StreamTypeNames.RAW_EVENTS);
                    feedStore.writeDocument(feedDoc);
                    LOGGER.debug(() -> LogUtil.message("Created feed {}", feedDoc.getName()));
                    return name;
                })
                .collect(Collectors.toList());
    }

    @Test
    void testImportLockedFiles() throws IOException {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve(getFileName(1));
        try (OutputStream lockedBadFile = writeLockedTestFile(testFile1, feedName1)) {
            final Path testFile2 = proxyDir.resolve(getFileName(2));
            writeTestFile(testFile2, feedName1, "some\ntest\ndataa\n");

            assertThat(Files.isRegularFile(testFile1))
                    .as("Built test zip file")
                    .isTrue();
            assertThat(Files.isRegularFile(testFile2))
                    .as("Built test zip file")
                    .isTrue();

            aggregate(10);

            assertThat(Files.isRegularFile(testFile1))
                    .as("Expecting task to rename bad zip file")
                    .isFalse();
            assertThat(Files.isRegularFile(Paths.get(FileUtil.getCanonicalPath(testFile1) + ".bad")))
                    .as("Expecting task to rename bad zip file")
                    .isTrue();
            assertThat(Files.isRegularFile(testFile2))
                    .as("Expecting good file to go")
                    .isFalse();

            // run again and it should clear down the one
            aggregate(10);

            assertThat(Files.isRegularFile(Paths.get(FileUtil.getCanonicalPath(testFile1) + ".bad")))
                    .as("Expecting bad zip file to still be there")
                    .isTrue();
            assertThat(Files.isRegularFile(testFile2))
                    .as("Expecting task to just write the one file and leave the bad one")
                    .isFalse();
        }
    }

    @Test
    void testImportZipWithContextFiles() throws IOException {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve(getFileName(1));
        writeTestFileWithContext(testFile1, feedName1, "data1\ndata1\n", "context1\ncontext1\n");

        assertThat(Files.isRegularFile(testFile1))
                .as("Built test zip file")
                .isTrue();

        aggregate(10);

        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName1));
        final List<Meta> list = metaService.find(criteria).getValues();
        assertThat(list.size())
                .isEqualTo(1);

        try (final Source source = store.openSource(list.get(0).getId())) {
            assertContent("expecting meta data", source, true, StreamTypeNames.META);
            try {
                // TODO : @66 No idea what we might get here.
                assertThat(1)
                        .as("expecting NO boundary data")
                        .isEqualTo(source.count());
            } catch (final IOException e) {
                // Ignore.
            }

            assertContent("expecting context data", source, true, StreamTypeNames.CONTEXT);

        }
        try (final Source source = store.openSource(list.get(0).getId())) {
            assertThat(source.count())
                    .isEqualTo(1);
        }
    }

    @Test
    void testImportZipWithContextFiles2() throws IOException {

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve(getFileName(1));
        writeTestFileWithContext(testFile1, feedName1, "data1\ndata1\n", "context1\ncontext1\n");
        final Path testFile2 = proxyDir.resolve(getFileName(2));
        writeTestFileWithContext(testFile2, feedName1, "data2\ndata2\n", "context2\ncontext2\n");

        aggregate(10);

        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName1));
        final List<Meta> list = metaService.find(criteria).getValues();
        assertThat(list.size()).isEqualTo(1);

        try (final Source source = store.openSource(list.get(0).getId())) {
            assertContent("expecting meta data", source, true, StreamTypeNames.META);
            try {
                // TODO : @66 No idea what we might get here.
                assertThat(2)
                        .as("expecting boundary data")
                        .isEqualTo(source.count());
            } catch (final IOException e) {
                // Ignore.
            }

//        assertContent("expecting boundary data", source.getChildStream(StreamTypeNames.BOUNDARY_INDEX), true);
            assertContent("expecting context data", source, true, StreamTypeNames.CONTEXT);

        }

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Compression", "Zip");
        attributeMap.put("Feed", feedName1);
        attributeMap.put("Proxy", "ProxyTest");
        attributeMap.put("ReceivedTime", "2010-01-01T00:00:00.000Z");

        try (final Source source = store.openSource(list.get(0).getId())) {
            assertThat(source.count())
                    .isEqualTo(2);

            try (final InputStreamProvider inputStreamProvider = source.get(0)) {
                try (final InputStream inputStream = inputStreamProvider.get()) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("data1\ndata1\n");
                }
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                    checkMeta(inputStream, attributeMap);
                }
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("context1\ncontext1\n");
                }
            }
            try (final InputStreamProvider inputStreamProvider = source.get(1)) {
                try (final InputStream inputStream = inputStreamProvider.get()) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("data2\ndata2\n");
                }
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
                    checkMeta(inputStream, attributeMap);
                }
                try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    assertThat(StreamUtil.streamToString(inputStream, false))
                            .isEqualTo("context2\ncontext2\n");
                }
            }
        }
    }

    private void checkMeta(final InputStream inputStream, final AttributeMap attributeMap) throws IOException {
        final AttributeMap resultAttributeMap = new AttributeMap();
        AttributeMapUtil.read(inputStream, resultAttributeMap);
        attributeMap.forEach((k, v) -> assertThat(resultAttributeMap.get(k)).isEqualTo(v));
    }

    private void assertContent(final String msg, final Source is, final boolean hasContent, final String dataType)
            throws IOException {
        try (final InputStreamProvider inputStreamProvider = is.get(0)) {
            if (hasContent) {
                try (final SegmentInputStream inputStream = inputStreamProvider.get(dataType)) {
                    assertThat(inputStream.size() > 0)
                            .as(msg)
                            .isTrue();
                }
            } else {
                try (final SegmentInputStream inputStream = inputStreamProvider.get(dataType)) {
                    assertThat(inputStream.size())
                            .as(msg)
                            .as(msg)
                            .isEqualTo(0);
                }
            }
        }
    }

    private OutputStream writeLockedTestFile(final Path testFile, final String eventFeed)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(testFile));

        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_META_ENTRY.getFullName()));
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(zipOutputStream, StreamUtil.DEFAULT_CHARSET));
        printWriter.println("Feed:" + eventFeed);
        printWriter.println("Proxy:ProxyTest");
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_DATA_ENTRY.getFullName()));
        printWriter = new PrintWriter(new OutputStreamWriter(zipOutputStream, StreamUtil.DEFAULT_CHARSET));
        printWriter.println("Time,Action,User,File");
        printWriter.println("01/01/2009:00:00:01,OPEN,userone,proxyload.txt");
        printWriter.flush();

        writeMeta(testFile, eventFeed);

        return zipOutputStream;
    }

    private void writeTestFileWithContext(final Path testFile, final String eventFeed, final String content,
                                          final String context) throws IOException {
        final Path dir = testFile.getParent();
        Files.createDirectories(dir);

        final OutputStream fileOutputStream = Files.newOutputStream(testFile);
        final ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("file1.meta"));
        final PrintWriter printWriter = new PrintWriter(zipOutputStream);
        printWriter.println("Feed:" + eventFeed);
        printWriter.println("Proxy:ProxyTest");
        printWriter.println("Compression:Zip");
        printWriter.println("ReceivedTime:2010-01-01T00:00:00.000Z");
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry("file1.dat"));
        zipOutputStream.write(content.getBytes(StreamUtil.DEFAULT_CHARSET));
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry("file1.ctx"));
        zipOutputStream.write(context.getBytes(StreamUtil.DEFAULT_CHARSET));
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        zipOutputStream.close();
        fileOutputStream.close();

        writeMeta(testFile, eventFeed);
    }

    private void writeTestFile(final Path testFile, final String eventFeed, final String data)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(testFile));
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_META_ENTRY.getFullName()));
        PrintWriter printWriter = new PrintWriter(zipOutputStream);
        printWriter.println("Feed:" + eventFeed);
        printWriter.println("Proxy:ProxyTest");
        printWriter.println("ReceivedTime:2010-01-01T00:00:00.000Z");
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_DATA_ENTRY.getFullName()));
        printWriter = new PrintWriter(zipOutputStream);
        printWriter.print(data);
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.close();

        writeMeta(testFile, eventFeed);
    }

    private void writeTestFileWithManyEntries(final Path testFile, final String eventFeed, final int count)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(testFile)));

        LOGGER.debug(() -> LogUtil.message("Creating file {}", testFile.toAbsolutePath().toString()));

        for (int i = 1; i <= count; i++) {
            LOGGER.debug(() -> LogUtil.message("Using feed {}", eventFeed));

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

        writeMeta(testFile, eventFeed);
    }

    private void writeMeta(final Path zipFile, final String feedName) throws IOException {
        final Path dir = zipFile.getParent();
        final Path metaFile = dir.resolve(ProxyRepoFileNames.getMeta(zipFile.getFileName().toString()));
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        try (final OutputStream outputStream = Files.newOutputStream(metaFile)) {
            AttributeMapUtil.write(attributeMap, outputStream);
        }
    }

    @Test
    void testAggregationLimits_SmallFiles() throws IOException {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 50; i++) {
            final Path testFile1 = proxyDir.resolve(getFileName(i));
            writeTestFile(testFile1, feedName1, "data1\ndata1\n");
        }

        aggregate(50, 1L);

        final FindMetaCriteria findMetaCriteria1 = new FindMetaCriteria();
        findMetaCriteria1.setExpression(MetaExpressionUtil.createFeedExpression(feedName1));
        assertThat(metaService.find(findMetaCriteria1).size())
                .isEqualTo(50);
    }

    @Test
    void testAggregationLimits_SmallCount() throws IOException {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        Files.createDirectories(proxyDir);

        for (int i = 1; i <= 50; i++) {
            final Path testFile1 = proxyDir.resolve(getFileName(i));
            writeTestFile(testFile1, feedName1, "data1\ndata1\n");
        }

        aggregate(25);

        final FindMetaCriteria findMetaCriteria1 = new FindMetaCriteria();
        findMetaCriteria1.setExpression(MetaExpressionUtil.createFeedExpression(feedName1));
        assertThat(metaService.find(findMetaCriteria1).size())
                .isEqualTo(2);
    }

    private void createFeeds(final String... feeds) {
        for (final String feed : feeds) {
            feedStore.createDocument(feed);
        }
    }

    private String getFileName(final long i) {
        return StroomFileNameUtil.getIdPath(i) + ".zip";
    }
}
