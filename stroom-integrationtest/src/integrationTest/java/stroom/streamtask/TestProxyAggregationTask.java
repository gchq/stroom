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

package stroom.streamtask;


import org.junit.jupiter.api.Test;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.ExpressionUtil;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.store.api.NestedInputStream;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.datafeed.BufferFactory;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedDocCache;
import stroom.feed.FeedStore;
import stroom.io.SeekableInputStream;
import stroom.proxy.repo.StroomZipFile;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.task.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.test.StroomExpectedException;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyAggregationTask extends AbstractCoreIntegrationTest {
    private final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    @Inject
    private StreamStore streamStore;
    @Inject
    private DataMetaService streamMetaService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private FeedDocCache feedDocCache;
    @Inject
    private MetaDataStatistic metaDataStatistic;
    @Inject
    private TaskContext taskContext;
    @Inject
    private ExecutorProvider executorProvider;
    @Inject
    private BufferFactory bufferFactory;

    private void aggregate(final String proxyDir,
                           final int maxAggregation,
                           final long maxStreamSize) {
        final ProxyFileProcessorImpl proxyFileProcessor = new ProxyFileProcessorImpl(streamStore, feedDocCache, metaDataStatistic, maxAggregation, maxStreamSize, bufferFactory);
        final ProxyAggregationExecutor proxyAggregationExecutor = new ProxyAggregationExecutor(proxyFileProcessor, taskContext, executorProvider, proxyDir, 10, maxAggregation, 10000, maxStreamSize);
        proxyAggregationExecutor.exec(new DummyTask());
    }

    private void aggregate(final String proxyDir,
                           final int maxAggregation) {
        aggregate(proxyDir, maxAggregation, DEFAULT_MAX_STREAM_SIZE);
    }

    @Test
    void testImport() throws IOException {
        // commonTestControl.deleteAll();

        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1, feedName2);

        Files.createDirectories(proxyDir);

        final Path testFile1 = proxyDir.resolve("sample1.zip");
        writeTestFile(testFile1, feedName1, "data1\ndata1\n");

        final Path testFile2 = proxyDir.resolve("sample2.zip.lock");
        writeTestFile(testFile2, feedName2, "data2\ndata2\n");

        final Path testFile3 = proxyDir.resolve("sample3.zip");
        writeTestFile(testFile3, feedName1, "data3\ndata3\n");

        final Path testFile4 = proxyDir.resolve("some/nested/dir/sample4.zip");
        writeTestFile(testFile4, feedName2, "data4\ndata4\n");

        assertThat(Files.isRegularFile(testFile1)).as("Built test zip file").isTrue();
        assertThat(Files.isRegularFile(testFile2)).as("Built test zip file").isTrue();
        assertThat(Files.isRegularFile(testFile3)).as("Built test zip file").isTrue();
        assertThat(Files.isRegularFile(testFile4)).as("Built test zip file").isTrue();

        aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

        assertThat(Files.isRegularFile(testFile1)).as("Expecting task to delete file once loaded into stream store").isFalse();
        assertThat(Files.isRegularFile(testFile2)).as("Expecting task to not delete file as it was still locked").isTrue();
        assertThat(Files.isRegularFile(testFile3)).as("Expecting task to delete file once loaded into stream store").isFalse();
        assertThat(Files.isRegularFile(testFile4)).as("Expecting task to delete file once loaded into stream store").isFalse();

        final FindDataCriteria findStreamCriteria1 = new FindDataCriteria();
        findStreamCriteria1.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        final BaseResultList<Data> resultList1 = streamMetaService.find(findStreamCriteria1);
        assertThat(resultList1.size()).as("Expecting 2 files to get merged").isEqualTo(1);

        final StreamSource streamSource = streamStore.openStreamSource(resultList1.getFirst().getId());

        assertThat(streamSource.getChildStream(StreamTypeNames.META)).isNotNull();

        final NestedInputStream nestedInputStream = streamSource.getNestedInputStream();
        assertThat(nestedInputStream.getEntryCount()).isEqualTo(2);
        nestedInputStream.getNextEntry();
        assertThat(StreamUtil.streamToString(nestedInputStream, false)).isEqualTo("data1\ndata1\n");
        nestedInputStream.closeEntry();
        nestedInputStream.getNextEntry();
        assertThat(StreamUtil.streamToString(nestedInputStream, false)).isEqualTo("data3\ndata3\n");
        nestedInputStream.closeEntry();

        final StreamSource metaSource = streamSource.getChildStream(StreamTypeNames.META);
        final NestedInputStream metaNestedInputStream = metaSource.getNestedInputStream();
        assertThat(metaNestedInputStream.getEntryCount()).isEqualTo(2);

        nestedInputStream.close();
        metaNestedInputStream.close();
        streamStore.closeStreamSource(streamSource);

        final FindDataCriteria findStreamCriteria2 = new FindDataCriteria();
        findStreamCriteria2.setExpression(ExpressionUtil.createFeedExpression(feedName2));

        final BaseResultList<Data> resultList2 = streamMetaService.find(findStreamCriteria2);

        assertThat(resultList2.size()).as("Expecting file 1 ").isEqualTo(1);
    }

    @Test
    void testImportLots() throws IOException {
        // commonTestControl.deleteAll();

        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        Files.createDirectory(proxyDir);

        final Path testFile1 = proxyDir.resolve("001.zip");
        writeTestFileWithManyEntries(testFile1, feedName1, 10);

        final Path testFile2 = proxyDir.resolve("002.zip");
        writeTestFileWithManyEntries(testFile2, feedName1, 5);

        final Path testFile3 = proxyDir.resolve("003.zip");
        writeTestFileWithManyEntries(testFile3, feedName1, 5);

        final Path testFile4 = proxyDir.resolve("004.zip");
        writeTestFileWithManyEntries(testFile4, feedName1, 10);

        aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

        final FindDataCriteria criteria = new FindDataCriteria();
        criteria.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        final List<Data> list = streamMetaService.find(criteria);
        assertThat(list.size()).isEqualTo(3);

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamTypeNames.META), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = source.getNestedInputStream();

        assertThat(nestedInputStream.getEntryCount()).isEqualTo(10);
        nestedInputStream.close();
        streamStore.closeStreamSource(source);

    }

    @Test
    @StroomExpectedException(exception = ZipException.class)
    public void testImportLockedFiles() throws IOException {
        // commonTestControl.deleteAll();
        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve("sample1.zip");
        try (OutputStream lockedBadFile = writeLockedTestFile(testFile1, feedName1)) {
            final Path testFile2 = proxyDir.resolve("sample2.zip");
            writeTestFile(testFile2, feedName1, "some\ntest\ndataa\n");

            assertThat(Files.isRegularFile(testFile1)).as("Built test zip file").isTrue();
            assertThat(Files.isRegularFile(testFile2)).as("Built test zip file").isTrue();

            aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

            assertThat(Files.isRegularFile(testFile1)).as("Expecting task to rename bad zip file").isFalse();
            assertThat(Files.isRegularFile(Paths.get(FileUtil.getCanonicalPath(testFile1) + ".bad"))).as("Expecting task to rename bad zip file").isTrue();
            assertThat(Files.isRegularFile(testFile2)).as("Expecting good file to go").isFalse();

            // run again and it should clear down the one
            aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

            assertThat(Files.isRegularFile(Paths.get(FileUtil.getCanonicalPath(testFile1) + ".bad"))).as("Expecting bad zip file to still be there").isTrue();
            assertThat(Files.isRegularFile(testFile2)).as("Expecting task to just write the one file and leave the bad one").isFalse();
        }
    }

    @Test
    void testImportZipWithContextFiles() throws IOException {
        // commonTestControl.deleteAll();

        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve("sample1.zip");
        writeTestFileWithContext(testFile1, feedName1, "data1\ndata1\n", "context1\ncontext1\n");

        assertThat(Files.isRegularFile(testFile1)).as("Built test zip file").isTrue();

        aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

        final FindDataCriteria criteria = new FindDataCriteria();
        criteria.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        final List<Data> list = streamMetaService.find(criteria);
        assertThat(list.size()).isEqualTo(1);

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamTypeNames.META), true);
        try {
            // TODO : @66 No idea what we might get here.
            assertThat(1).as("expecting NO boundary data").isEqualTo(source.getNestedInputStream().getEntryCount());
        } catch (final IOException e) {
            // Ignore.
        }

