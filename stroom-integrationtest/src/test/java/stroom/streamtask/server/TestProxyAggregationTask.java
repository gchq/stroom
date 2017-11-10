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

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.Feed;
import stroom.io.SeekableInputStream;
import stroom.node.server.StroomPropertyService;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.NestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.spring.DummyTask;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.test.StroomExpectedException;
import stroom.util.thread.ThreadUtil;
import stroom.util.zip.StroomZipFile;

import javax.annotation.Resource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class TestProxyAggregationTask extends AbstractCoreIntegrationTest {
    @Resource
    private StreamStore streamStore;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private ProxyAggregationExecutor proxyAggregationExecutor;
    @Resource
    private StroomPropertyService stroomPropertyService;

    @Test
    public void testImport() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();
        final Feed eventFeed2 = commonTestScenarioCreator.createSimpleFeed();

        proxyDir.mkdirs();

        final File testFile1 = new File(proxyDir, "sample1.zip");
        writeTestFile(testFile1, eventFeed1, "data1\ndata1\n");

        final File testFile2 = new File(proxyDir, "sample2.zip.lock");
        writeTestFile(testFile2, eventFeed1, "data2\ndata2\n");

        final File testFile3 = new File(proxyDir, "sample3.zip");
        writeTestFile(testFile3, eventFeed1, "data3\ndata3\n");

        final File testFile4 = new File(proxyDir, "some/nested/dir/sample4.zip");
        writeTestFile(testFile4, eventFeed2, "data4\ndata4\n");

        Assert.assertTrue("Built test zip file", testFile1.isFile());
        Assert.assertTrue("Built test zip file", testFile2.isFile());
        Assert.assertTrue("Built test zip file", testFile3.isFile());
        Assert.assertTrue("Built test zip file", testFile4.isFile());

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath());

        Assert.assertFalse("Expecting task to delete file once loaded into stream store", testFile1.isFile());
        Assert.assertTrue("Expecting task to not delete file as it was still locked", testFile2.isFile());
        Assert.assertFalse("Expecting task to delete file once loaded into stream store", testFile3.isFile());
        Assert.assertFalse("Expecting task to delete file once loaded into stream store", testFile4.isFile());

        final FindStreamCriteria findStreamCriteria1 = new FindStreamCriteria();
        findStreamCriteria1.obtainFeeds().obtainInclude().add(eventFeed1.getId());
        final BaseResultList<Stream> resultList1 = streamStore.find(findStreamCriteria1);
        Assert.assertEquals("Expecting 2 files to get merged", 1, resultList1.size());

        final StreamSource streamSource = streamStore.openStreamSource(resultList1.getFirst().getId());

        Assert.assertNotNull(streamSource.getChildStream(StreamType.META));

        final NestedInputStream nestedInputStream = new RANestedInputStream(streamSource);
        Assert.assertEquals(2, nestedInputStream.getEntryCount());
        nestedInputStream.getNextEntry();
        Assert.assertEquals("data1\ndata1\n", StreamUtil.streamToString(nestedInputStream, false));
        nestedInputStream.closeEntry();
        nestedInputStream.getNextEntry();
        Assert.assertEquals("data3\ndata3\n", StreamUtil.streamToString(nestedInputStream, false));
        nestedInputStream.closeEntry();

        final StreamSource metaSource = streamSource.getChildStream(StreamType.META);
        final NestedInputStream metaNestedInputStream = new RANestedInputStream(metaSource);
        Assert.assertEquals(2, metaNestedInputStream.getEntryCount());

        nestedInputStream.close();
        metaNestedInputStream.close();
        streamStore.closeStreamSource(streamSource);

        final FindStreamCriteria findStreamCriteria2 = new FindStreamCriteria();
        findStreamCriteria2.obtainFeeds().obtainInclude().add(eventFeed2.getId());

        final BaseResultList<Stream> resultList2 = streamStore.find(findStreamCriteria2);

        Assert.assertEquals("Expecting file 1 ", 1, resultList2.size());
    }

    @Test
    public void testImportLots() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        proxyDir.mkdirs();

        final File testFile1 = new File(proxyDir, "001.zip");
        writeTestFileWithManyEntries(testFile1, eventFeed1, 10);

        final File testFile2 = new File(proxyDir, "002.zip");
        writeTestFileWithManyEntries(testFile2, eventFeed1, 5);

        final File testFile3 = new File(proxyDir, "003.zip");
        writeTestFileWithManyEntries(testFile3, eventFeed1, 5);

        final File testFile4 = new File(proxyDir, "004.zip");
        writeTestFileWithManyEntries(testFile4, eventFeed1, 10);

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath(), true, 10, null);

        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.obtainFeeds().obtainInclude().add(eventFeed1.getId());
        final List<Stream> list = streamStore.find(criteria);
        Assert.assertEquals(3, list.size());

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamType.META), true);
        assertContent("expecting NO boundary data", source.getChildStream(StreamType.BOUNDARY_INDEX), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = new RANestedInputStream(source);

        Assert.assertEquals(10, nestedInputStream.getEntryCount());
        nestedInputStream.close();
        streamStore.closeStreamSource(source);

    }

    @Test
    @StroomExpectedException(exception = ZipException.class)
    public void testImportLockedFiles() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        FileUtil.mkdirs(proxyDir);

        final File testFile1 = new File(proxyDir, "sample1.zip");
        try (OutputStream lockedBadFile = writeLockedTestFile(testFile1, eventFeed1)) {
            final File testFile2 = new File(proxyDir, "sample2.zip");
            writeTestFile(testFile2, eventFeed1, "some\ntest\ndataa\n");

            Assert.assertTrue("Built test zip file", testFile1.isFile());
            Assert.assertTrue("Built test zip file", testFile2.isFile());

            proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath());

            Assert.assertFalse("Expecting task to rename bad zip file", testFile1.isFile());
            Assert.assertTrue("Expecting task to rename bad zip file",
                    new File(testFile1.getAbsolutePath() + ".bad").isFile());
            Assert.assertFalse("Expecting good file to go", testFile2.isFile());

            // run again and it should clear down the one
            proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath());

            Assert.assertTrue("Expecting bad zip file to still be there",
                    new File(testFile1.getAbsolutePath() + ".bad").isFile());
            Assert.assertFalse("Expecting task to just send the one file and leave the bad one", testFile2.isFile());
        }
    }

    @Test
    public void testImportZipWithContextFiles() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        FileUtil.mkdirs(proxyDir);

        final File testFile1 = new File(proxyDir, "sample1.zip");
        writeTestFileWithContext(testFile1, eventFeed1, "data1\ndata1\n", "context1\ncontext1\n");

        Assert.assertTrue("Built test zip file", testFile1.isFile());

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath());

        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.obtainFeeds().obtainInclude().add(eventFeed1.getId());
        final List<Stream> list = streamStore.find(criteria);
        Assert.assertEquals(1, list.size());

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamType.META), true);
        assertContent("expecting NO boundary data", source.getChildStream(StreamType.BOUNDARY_INDEX), false);
        assertContent("expecting context data", source.getChildStream(StreamType.CONTEXT), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = new RANestedInputStream(source);

        Assert.assertEquals(1, nestedInputStream.getEntryCount());
        nestedInputStream.close();

        final NestedInputStream metaNestedInputStream = new RANestedInputStream(source.getChildStream(StreamType.META));

        Assert.assertEquals(1, metaNestedInputStream.getEntryCount());
        metaNestedInputStream.close();

        final NestedInputStream ctxNestedInputStream = new RANestedInputStream(
                source.getChildStream(StreamType.CONTEXT));

        Assert.assertEquals(1, ctxNestedInputStream.getEntryCount());
        ctxNestedInputStream.close();

        streamStore.closeStreamSource(source);

    }

    @Test
    public void testImportZipWithContextFiles2() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        FileUtil.mkdirs(proxyDir);

        final File testFile1 = new File(proxyDir, "sample1.zip");
        writeTestFileWithContext(testFile1, eventFeed1, "data1\ndata1\n", "context1\ncontext1\n");
        final File testFile2 = new File(proxyDir, "sample2.zip");
        writeTestFileWithContext(testFile2, eventFeed1, "data2\ndata2\n", "context2\ncontext2\n");

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath());

        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.obtainFeeds().obtainInclude().add(eventFeed1.getId());
        final List<Stream> list = streamStore.find(criteria);
        Assert.assertEquals(1, list.size());

        StreamSource source = streamStore.openStreamSource(list.get(0).getId());

        assertContent("expecting meta data", source.getChildStream(StreamType.META), true);
        assertContent("expecting boundary data", source.getChildStream(StreamType.BOUNDARY_INDEX), true);
        assertContent("expecting context data", source.getChildStream(StreamType.CONTEXT), true);

        streamStore.closeStreamSource(source);
        source = streamStore.openStreamSource(list.get(0).getId());

        final NestedInputStream nestedInputStream = new RANestedInputStream(source);

        Assert.assertEquals(2, nestedInputStream.getEntryCount());
        nestedInputStream.getNextEntry();
        Assert.assertEquals("data1\ndata1\n", StreamUtil.streamToString(nestedInputStream, false));
        nestedInputStream.closeEntry();
        nestedInputStream.getNextEntry();
        Assert.assertEquals("data2\ndata2\n", StreamUtil.streamToString(nestedInputStream, false));
        nestedInputStream.closeEntry();
        nestedInputStream.close();

        final NestedInputStream metaNestedInputStream = new RANestedInputStream(source.getChildStream(StreamType.META));

        Assert.assertEquals(2, metaNestedInputStream.getEntryCount());
        metaNestedInputStream.close();

        final NestedInputStream ctxNestedInputStream = new RANestedInputStream(
                source.getChildStream(StreamType.CONTEXT));

        Assert.assertEquals(2, ctxNestedInputStream.getEntryCount());
        ctxNestedInputStream.close();
        streamStore.closeStreamSource(source);

    }

    private void assertContent(final String msg, final StreamSource is, final boolean hasContent) throws IOException {
        if (hasContent) {
            Assert.assertTrue(msg, ((SeekableInputStream) is.getInputStream()).getSize() > 0);
        } else {
            Assert.assertTrue(msg, ((SeekableInputStream) is.getInputStream()).getSize() == 0);
        }
        is.getInputStream().close();
    }

    private OutputStream writeLockedTestFile(final File testFile, final Feed eventFeed)
            throws IOException {
        FileUtil.mkdirs(testFile.getParentFile());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(testFile));

        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_META_ENTRY.getFullName()));
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(zipOutputStream, StreamUtil.DEFAULT_CHARSET));
        printWriter.println("Feed:" + eventFeed.getName());
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

    private void writeTestFileWithContext(final File testFile, final Feed eventFeed, final String content,
            final String context) throws IOException {
        testFile.getParentFile().mkdirs();
        final FileOutputStream fileOutputStream = new FileOutputStream(testFile);
        final ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("file1.meta"));
        final PrintWriter printWriter = new PrintWriter(zipOutputStream);
        printWriter.println("Feed:" + eventFeed.getName());
        printWriter.println("Proxy:ProxyTest");
        printWriter.println("Compression:Zip");
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

    private static void writeTestFile(final File testFile, final String feedName, final String data)
            throws IOException {

        FileUtil.mkdirs(testFile.getParentFile());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(testFile));
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_META_ENTRY.getFullName()));
        PrintWriter printWriter = new PrintWriter(zipOutputStream);
        printWriter.println("Feed:" + feedName);
        printWriter.println("Proxy:ProxyTest");
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_DATA_ENTRY.getFullName()));
        printWriter = new PrintWriter(zipOutputStream);
        printWriter.print(data);
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.close();
    }

    private static void writeTestFile(final File testFile, final Feed eventFeed, final String data)
            throws IOException {
        writeTestFile(testFile, eventFeed.getName(), data);
    }

    private void writeTestFileWithManyEntries(final File testFile, final Feed eventFeed, final int count)
            throws IOException {
        FileUtil.mkdirs(testFile.getParentFile());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(testFile)));

        for (int i = 1; i <= count; i++) {
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

    @Test
    public void testAggregationLimits_SmallFiles() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 50; i++) {
            final File testFile1 = new File(proxyDir, "sample" + i + ".zip");
            writeTestFile(testFile1, eventFeed1, "data1\ndata1\n");
        }

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath(), true, 50, 1L);

        final FindStreamCriteria findStreamCriteria1 = new FindStreamCriteria();
        findStreamCriteria1.obtainFeeds().obtainInclude().add(eventFeed1);
        Assert.assertEquals(50, streamStore.find(findStreamCriteria1).size());
    }

    @Test
    public void testmaxScan_ManyFiles() throws IOException {
        // commonTestControl.deleteAll();
        StroomProperties.setIntProperty("stroom.maxAggregationScan", 5, StroomProperties.Source.TEST);

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 1; i++) {
            final File testFile1 = new File(proxyDir, "sample" + i + ".zip");
            writeTestFile(testFile1, eventFeed1, "data1\ndata1\n");
        }

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath(), true, 50, 1000L);

        final FindStreamCriteria findStreamCriteria1 = new FindStreamCriteria();
        findStreamCriteria1.obtainFeeds().obtainInclude().add(eventFeed1);
        Assert.assertEquals(1, streamStore.find(findStreamCriteria1).size());

        while (true) {

        }
    }
    public static void main(final String[] args) throws IOException {

        final File proxyDir = new File("/home/dev/tmp/dev/proxy");

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 1000000000; i++) {
            final File testFile1 = new File(proxyDir, String.format("%08d", i) + ".zip");
            writeTestFile(testFile1, "TEST_FEED", i + "-data1\n" + i + "-data1\n");
            ThreadUtil.sleep(250);
        }
    }

    @Test
    public void testAggregationLimits_SmallCount() throws IOException {
        // commonTestControl.deleteAll();

        final File proxyDir = new File(getCurrentTestDir(), "proxy" + FileSystemTestUtil.getUniqueTestString());

        final Feed eventFeed1 = commonTestScenarioCreator.createSimpleFeed();



        proxyDir.mkdirs();

        for (int i = 1; i <= 50; i++) {
            final File testFile1 = new File(proxyDir, "sample" + i + ".zip");
            writeTestFile(testFile1, eventFeed1, "data1\ndata1\n");
        }

        proxyAggregationExecutor.aggregate(new DummyTask(), proxyDir.getAbsolutePath(), true, 25, null);

        final FindStreamCriteria findStreamCriteria1 = new FindStreamCriteria();
        findStreamCriteria1.obtainFeeds().obtainInclude().add(eventFeed1);
        Assert.assertEquals(2, streamStore.find(findStreamCriteria1).size());
    }
}
