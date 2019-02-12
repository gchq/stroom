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


import org.junit.jupiter.api.Test;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.StandardHeaderArguments;
import stroom.receive.common.StreamTargetStroomStreamHandler;
import stroom.receive.common.StroomStreamProcessor;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemZipProcessor extends AbstractCoreIntegrationTest {
    @Inject
    private Store streamStore;
    @Inject
    private FeedProperties feedProperties;
    @Inject
    private FileSystemDataStoreMaintenanceService streamMaintenanceService;

    @Test
    void testSimpleSingleFile() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes());
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            expectedBoundaries.add(map);

            doTest(file, 1, new HashSet<>(Arrays.asList("revt.bgz", "revt.meta.bgz", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testSimpleSingleFileReadThreeTimes() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("tom1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile1\nFile1\nFile1\nFile1\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));

            doTest(file, 3, new HashSet<>(
                            Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testSimpleSingleFileWithMetaAndContext() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file));
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

            final HashMap<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\n");
            expectedContent.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            expectedContent.put(StreamTypeNames.META, "Meta11:1\nMeta12:1\nStreamSize:12\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            map.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            map.put(StreamTypeNames.META, "Meta11:1\nMeta12:1\nStreamSize:12\n");
            expectedBoundaries.add(map);

            doTest(file, 1,
                    new HashSet<>(Arrays.asList("revt.bgz", "revt.ctx.bgz", "revt.meta.bgz", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
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
            final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("entry1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry1.ctx"));
            zipOut.write("Context1\nContext1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry1.meta"));
            zipOut.write("Meta1a\nMeta1b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry2.dat"));
            zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry2.meta"));
            zipOut.write("Meta2a\nMeta2b\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry2.ctx"));
            zipOut.write("Context2\nContext2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            expectedContent.put(StreamTypeNames.CONTEXT, "Context1\nContext1\nContext2\nContext2\n");
            expectedContent.put(StreamTypeNames.META, "Meta1a\nMeta1b\nStreamSize:12\nMeta2a\nMeta2b\nStreamSize:12\n");

            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            Map<String, String> map = new HashMap<>();
            map.put(null, "File1\nFile1\n");
            map.put(StreamTypeNames.CONTEXT, "Context1\nContext1\n");
            map.put(StreamTypeNames.META, "Meta1a\nMeta1b\nStreamSize:12\n");
            expectedBoundaries.add(map);
            map = new HashMap<>();
            map.put(null, "File2\nFile2\n");
            map.put(StreamTypeNames.CONTEXT, "Context2\nContext2\n");
            map.put(StreamTypeNames.META, "Meta2a\nMeta2b\nStreamSize:12\n");
            expectedBoundaries.add(map);

            doTest(file, 1, new HashSet<>(Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.ctx.bgz",
                    "revt.ctx.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")), expectedContent,
                    expectedBoundaries);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testMultiFile() throws IOException {
        final Path file = getCurrentTestDir().resolve(
                FileSystemTestUtil.getUniqueTestString() + "TestFileSystemZipProcessor.zip");
        try {
            final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(file));
            zipOut.putNextEntry(new ZipEntry("entry1.dat"));
            zipOut.write("File1\nFile1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("entry2.dat"));
            zipOut.write("File2\nFile2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOut.closeEntry();
            zipOut.close();

            final HashMap<String, String> expectedContent = new HashMap<>();
            expectedContent.put(null, "File1\nFile1\nFile2\nFile2\n");
            final List<Map<String, String>> expectedBoundaries = new ArrayList<>();
            expectedBoundaries.add(Collections.singletonMap(null, "File1\nFile1\n"));
            expectedBoundaries.add(Collections.singletonMap(null, "File2\nFile2\n"));

            doTest(file, 1, new HashSet<>(
                            Arrays.asList("revt.bgz", "revt.bdy.dat", "revt.meta.bgz", "revt.meta.bdy.dat", "revt.mf.dat")),
                    expectedContent, expectedBoundaries);
        } finally {
            Files.delete(file);
        }
    }

    private void doTest(final Path file, final int processCount, final Set<String> expectedFiles,
                        final HashMap<String, String> expectedContent,
                        final List<Map<String, String>> expectedBoundaries) throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                .buildSingleHandlerList(streamStore, feedProperties, null, feedName, StreamTypeNames.RAW_EVENTS);

        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, handlerList, new byte[1000],
                "DefaultDataFeedRequest-" + attributeMap.get(StandardHeaderArguments.GUID));
        stroomStreamProcessor.setAppendReceivedPath(false);

        for (int i = 0; i < processCount; i++) {
            stroomStreamProcessor.process(Files.newInputStream(file), String.valueOf(i));
        }

        stroomStreamProcessor.closeHandlers();

        final List<Path> files = streamMaintenanceService
                .findAllStreamFile(handlerList.get(0).getStreamSet().iterator().next());

        final HashSet<String> foundFiles = new HashSet<>();

        for (final Path rfile : files) {
            if (Files.isRegularFile(rfile)) {
                String fileName = rfile.getFileName().toString();
                fileName = fileName.substring(fileName.indexOf(".") + 1);
                foundFiles.add(fileName);
            }
        }
        assertThat(foundFiles).as("Checking expected output files").isEqualTo(expectedFiles);

//        // Test full content
//        try (final Source source = streamStore.openStreamSource(handlerList.get(0).getStreamSet().iterator().next().getId())) {
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
        try (final Source source = streamStore.openStreamSource(handlerList.get(0).getStreamSet().iterator().next().getId())) {
            int index = 0;
            for (final Map<String, String> map : expectedBoundaries) {
                try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                    index++;

                    map.forEach((key, value) -> {
                        if (key == null) {
                            try (final InputStream inputStream = inputStreamProvider.get()) {
                                assertThat(inputStream).isNotNull();
                                assertThat(StreamUtil.streamToString(inputStream, false)).isEqualTo(value);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        } else {
                            try (final InputStream inputStream = inputStreamProvider.get(key)) {
                                assertThat(inputStream).isNotNull();
                                assertThat(StreamUtil.streamToString(inputStream, false)).isEqualTo(value);
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
