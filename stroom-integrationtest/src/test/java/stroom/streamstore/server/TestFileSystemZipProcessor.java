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

package stroom.streamstore.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Resource;

import stroom.proxy.repo.StroomStreamProcessor;
import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomStreamProcessor;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import stroom.util.zip.StroomHeaderArguments;
import stroom.feed.MetaMap;

public class TestFileSystemZipProcessor extends AbstractCoreIntegrationTest {
    @Resource
    private StreamStore streamStore;
    @Resource
    private FeedService feedService;
    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;

    @Test
    public void testSimpleSingleFile() throws IOException {
        final File file = new File(getCurrentTestDir(),
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes());
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<StreamType, String> expectedContent = new HashMap<StreamType, String>();
            expectedContent.put(null, "File1\nFile1\n");
            final HashMap<StreamType, List<String>> expectedBoundaries = new HashMap<StreamType, List<String>>();
            expectedBoundaries.put(null, Arrays.asList("File1\nFile1\n"));

            doTest(file, 1, new HashSet<String>(Arrays.asList("revt.bgz", "revt.meta.bgz", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testSimpleSingleFileReadThreeTimes() throws IOException {
        final File file = new File(getCurrentTestDir(),
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<StreamType, String> expectedContent = new HashMap<StreamType, String>();
            expectedContent.put(null, "File1\nFile1\nFile1\nFile1\nFile1\nFile1\n");
            final HashMap<StreamType, List<String>> expectedBoundaries = new HashMap<StreamType, List<String>>();
            expectedBoundaries.put(null, Arrays.asList("File1\nFile1\n", "File1\nFile1\n", "File1\nFile1\n"));

            doTest(file, 3, new HashSet<String>(
                    Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testSimpleSingleFileWithMetaAndContext() throws IOException {
        final File file = new File(getCurrentTestDir(),
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom1.ctx"));
            zipOut.write("Context1\nContext1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom1.meta"));
            zipOut.write("Meta11:1\nMeta12:1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<StreamType, String> expectedContent = new HashMap<StreamType, String>();
            expectedContent.put(null, "File1\nFile1\n");
            expectedContent.put(StreamType.CONTEXT, "Context1\nContext1\n");
            expectedContent.put(StreamType.META, "Meta11:1\nMeta12:1\nStreamSize:12\n");

            final HashMap<StreamType, List<String>> expectedBoundaries = new HashMap<StreamType, List<String>>();
            expectedBoundaries.put(null, Arrays.asList("File1\nFile1\n"));
            expectedBoundaries.put(StreamType.CONTEXT, Arrays.asList("Context1\nContext1\n"));
            expectedBoundaries.put(StreamType.META, Arrays.asList("Meta11:1\nMeta12:1\nStreamSize:12\n"));

            doTest(file, 1,
                    new HashSet<String>(Arrays.asList("revt.bgz", "revt.ctx.bgz", "revt.meta.bgz", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            file.delete();
        }

    }

    @Test
    public void testMultiFileWithMetaAndContext() throws IOException {
        final File file = new File(getCurrentTestDir(),
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            // Build a zip with an odd order
            final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom2.dat"));
            zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom1.ctx"));
            zipOut.write("Context1\nContext1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom1.meta"));
            zipOut.write("Meta1a\nMeta1b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom2.meta"));
            zipOut.write("Meta2a\nMeta2b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom2.ctx"));
            zipOut.write("Context2\nContext2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<StreamType, String> expectedContent = new HashMap<StreamType, String>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            expectedContent.put(StreamType.CONTEXT, "Context1\nContext1\nContext2\nContext2\n");
            expectedContent.put(StreamType.META, "Meta1a\nMeta1b\nStreamSize:12\nMeta2a\nMeta2b\nStreamSize:12\n");

            final HashMap<StreamType, List<String>> expectedBoundaries = new HashMap<StreamType, List<String>>();
            expectedBoundaries.put(null, Arrays.asList("File1\nFile1\n", "File2\nFile2\n"));
            expectedBoundaries.put(StreamType.CONTEXT, Arrays.asList("Context1\nContext1\n", "Context2\nContext2\n"));
            expectedBoundaries.put(StreamType.META,
                    Arrays.asList("Meta1a\nMeta1b\nStreamSize:12\n", "Meta2a\nMeta2b\nStreamSize:12\n"));

            doTest(file, 1, new HashSet<String>(Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.ctx.bgz",
                    "revt.ctx.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")), expectedContent,
                    expectedBoundaries);
        } finally {
            file.delete();
        }

    }

    @Test
    public void testMultiFile() throws IOException {
        final File file = new File(getCurrentTestDir(),
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("tom2.dat"));
            zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<StreamType, String> expectedContent = new HashMap<StreamType, String>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            final HashMap<StreamType, List<String>> expectedBoundaries = new HashMap<StreamType, List<String>>();
            expectedBoundaries.put(null, Arrays.asList("File1\nFile1\n", "File2\nFile2\n"));

            doTest(file, 1, new HashSet<String>(
                    Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            file.delete();
        }
    }

    private void doTest(final File file, final int processCount, final Set<String> expectedFiles,
            final HashMap<StreamType, String> expectedContent,
            final HashMap<StreamType, List<String>> expectedBoundaries) throws IOException {
        final Feed eventFeed = commonTestScenarioCreator.createSimpleFeed();

        final MetaMap metaMap = new MetaMap();
        metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);

        final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                .buildSingleHandlerList(streamStore, feedService, null, eventFeed, eventFeed.getStreamType());

        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlerList, new byte[1000],
                "DefaultDataFeedRequest-" + metaMap.get(StroomHeaderArguments.GUID));
        stroomStreamProcessor.setAppendReceivedPath(false);

        for (int i = 0; i < processCount; i++) {
            stroomStreamProcessor.process(new FileInputStream(file), String.valueOf(i));
        }

        stroomStreamProcessor.closeHandlers();

        final FileArrayList files = streamMaintenanceService
                .findAllStreamFile(handlerList.get(0).getStreamSet().iterator().next());

        final HashSet<String> foundFiles = new HashSet<String>();

        for (final File rfile : files) {
            if (rfile.isFile()) {
                String fileName = rfile.getName();
                fileName = fileName.substring(fileName.indexOf(".") + 1);
                foundFiles.add(fileName);
            }
        }
        Assert.assertEquals("Checking expected output files", expectedFiles, foundFiles);

        // Test full content
        StreamSource source = streamStore.openStreamSource(handlerList.get(0).getStreamSet().iterator().next().getId());
        for (final Entry<StreamType, String> entry : expectedContent.entrySet()) {
            final StreamType key = entry.getKey();
            final String content = entry.getValue();
            if (key == null) {
                Assert.assertEquals(content, StreamUtil.streamToString(source.getInputStream()));
            } else {
                Assert.assertEquals(content, StreamUtil.streamToString(source.getChildStream(key).getInputStream()));
            }
        }
        streamStore.closeStreamSource(source);

        // Test boundaries
        source = streamStore.openStreamSource(handlerList.get(0).getStreamSet().iterator().next().getId());
        for (final Entry<StreamType, List<String>> entry : expectedBoundaries.entrySet()) {
            final StreamType key = entry.getKey();
            final List<String> nestedContentList = entry.getValue();
            if (key == null) {
                final RANestedInputStream inputStream = new RANestedInputStream(source);
                for (final String nestedContent : nestedContentList) {
                    Assert.assertTrue(inputStream.getNextEntry());
                    Assert.assertEquals(nestedContent, StreamUtil.streamToString(inputStream, false));
                    inputStream.closeEntry();
                }
                inputStream.close();
            } else {
                final RANestedInputStream inputStream = new RANestedInputStream(source.getChildStream(key));
                for (final String nestedContent : nestedContentList) {
                    Assert.assertTrue(inputStream.getNextEntry());
                    Assert.assertEquals(nestedContent, StreamUtil.streamToString(inputStream, false));
                    inputStream.closeEntry();
                }
                inputStream.close();
            }
        }
        streamStore.closeStreamSource(source);
    }
}