//        assertContent("expecting NO boundary data", source.getChildStream(StreamTypeNames.BOUNDARY_INDEX), false);
        assertContent("expecting context data", source.getChildStream(StreamTypeNames.CONTEXT), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = source.getNestedInputStream();

        assertThat(nestedInputStream.getEntryCount()).isEqualTo(1);
        nestedInputStream.close();

        final NestedInputStream metaNestedInputStream = source.getChildStream(StreamTypeNames.META).getNestedInputStream();

        assertThat(metaNestedInputStream.getEntryCount()).isEqualTo(1);
        metaNestedInputStream.close();

        final NestedInputStream ctxNestedInputStream = source.getChildStream(StreamTypeNames.CONTEXT).getNestedInputStream();

        assertThat(ctxNestedInputStream.getEntryCount()).isEqualTo(1);
        ctxNestedInputStream.close();

        streamStore.closeStreamSource(source);

    }

    @Test
    void testImportZipWithContextFiles2() throws IOException {
        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        final Path testFile1 = proxyDir.resolve("sample1.zip");
        writeTestFileWithContext(testFile1, feedName1, "data1\ndata1\n", "context1\ncontext1\n");
        final Path testFile2 = proxyDir.resolve("sample2.zip");
        writeTestFileWithContext(testFile2, feedName1, "data2\ndata2\n", "context2\ncontext2\n");

        aggregate(FileUtil.getCanonicalPath(proxyDir), 10);

        final FindDataCriteria criteria = new FindDataCriteria();
        criteria.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        final List<Data> list = streamMetaService.find(criteria);
        assertThat(list.size()).isEqualTo(1);

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamTypeNames.META), true);
        try {
            // TODO : @66 No idea what we might get here.
            assertThat(2).as("expecting boundary data").isEqualTo(source.getNestedInputStream().getEntryCount());
        } catch (final IOException e) {
            // Ignore.
        }

