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

package stroom.streamstore.fs;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

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

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFileSystemUtil extends StroomUnitTest {
    private static final String NO_WRITE_DIR1 = "/usr/bin/username";
    private static final String NO_WRITE_DIR2 = "/unable/to/create/this";

    private Volume buildTestVolume() throws IOException {
        final Volume config = new Volume();
        config.setPath(FileUtil.getCanonicalPath(getCurrentTestDir()));
        config.setNode(buildTestNode());
        return config;
    }

    private Node buildTestNode() {
        final Node node = new Node();
        node.setName("Test");
        return node;
    }

    @Test
    public void testEncode() {
        Assert.assertEquals("ABC", FileSystemUtil.encodeFileName("ABC"));
        Assert.assertEquals("ABC#02f", FileSystemUtil.encodeFileName("ABC/"));

        Assert.assertEquals("ABC#000", FileSystemUtil.encodeFileName("ABC" + ((char) 0)));
        Assert.assertEquals("#023", FileSystemUtil.encodeFileName("#"));
        Assert.assertEquals("#023#023", FileSystemUtil.encodeFileName("##"));

        Assert.assertEquals("#", FileSystemUtil.decodeFileName("#023"));
        Assert.assertEquals("##", FileSystemUtil.decodeFileName("#023#023"));

        Assert.assertEquals("#", FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("#")));
        Assert.assertEquals("//", FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("//")));

        Assert.assertEquals("ABC/", FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("ABC/")));

        Assert.assertEquals("///", FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("///")));
        Assert.assertEquals("#/", FileSystemUtil.decodeFileName(FileSystemUtil.encodeFileName("#/")));

    }

    @Test
    public void testCreateFileTypeRoot() throws IOException {
        final String root = FileUtil.getCanonicalPath(FileSystemUtil.createFileTypeRoot(buildTestVolume()));

        Assert.assertNotNull(root);
        Assert.assertTrue(root.endsWith("store"));
    }

    @Test
    public void testCreateRootStreamFile() throws IOException {
        final Stream md = Stream.createStreamForTesting(StreamType.EVENTS, Feed.createStub(1), null,
                DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));
        md.setId(1001001L);

        final Path rootFile = FileSystemStreamTypeUtil.createRootStreamFile(buildTestVolume(), md, StreamType.EVENTS);

        Assert.assertNotNull(rootFile);
        assertPathEndsWith(rootFile, "EVENTS/2010/01/01/001/001/1=001001001.evt.bgz");
    }

    @Test
    public void testCreateChildStreamFile() throws IOException {
        final Stream md = Stream.createStreamForTesting(StreamType.RAW_EVENTS, Feed.createStub(1), null,
                DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));
        md.setId(1001001L);

        final Path rootFile = FileSystemStreamTypeUtil.createRootStreamFile(buildTestVolume(), md,
                StreamType.RAW_EVENTS);

        touch(rootFile);

        final Path child1 = FileSystemStreamTypeUtil.createChildStreamFile(rootFile, StreamType.CONTEXT);
        touch(child1);
        assertPathEndsWith(child1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.bgz");

        final Path child2 = FileSystemStreamTypeUtil.createChildStreamFile(rootFile, StreamType.SEGMENT_INDEX);
        touch(child2);
        assertPathEndsWith(child2, "EVENTS/2010/01/01/001/001/1=001001001.revt.seg.dat");

        final Path child1_1 = FileSystemStreamTypeUtil.createChildStreamFile(child1, StreamType.SEGMENT_INDEX);
        touch(child1_1);
        assertPathEndsWith(child1_1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.seg.dat");

        final List<Path> kids = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile);
        Assert.assertEquals("should match 3 kids", 3, kids.size());

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

        Assert.assertTrue("Expecting " + fullPath + " to end with " + check, fullPath.endsWith(check));

    }

    @Test
    public void testParentMkdirsAndDelete() {
        final Path dir1 = getCurrentTestDir().resolve(FileSystemTestUtil.getUniqueTestString());
        final Path dir2 = getCurrentTestDir().resolve(FileSystemTestUtil.getUniqueTestString());
        final Path file1 = dir1.resolve("test.dat");
        final Path file2 = dir2.resolve("test.dat");
        final HashSet<Path> files = new HashSet<>();
        files.add(file1);
        files.add(file2);

        FileUtil.mkdirs(file1);
        FileUtil.mkdirs(file2);

        Assert.assertTrue("Dirs exist... but not error",
                FileSystemUtil.mkdirs(FileUtil.getTempDir(), file1.getParent()));
        Assert.assertTrue("Dirs exist... but not error",
                FileSystemUtil.mkdirs(FileUtil.getTempDir(), file2.getParent()));

        Assert.assertTrue("Delete Files", FileSystemUtil.deleteAnyPath(files));
        Assert.assertTrue("Delete Files Gone", FileSystemUtil.deleteAnyPath(files));

        Assert.assertTrue("Delete Files Gone", FileUtil.deleteContents(dir1));
        Assert.assertTrue("Delete Files Gone", FileUtil.deleteContents(dir2));

    }

    @Test
    public void testCreateBadDirs() {
        Assert.assertFalse(FileSystemUtil.mkdirs(null, Paths.get(NO_WRITE_DIR1)));
        Assert.assertFalse(FileSystemUtil.mkdirs(null, Paths.get(NO_WRITE_DIR2)));
    }

    /**
     * Here we batch up creating a load of directories as under load this can
     * fail. This is due to mkdirs not being concurrent (so each thread is
     * trying to create the same dir).
     */
    @Test
    public void testParentMkdirs() throws InterruptedException {
        final HashSet<Path> fileSet = new HashSet<>();

        final String dir = FileSystemTestUtil.getUniqueTestString();
        final Path dir1 = getCurrentTestDir().resolve(dir);
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

        Assert.assertTrue(allOk.get());

        FileUtil.deleteDir(dir1);
    }

    @Test
    public void testMkDirs() {
        final Path rootDir = getCurrentTestDir();
        Assert.assertTrue("Should be OK to create a dir off the root",
                FileSystemUtil.mkdirs(rootDir, rootDir.resolve(FileSystemTestUtil.getUniqueTestString())));
        final Path nonExistingRoot = rootDir.resolve(FileSystemTestUtil.getUniqueTestString());
        Assert.assertFalse("Should be NOT be OK to create a dir off a non existant root", FileSystemUtil
                .mkdirs(nonExistingRoot, nonExistingRoot.resolve(FileSystemTestUtil.getUniqueTestString() + "/a/b")));

    }

    @Test
    public void testDirPath() {
        final Stream data1 = Stream.createStreamForTesting(StreamType.EVENTS, Feed.createStub(2), null,
                DateUtil.parseNormalDateTimeString("2008-11-18T10:00:00.000Z"));
        data1.setId(100100L);

        Assert.assertEquals("EVENTS/2008/11/18/100", FileSystemStreamTypeUtil.getDirectory(data1, StreamType.EVENTS));
        Assert.assertEquals("2=100100", FileSystemStreamTypeUtil.getBaseName(data1));
    }

    @Test
    public void testDirPath2() {
        final Stream data1 = Stream.createStreamForTesting(StreamType.EVENTS, Feed.createStub(2), null,
                DateUtil.parseNormalDateTimeString("2008-11-18T10:00:00.000Z"));

        data1.setId(1100100L);

        Assert.assertEquals("EVENTS/2008/11/18/001/100",
                FileSystemStreamTypeUtil.getDirectory(data1, StreamType.EVENTS));
        Assert.assertEquals("2=001100100", FileSystemStreamTypeUtil.getBaseName(data1));
    }

}
