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

package stroom.data.store.impl.fs;

import org.junit.jupiter.api.Test;
import stroom.data.meta.api.Stream;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.test.FileSystemTestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFileSystemUtil {
    private static final String NO_WRITE_DIR1 = "/usr/bin/username";
    private static final String NO_WRITE_DIR2 = "/unable/to/create/this";

    private VolumeEntity buildTestVolume() {
        final VolumeEntity config = new VolumeEntity();
        config.setPath(FileUtil.getCanonicalPath(FileUtil.getTempDir()));
        config.setNode(buildTestNode());
        return config;
    }

    private Node buildTestNode() {
        final Node node = new Node();
        node.setName("Test");
        return node;
    }

    @Test
    void testEncode() {
        assertThat(FileSystemUtil.encodeFileName("ABC")).isEqualTo("ABC");
        assertThat(FileSystemUtil.encodeFileName("ABC/")).isEqualTo("ABC#02f");

        assertThat(FileSystemUtil.encodeFileName("ABC" + ((char) 0))).isEqualTo("ABC#000");
        assertThat(FileSystemUtil.encodeFileName("#")).isEqualTo("#023");
        assertThat(FileSystemUtil.encodeFileName("##")).isEqualTo("#023#023");

        assertThat(FileSystemUtil.decodeFileName("#023")).isEqualTo("#");
        assertThat(FileSystemUtil.decodeFileName("#023#023")).isEqualTo("##");

        assertThat(FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("#"))).isEqualTo("#");
        assertThat(FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("//"))).isEqualTo("//");

        assertThat(FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("ABC/"))).isEqualTo("ABC/");

        assertThat(FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("///"))).isEqualTo("///");
        assertThat(FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("#/"))).isEqualTo("#/");

    }

    @Test
    void testCreateFileTypeRoot() {
        final String root = FileUtil.getCanonicalPath(FileSystemUtil.createFileTypeRoot(buildTestVolume()));
        assertThat(root).isNotNull();
        assertThat(root).endsWith("store");
    }

    @Test
    void testCreateRootStreamFile() {
        final Stream stream = mock(Stream.class);
        when(stream.getId()).thenReturn(1001001L);
        when(stream.getStreamTypeName()).thenReturn(StreamTypeNames.EVENTS);
        when(stream.getFeedName()).thenReturn("TEST_FEED");
        when(stream.getCreateMs()).thenReturn(DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));

        final FileSystemFeedPaths fileSystemFeedPaths = mock(FileSystemFeedPaths.class);
        when(fileSystemFeedPaths.getPath(any())).thenReturn("1");

        final FileSystemStreamPathHelper fileSystemStreamPathHelper = new FileSystemStreamPathHelper(fileSystemFeedPaths);

        final Path rootFile = fileSystemStreamPathHelper.createRootStreamFile(buildTestVolume().getPath(), stream, StreamTypeNames.EVENTS);

        assertThat(rootFile).isNotNull();
        assertPathEndsWith(rootFile, "EVENTS/2010/01/01/001/001/1=001001001.evt.bgz");
    }

    @Test
    void testCreateChildStreamFile() throws IOException {
        final Stream stream = mock(Stream.class);
        when(stream.getId()).thenReturn(1001001L);
        when(stream.getStreamTypeName()).thenReturn(StreamTypeNames.RAW_EVENTS);
        when(stream.getFeedName()).thenReturn("TEST_FEED");
        when(stream.getCreateMs()).thenReturn(DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));

        final FileSystemFeedPaths fileSystemFeedPaths = mock(FileSystemFeedPaths.class);
        when(fileSystemFeedPaths.getPath(any())).thenReturn("1");

        final FileSystemStreamPathHelper fileSystemStreamPathHelper = new FileSystemStreamPathHelper(fileSystemFeedPaths);

        final Path rootFile = fileSystemStreamPathHelper.createRootStreamFile(buildTestVolume().getPath(), stream,
                StreamTypeNames.RAW_EVENTS);

        touch(rootFile);

        final Path child1 = fileSystemStreamPathHelper.createChildStreamFile(rootFile, StreamTypeNames.CONTEXT);
        touch(child1);
        assertPathEndsWith(child1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.bgz");

        final Path child2 = fileSystemStreamPathHelper.createChildStreamFile(rootFile, InternalStreamTypeNames.SEGMENT_INDEX);
        touch(child2);
        assertPathEndsWith(child2, "EVENTS/2010/01/01/001/001/1=001001001.revt.seg.dat");

        final Path child1_1 = fileSystemStreamPathHelper.createChildStreamFile(child1, InternalStreamTypeNames.SEGMENT_INDEX);
        touch(child1_1);
        assertPathEndsWith(child1_1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.seg.dat");

        final List<Path> kids = fileSystemStreamPathHelper.findAllDescendantStreamFileList(rootFile);
        assertThat(kids.size()).withFailMessage("should match 3 kids").isEqualTo(3);

        for (final Path kid : kids) {
            FileUtil.deleteFile(kid);
        }
        FileUtil.deleteFile(rootFile);
    }

    private void touch(final Path path) throws IOException {
        FileUtil.mkdirs(path.getParent());
        FileUtil.touch(path);
    }

    private void assertPathEndsWith(final Path file, String check) {
        String fullPath = FileUtil.getCanonicalPath(file);
        fullPath = fullPath.replace('/', '-');
        fullPath = fullPath.replace('\\', '-');
        check = check.replace('/', '-');
        check = check.replace('\\', '-');
        assertThat(fullPath).withFailMessage("Expecting " + fullPath + " to end with " + check).endsWith(check);
    }

    @Test
    void testParentMkdirsAndDelete() {
        final Path dir1 = FileUtil.getTempDir().resolve(FileSystemTestUtil.getUniqueTestString());
        final Path dir2 = FileUtil.getTempDir().resolve(FileSystemTestUtil.getUniqueTestString());
        final Path file1 = dir1.resolve("test.dat");
        final Path file2 = dir2.resolve("test.dat");
        final HashSet<Path> files = new HashSet<>();
        files.add(file1);
        files.add(file2);

        FileUtil.mkdirs(file1);
        FileUtil.mkdirs(file2);

        assertThat(FileSystemUtil.mkdirs(FileUtil.getTempDir(), file1.getParent())).withFailMessage("Dirs exist... but not error").isTrue();
        assertThat(FileSystemUtil.mkdirs(FileUtil.getTempDir(), file2.getParent())).withFailMessage("Dirs exist... but not error").isTrue();

        assertThat(FileSystemUtil.deleteAnyPath(files)).withFailMessage("Delete Files").isTrue();
        assertThat(FileSystemUtil.deleteAnyPath(files)).withFailMessage("Delete Files Gone").isTrue();

        assertThat(FileUtil.deleteContents(dir1)).withFailMessage("Delete Files Gone").isTrue();
        assertThat(FileUtil.deleteContents(dir2)).withFailMessage("Delete Files Gone").isTrue();
    }

    @Test
    void testCreateBadDirs() {
        assertThat(FileSystemUtil.mkdirs(null, Paths.get(NO_WRITE_DIR1))).isFalse();
        assertThat(FileSystemUtil.mkdirs(null, Paths.get(NO_WRITE_DIR2))).isFalse();
    }

    /**
     * Here we batch up creating a load of directories as under load this can
     * fail. This is due to mkdirs not being concurrent (so each thread is
     * trying to create the same dir).
     */
    @Test
    void testParentMkdirs() throws InterruptedException {
        final HashSet<Path> fileSet = new HashSet<>();

        final String dir = FileSystemTestUtil.getUniqueTestString();
        final Path dir1 = FileUtil.getTempDir().resolve(dir);
        // Create 100 files in 10 similar directories
        for (int i = 0; i < 100; i++) {
            final Path dir2 = dir1.resolve(dir);
            final Path dir3 = dir2.resolve("" + (i % 2));
            final Path dir4 = dir3.resolve("" + (i % 2));
            final Path dir5 = dir4.resolve("" + (i % 2));
            final Path file = dir5.resolve("test.dat");
            fileSet.add(file);
        }

        final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(100);

        final AtomicBoolean allOk = new AtomicBoolean(true);
        for (int i = 0; i < 100; i++) {
            final Runnable r = () -> {
                for (final Path file : fileSet) {
                    try {
                        Files.createDirectories(file.getParent());
                    } catch (final IOException e) {
                        allOk.set(false);
                    }
                }
            };
            threadPoolExecutor.submit(r);
        }

        threadPoolExecutor.shutdown();

        threadPoolExecutor.awaitTermination(100, TimeUnit.SECONDS);

        assertThat(allOk.get()).isTrue();

        FileUtil.deleteDir(dir1);
    }

    @Test
    void testMkDirs() {
        final Path rootDir = FileUtil.getTempDir();
        assertThat(FileSystemUtil.mkdirs(rootDir, rootDir.resolve(FileSystemTestUtil.getUniqueTestString())))
                .withFailMessage("Should be OK to create a dir off the root")
                .isTrue();
        final Path nonExistingRoot = rootDir.resolve(FileSystemTestUtil.getUniqueTestString());
        assertThat(FileSystemUtil
                .mkdirs(nonExistingRoot, nonExistingRoot.resolve(FileSystemTestUtil.getUniqueTestString() + "/a/b")))
                .withFailMessage("Should be NOT be OK to create a dir off a non existant root")
                .isFalse();

    }

    @Test
    void testDirPath() {
        final Stream stream = mock(Stream.class);
        when(stream.getId()).thenReturn(100100L);
        when(stream.getStreamTypeName()).thenReturn(StreamTypeNames.EVENTS);
        when(stream.getFeedName()).thenReturn("TEST_FEED");
        when(stream.getCreateMs()).thenReturn(DateUtil.parseNormalDateTimeString("2008-11-18T10:00:00.000Z"));

        final FileSystemFeedPaths fileSystemFeedPaths = mock(FileSystemFeedPaths.class);
        when(fileSystemFeedPaths.getPath(any())).thenReturn("2");

        final FileSystemStreamPathHelper fileSystemStreamPathHelper = new FileSystemStreamPathHelper(fileSystemFeedPaths);

        assertThat(fileSystemStreamPathHelper.getDirectory(stream, StreamTypeNames.EVENTS)).isEqualTo("EVENTS/2008/11/18/100");
        assertThat(fileSystemStreamPathHelper.getBaseName(stream)).isEqualTo("2=100100");
    }

    @Test
    void testDirPath2() {
        final Stream stream = mock(Stream.class);
        when(stream.getId()).thenReturn(1100100L);
        when(stream.getStreamTypeName()).thenReturn(StreamTypeNames.EVENTS);
        when(stream.getFeedName()).thenReturn("TEST_FEED");
        when(stream.getCreateMs()).thenReturn(DateUtil.parseNormalDateTimeString("2008-11-18T10:00:00.000Z"));

        final FileSystemFeedPaths fileSystemFeedPaths = mock(FileSystemFeedPaths.class);
        when(fileSystemFeedPaths.getPath(any())).thenReturn("2");

        final FileSystemStreamPathHelper fileSystemStreamPathHelper = new FileSystemStreamPathHelper(fileSystemFeedPaths);

        assertThat(fileSystemStreamPathHelper.getDirectory(stream, StreamTypeNames.EVENTS)).isEqualTo("EVENTS/2008/11/18/001/100");
        assertThat(fileSystemStreamPathHelper.getBaseName(stream)).isEqualTo("2=001100100");
    }
}