//        assertContent("expecting boundary data", source.getChildStream(StreamTypeNames.BOUNDARY_INDEX), true);
        assertContent("expecting context data", source.getChildStream(StreamTypeNames.CONTEXT), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = source.getNestedInputStream();

        assertThat(nestedInputStream.getEntryCount()).isEqualTo(2);
        nestedInputStream.getNextEntry();
        assertThat(StreamUtil.streamToString(nestedInputStream, false)).isEqualTo("data1\ndata1\n");
        nestedInputStream.closeEntry();
        nestedInputStream.getNextEntry();
        assertThat(StreamUtil.streamToString(nestedInputStream, false)).isEqualTo("data2\ndata2\n");
        nestedInputStream.closeEntry();
        nestedInputStream.close();

        final NestedInputStream metaNestedInputStream = source.getChildStream(StreamTypeNames.META).getNestedInputStream();

        assertThat(metaNestedInputStream.getEntryCount()).isEqualTo(2);
        metaNestedInputStream.close();

        final NestedInputStream ctxNestedInputStream = source.getChildStream(StreamTypeNames.CONTEXT).getNestedInputStream();

        assertThat(ctxNestedInputStream.getEntryCount()).isEqualTo(2);
        ctxNestedInputStream.close();
        streamStore.closeStreamSource(source);
    }

    private void assertContent(final String msg, final StreamSource is, final boolean hasContent) throws IOException {
        if (hasContent) {
            assertThat(((SeekableInputStream) is.getInputStream()).getSize() > 0).as(msg).isTrue();
        } else {
            assertThat(((SeekableInputStream) is.getInputStream()).getSize()).as(msg).isEqualTo(0);
        }
        is.getInputStream().close();
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

        return zipOutputStream;
    }

    private void writeTestFileWithContext(final Path testFile, final String eventFeed, final String content,
                                          final String context) throws IOException {
        Files.createDirectories(testFile.getParent());
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
    }

    private void writeTestFileWithManyEntries(final Path testFile, final String eventFeed, final int count)
            throws IOException {
        Files.createDirectories(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(testFile)));

        for (int i = 1; i <= count; i++) {
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

    @Test
    void testAggregationLimits_SmallFiles() throws IOException {
        // commonTestControl.deleteAll();

        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 50; i++) {
            final Path testFile1 = proxyDir.resolve("sample" + i + ".zip");
            writeTestFile(testFile1, feedName1, "data1\ndata1\n");
        }

        aggregate(FileUtil.getCanonicalPath(proxyDir), 50, 1L);

        final FindDataCriteria findStreamCriteria1 = new FindDataCriteria();
        findStreamCriteria1.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        assertThat(streamMetaService.find(findStreamCriteria1).size()).isEqualTo(50);
    }

    @Test
    void testAggregationLimits_SmallCount() throws IOException {
        // commonTestControl.deleteAll();

        final Path proxyDir = getCurrentTestDir().resolve("proxy" + FileSystemTestUtil.getUniqueTestString());

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        createFeeds(feedName1);

        Files.createDirectories(proxyDir);

        for (int i = 1; i <= 50; i++) {
            final Path testFile1 = proxyDir.resolve("sample" + i + ".zip");
            writeTestFile(testFile1, feedName1, "data1\ndata1\n");
        }

        aggregate(FileUtil.getCanonicalPath(proxyDir), 25);

        final FindDataCriteria findStreamCriteria1 = new FindDataCriteria();
        findStreamCriteria1.setExpression(ExpressionUtil.createFeedExpression(feedName1));
        assertThat(streamMetaService.find(findStreamCriteria1).size()).isEqualTo(2);
    }

    private void createFeeds(final String... feeds) {
        feedDocCache.clear();
        for (final String feed : feeds) {
            feedStore.createDocument(feed);
        }
    }
}
