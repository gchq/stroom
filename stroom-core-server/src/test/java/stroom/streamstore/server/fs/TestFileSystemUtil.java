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

package stroom.streamstore.server.fs;

import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.FileSystemTestUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFileSystemUtil extends StroomUnitTest {
    private Volume buildTestVolume() throws IOException {
        final Volume config = new Volume();
        config.setPath(getCurrentTestDir().getCanonicalPath());
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
        final String root = FileSystemUtil.createFileTypeRoot(buildTestVolume()).getAbsolutePath();

        Assert.assertNotNull(root);
        Assert.assertTrue(root.endsWith("store"));
    }

    @Test
    public void testCreateRootStreamFile() throws IOException {
        final Stream md = Stream.createStreamForTesting(StreamType.EVENTS, Feed.createStub(1), null,
                DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));
        md.setId(1001001L);

        final File rootFile = FileSystemStreamTypeUtil.createRootStreamFile(buildTestVolume(), md, StreamType.EVENTS);

        Assert.assertNotNull(rootFile);
        assertPathEndsWith(rootFile, "EVENTS/2010/01/01/001/001/1=001001001.evt.bgz");
    }

    @Test
    public void testCreateChildStreamFile() throws IOException {
        final Stream md = Stream.createStreamForTesting(StreamType.RAW_EVENTS, Feed.createStub(1), null,
                DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z"));
        md.setId(1001001L);

        final File rootFile = FileSystemStreamTypeUtil.createRootStreamFile(buildTestVolume(), md,
                StreamType.RAW_EVENTS);

        FileUtils.touch(rootFile);

        final File child1 = FileSystemStreamTypeUtil.createChildStreamFile(rootFile, StreamType.CONTEXT);
        FileUtils.touch(child1);
        assertPathEndsWith(child1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.bgz");

        final File child2 = FileSystemStreamTypeUtil.createChildStreamFile(rootFile, StreamType.SEGMENT_INDEX);
        FileUtils.touch(child2);
        assertPathEndsWith(child2, "EVENTS/2010/01/01/001/001/1=001001001.revt.seg.dat");

        final File child1_1 = FileSystemStreamTypeUtil.createChildStreamFile(child1, StreamType.SEGMENT_INDEX);
        FileUtils.touch(child1_1);
        assertPathEndsWith(child1_1, "EVENTS/2010/01/01/001/001/1=001001001.revt.ctx.seg.dat");

        final List<File> kids = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile);
        Assert.assertEquals("should match 3 kids", 3, kids.size());

        for (final File kid : kids) {
            FileUtil.deleteFile(kid);
        }
        FileUtil.deleteFile(rootFile);
    }

    private void assertPathEndsWith(final File file, String check) {
        String fullPath = file.getAbsolutePath();
        fullPath = fullPath.replace('/', '-');
        fullPath = fullPath.replace('\\', '-');
        check = check.replace('/', '-');
        check = check.replace('\\', '-');

        Assert.assertTrue("Expecting " + fullPath + " to end with " + check, fullPath.endsWith(check));

    }

    @Test
    public void testParentMkdirsAndDelete() {
        final File dir1 = new File(getCurrentTestDir(), FileSystemTestUtil.getUniqueTestString());
        final File dir2 = new File(getCurrentTestDir(), FileSystemTestUtil.getUniqueTestString());
        final File file1 = new File(dir1, "test.dat");
        final File file2 = new File(dir2, "test.dat");
        final HashSet<File> files = new HashSet<File>();
        files.add(file1);
        files.add(file2);

        Assert.assertTrue("Create dirs", FileSystemUtil.mkdirs(getCurrentTestDir(), file1.getParentFile()));
        Assert.assertTrue("Create dirs", FileSystemUtil.mkdirs(getCurrentTestDir(), file2.getParentFile()));

        Assert.assertTrue("Dirs exist... but not error",
                FileSystemUtil.mkdirs(FileUtil.getTempDir(), file1.getParentFile()));
        Assert.assertTrue("Dirs exist... but not error",
                FileSystemUtil.mkdirs(FileUtil.getTempDir(), file2.getParentFile()));

        Assert.assertTrue("Delete Files", FileSystemUtil.deleteAnyFile(files));
        Assert.assertTrue("Delete Files Gone", FileSystemUtil.deleteAnyFile(files));

        Assert.assertTrue("Delete Files Gone", FileSystemUtil.deleteContents(dir1));
        Assert.assertTrue("Delete Files Gone", FileSystemUtil.deleteContents(dir2));

    }

    public static final String NO_WRITE_DIR1 = "/usr/bin/username";
    public static final String NO_WRITE_DIR2 = "/unable/to/create/this";

    @Test
    public void testCreateBadDirs() {
        Assert.assertFalse(FileSystemUtil.mkdirs(null, new File(NO_WRITE_DIR1)));
        Assert.assertFalse(FileSystemUtil.mkdirs(null, new File(NO_WRITE_DIR2)));
    }

    /**
     * Here we batch up creating a load of directories as under load this can
     * fail. This is due to mkdirs not being concurrent (so each thread is
     * trying to create the same dir).
     */
    @Test
    public void testParentMkdirs() throws Exception {
        final HashSet<File> fileSet = new HashSet<>();

        final String dir = FileSystemTestUtil.getUniqueTestString();
        final File dir1 = new File(getCurrentTestDir(), dir);
        // Create 100 files in 10 similar directories
        for (int i = 0; i < 100; i++) {
            final File dir2 = new File(dir1, dir);
            final File dir3 = new File(dir2, "" + (i % 2));
            final File dir4 = new File(dir3, "" + (i % 2));
            final File dir5 = new File(dir4, "" + (i % 2));
            final File file = new File(dir5, "test.dat");
            fileSet.add(file);
        }

        final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(100);

        for (int i = 0; i < 100; i++) {
            final Runnable r = () -> {
                for (final File file : fileSet) {
                    setOk(FileSystemUtil.mkdirs(null, file.getParentFile()));
                }
            };
            threadPoolExecutor.submit(r);
        }

        threadPoolExecutor.shutdown();

        threadPoolExecutor.awaitTermination(100, TimeUnit.SECONDS);

        Assert.assertTrue(allOk);

        FileSystemUtil.deleteDirectory(dir1);
    }

    private volatile boolean allOk = true;

    private synchronized void setOk(final boolean ok) {
        if (!ok) {
            System.out.println("One thread failed!");
            allOk = false;
        }
    }

    @Test
    public void testMkDirs() {
        final File rootDir = getCurrentTestDir();
        Assert.assertTrue("Should be OK to create a dir off the root",
                FileSystemUtil.mkdirs(rootDir, new File(rootDir, FileSystemTestUtil.getUniqueTestString())));
        final File nonExistingRoot = new File(rootDir, FileSystemTestUtil.getUniqueTestString());
        Assert.assertFalse("Should be NOT be OK to create a dir off a non existant root", FileSystemUtil
                .mkdirs(nonExistingRoot, new File(nonExistingRoot, FileSystemTestUtil.getUniqueTestString() + "/a/b")));

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
