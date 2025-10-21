/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StreamTargetStreamHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;
import stroom.util.net.HostNameUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemZipProcessor extends AbstractCoreIntegrationTest {

    @Inject
    private Store streamStore;
    @Inject
    private FsFileFinder fileFinder;
    @Inject
    private StreamTargetStreamHandlers streamTargetStreamHandlers;

    private final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.STROOM, "node1");

    @Test
    void testSimpleSingleFile() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            try (final ZipArchiveOutputStream zipOut = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOut.putArchiveEntry(new ZipArchiveEntry("test1.dat"));
                zipOut.write("File1\nFile1\n".getBytes());
                zipOut.closeArchiveEntry();
            }

            final Map<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            final Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            expectedBoundaries.add(map);

            doTest(file,
                    1,
                    Set.of("revt.bgz", "revt.meta.bgz", "revt.mf.dat"),
                    expectedContent,
                    expectedBoundaries,
                    Instant.now(),
                    uniqueIdGenerator.generateId());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testSimpleSingleFileReadThreeTimes() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            try (final ZipArchiveOutputStream zipOut = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOut.putArchiveEntry(new ZipArchiveEntry("test1.dat"));
                zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
            }

            final Map<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile1\nFile1\nFile1\nFile1\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));

            doTest(file,
                    3,
                    Set.of(
                            "revt.bgz",
                            "revt.bdy.dat",
                            "revt.meta.bgz",
                            "revt.meta.bdy.dat",
                            "revt.mf.dat"),
                    expectedContent,
                    expectedBoundaries,
                    Instant.now(),
                    uniqueIdGenerator.generateId());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testSimpleSingleFileWithMetaAndContext() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            try (final ZipArchiveOutputStream zipOut = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOut.putArchiveEntry(new ZipArchiveEntry("test1.dat"));
                zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("test1.ctx"));
                zipOut.write("Context1\nContext1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("test1.meta"));
                zipOut.write("Meta11:1\nMeta12:1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
            }

            final Map<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\n");
            expectedContent.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            expectedContent.put(StreamTypeNames.META, "Meta11:1\nMeta12:1\nStreamSize:12\n");

            final Instant receivedTime = Instant.now();
            final String receivedTimeStr = DateUtil.createNormalDateTimeString(receivedTime);
            final String hostName = HostNameUtil.determineHostName();
            final UniqueId receiptId = uniqueIdGenerator.generateId();

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            final Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            map.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            map.put(StreamTypeNames.META, LogUtil.message("""
                            Meta11:1
                            Meta12:1
                            ReceiptId:{}
                            ReceiptIdPath:{}
                            ReceivedPath:{}
                            ReceivedTime:{}
                            ReceivedTimeHistory:{}
                            StreamSize:12
                            """,
                    receiptId.toString(),
                    receiptId.toString(),
                    hostName,
                    receivedTimeStr,
                    receivedTimeStr));
            expectedBoundaries.add(map);

            doTest(file,
                    1,
                    Set.of("revt.bgz", "revt.ctx.bgz", "revt.meta.bgz", "revt.mf.dat"),
                    expectedContent,
                    expectedBoundaries,
                    receivedTime,
                    receiptId);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testMultiFileWithMetaAndContext() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            // Build a zip with an odd order
            try (final ZipArchiveOutputStream zipOut = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry1.dat"));
                zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry1.ctx"));
                zipOut.write("Context1\nContext1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry1.meta"));
                zipOut.write("Meta1a\nMeta1b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry2.dat"));
                zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry2.meta"));
                zipOut.write("Meta2a\nMeta2b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry2.ctx"));
                zipOut.write("Context2\nContext2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
            }

            final Map<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            expectedContent.put(StreamTypeNames.CONTEXT, "Context1\nContext1\nContext2\nContext2\n");
            expectedContent.put(StreamTypeNames.META, "Meta1a\nMeta1b\nStreamSize:12\nMeta2a\nMeta2b\nStreamSize:12\n");

            final Instant receivedTime = Instant.now();
            final String receivedTimeStr = DateUtil.createNormalDateTimeString(receivedTime);
            final String hostName = HostNameUtil.determineHostName();
            final UniqueId receiptId = uniqueIdGenerator.generateId();

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            map.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            map.put(StreamTypeNames.META, LogUtil.message("""
                            Meta1a
                            Meta1b
                            ReceiptId:{}
                            ReceiptIdPath:{}
                            ReceivedPath:{}
                            ReceivedTime:{}
                            ReceivedTimeHistory:{}
                            StreamSize:12
                            """,
                    receiptId.toString(),
                    receiptId.toString(),
                    hostName,
                    receivedTimeStr,
                    receivedTimeStr));
            expectedBoundaries.add(map);

            map = new HashMap<>();
            map.put(null, "File2\nFile2\n");
            map.put(StreamTypeNames.CONTEXT, "Context2\nContext2\n");
            map.put(StreamTypeNames.META, LogUtil.message("""
                            Meta2a
                            Meta2b
                            ReceiptId:{}
                            ReceiptIdPath:{}
                            ReceivedPath:{}
                            ReceivedTime:{}
                            ReceivedTimeHistory:{}
                            StreamSize:12
                            """,
                    receiptId.toString(),
                    receiptId.toString(),
                    hostName,
                    receivedTimeStr,
                    receivedTimeStr));
            expectedBoundaries.add(map);

            doTest(file,
                    1,
                    Set.of("revt.bgz",
                            "revt.bdy.dat",
                            "revt.ctx.bgz",
                            "revt.ctx.bdy.dat",
                            "revt.meta.bgz",
                            "revt.meta.bdy.dat",
                            "revt.mf.dat"),
                    expectedContent,
                    expectedBoundaries,
                    receivedTime,
                    receiptId);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testMultiFile() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            try (final ZipArchiveOutputStream zipOut = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry1.dat"));
                zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
                zipOut.putArchiveEntry(new ZipArchiveEntry("entry2.dat"));
                zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOut.closeArchiveEntry();
            }

            final Map<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File2\nFile2\n"));

            doTest(file,
                    1,
                    Set.of(
                            "revt.bgz",
                            "revt.bdy.dat",
                            "revt.meta.bgz",
                            "revt.meta.bdy.dat",
                            "revt.mf.dat"),
                    expectedContent,
                    expectedBoundaries,
                    Instant.now(),
                    uniqueIdGenerator.generateId());
        } finally {
            Files.delete(file);
        }
    }

    private void doTest(final Path file,
                        final int processCount,
                        final Set<String> expectedFiles,
                        final Map<String, String> expectedContent,
                        final List<Map<String, String>> expectedBoundaries,
                        final Instant receivedTime,
                        final UniqueId receiptId) throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        // Set the attrs that would normally be set by AttributeMapUtil.create
        AttributeMapUtil.addReceiptInfo(attributeMap, receivedTime, receiptId);

        final AtomicReference<StreamTargetStreamHandler> handlerRef = new AtomicReference<>();
        streamTargetStreamHandlers.handle(feedName, StreamTypeNames.RAW_EVENTS, attributeMap, handler -> {
            handlerRef.set((StreamTargetStreamHandler) handler);

            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                    attributeMap,
                    handler,
                    new ProgressHandler("Test"));
//            stroomStreamProcessor.setAppendReceivedPath(false);

            for (int i = 0; i < processCount; i++) {
                try (final InputStream inputStream = Files.newInputStream(file)) {
                    stroomStreamProcessor.processInputStream(inputStream, String.valueOf(i));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });

        final StreamTargetStreamHandler streamTargetStreamHandler = handlerRef.get();
        final List<Path> files = fileFinder
                .findAllStreamFile(streamTargetStreamHandler.getStreamSet().iterator().next());

        final Set<String> foundFiles = new HashSet<>();

        for (final Path rfile : files) {
            if (Files.isRegularFile(rfile)) {
                String fileName = rfile.getFileName().toString();
                fileName = fileName.substring(fileName.indexOf(".") + 1);
                foundFiles.add(fileName);
            }
        }
        assertThat(foundFiles).as("Checking expected output files")
                .isEqualTo(expectedFiles);

//        // Test full content
//        try (final Source source = streamStore.openSource(
//        handlerList.get(0).getStreamSet().iterator().next().getId())) {
//            try (final InputStreamProvider inputStreamProvider = source.get(0)) {
//                for (final Entry<String, String> entry : expectedContent.entrySet()) {
//                    final String key = entry.getKey();
//                    final String content = entry.getValue();
//                    if (key == null) {
//                        assertThat(StreamUtil.streamToString(inputStreamProvider.get())).isEqualTo(content);
//                    } else {
//                        assertThat(StreamUtil.streamToString(inputStreamProvider.get(key))).isEqualTo(content);
//                    }
//                }
//            }
//        }

        // Test boundaries
        try (final Source source = streamStore.openSource(
                streamTargetStreamHandler.getStreamSet().iterator().next().getId())) {

            int index = 0;
            for (final Map<String, String> map : expectedBoundaries) {
                try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                    index++;

                    map.forEach((key, expected) -> {
                        if (key == null) {
                            try (final InputStream inputStream = inputStreamProvider.get()) {
                                assertThat(inputStream).isNotNull();
                                final String actual = StreamUtil.streamToString(inputStream, false);
                                assertThat(actual).isEqualTo(expected);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        } else {
                            try (final InputStream inputStream = inputStreamProvider.get(key)) {
                                assertThat(inputStream).isNotNull();
                                final String actual = StreamUtil.streamToString(inputStream, false);
                                assertThat(actual).isEqualTo(expected);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                }
            }
        }
    }
}
