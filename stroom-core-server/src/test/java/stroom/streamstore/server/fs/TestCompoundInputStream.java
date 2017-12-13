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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.streamstore.server.fs.serializable.CompoundInputStream;
import stroom.streamstore.server.fs.serializable.NestedInputStream;
import stroom.streamstore.server.fs.serializable.NestedOutputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedOutputStream;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCompoundInputStream extends StroomUnitTest {
    private Path datFile;
    private Path segFile;
    private Path bdyFile;

    @Before
    public void setup() {
        final Path dir = getCurrentTestDir();
        datFile = dir.resolve("test.bzg");
        segFile = dir.resolve("test.seg.dat");
        bdyFile = dir.resolve("test.bdy.dat");
    }

    @After
    public void clean() {
        FileUtil.deleteFile(datFile);
        FileUtil.deleteFile(segFile);
        FileUtil.deleteFile(bdyFile);
    }

    public void setup(final int bdyCount, final int segPerBdy) throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final NestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

        for (int b = 1; b <= bdyCount; b++) {
            boundaryStream.putNextEntry();
            for (int s = 1; s <= segPerBdy; s++) {
                boundaryStream.write(("B=" + b + ",S=" + s + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
                if (s < segPerBdy) {
                    segmentStream.addSegment();
                }
            }
            boundaryStream.closeEntry();
        }

        boundaryStream.close();

        Assert.assertTrue(Files.isRegularFile(datFile));
        if (segPerBdy > 1) {
            Assert.assertTrue(Files.isRegularFile(segFile));
        } else {
            Assert.assertFalse(Files.isRegularFile(segFile));
        }
        if (bdyCount > 1) {
            Assert.assertTrue(Files.isRegularFile(bdyFile));
        } else {
            Assert.assertFalse(Files.isRegularFile(bdyFile));
        }

    }

    // @Test BROKEN
    public void testBlanks() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final NestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));
        // 1
        boundaryStream.putNextEntry();
        segmentStream.addSegment();
        boundaryStream.closeEntry();
        // 2
        boundaryStream.putNextEntry();
        segmentStream.addSegment();
        boundaryStream.closeEntry();
        // 3
        boundaryStream.putNextEntry();
        segmentStream.addSegment();
        boundaryStream.closeEntry();

        segmentStream.flush();
        boundaryStream.flush();
        boundaryStream.close();

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("", StreamUtil.streamToString(seg));

        seg = compoundInputStream.getNextInputStream(0);
        seg.include(1);
        Assert.assertEquals("", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testNoSegOrBdyData() throws IOException {
        setup(1, 1);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("B=1,S=1\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();

    }

    @Test
    public void testNoSegData() throws IOException {
        setup(1, 0);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("", StreamUtil.streamToString(seg));

        compoundInputStream.close();

    }

    @Test
    public void testNoBdyOrSeg() throws IOException {
        setup(1, 1);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("B=1,S=1\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testSmall1() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("B=1,S=1\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testSmall2() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(1);
        Assert.assertEquals("B=1,S=2\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testSmall3() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(1);
        seg.include(0);
        Assert.assertEquals("B=2,S=1\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testSmall4() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(1);
        seg.include(1);
        Assert.assertEquals("B=2,S=2\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testSmallIOError1() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        try {
            compoundInputStream.getNextInputStream(2);
            Assert.fail("Expecting IO Error");
        } catch (final Exception ex) {
        }
        compoundInputStream.close();
    }

    @Test
    public void testSmallIOError2() throws IOException {
        setup(2, 2);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        RASegmentInputStream seg = null;

        try {
            seg = compoundInputStream.getNextInputStream(1);
            seg.include(2);
            Assert.fail("Expecting IO Error");
        } catch (final Exception ex) {
        }
        StreamUtil.close(seg);
        compoundInputStream.close();
    }

    @Test
    public void testBdyWithNoSegs1() throws IOException {
        setup(100, 1);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        Assert.assertEquals("B=1,S=1\n", StreamUtil.streamToString(seg));
        compoundInputStream.close();
    }

    @Test
    public void testBdyWithNoSegs2() throws IOException {
        setup(100, 1);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(99);
        Assert.assertEquals("B=100,S=1\n", StreamUtil.streamToString(seg));

        compoundInputStream.close();
    }

    @Test
    public void testNoBdyWithSegs1() throws IOException {
        setup(1, 100);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        final RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        Assert.assertEquals("B=1,S=1\n", StreamUtil.streamToString(seg));
        compoundInputStream.close();
    }

    @Test
    public void testNoBdyWithSegs2WithReuse() throws IOException {
        setup(2, 100);

        final CompoundInputStream compoundInputStream = new CompoundInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true), new UncompressedInputStream(segFile, true));

        RASegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(99);
        Assert.assertEquals("B=1,S=100\n", StreamUtil.streamToString(new IgnoreCloseInputStream(seg)));

        seg = compoundInputStream.getNextInputStream(0);
        seg.include(99);
        Assert.assertEquals("B=2,S=100\n", StreamUtil.streamToString(new IgnoreCloseInputStream(seg)));

        compoundInputStream.close();
    }

    @Test
    public void testBroken() throws IOException {
        setup(10, 10);

        final NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        nestedInputStream.getNextEntry();
        nestedInputStream.closeEntry();
        nestedInputStream.getNextEntry();

        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true), nestedInputStream.entryByteOffsetStart(),
                nestedInputStream.entryByteOffsetEnd());

        segmentInputStream.include(0);
        segmentInputStream.include(1);
        segmentInputStream.include(9);

        Assert.assertEquals("B=2,S=1\nB=2,S=2\nB=2,S=10\n", StreamUtil.streamToString(segmentInputStream, true));
        nestedInputStream.closeEntry();
        nestedInputStream.close();

    }

    @Test
    public void testSmallBigScan() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();

        }
        nestedInputStream.close();

        // Overlay a segment input stream
        nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final RASegmentInputStream segmentInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                    new UncompressedInputStream(segFile, true), nestedInputStream.entryByteOffsetStart(),
                    nestedInputStream.entryByteOffsetEnd());

            Assert.assertEquals("Expecting 10 segments", 10, segmentInputStream.count());

            final String full = "[" + StreamUtil.streamToString(segmentInputStream, true) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();

        }
        nestedInputStream.close();

        // Overlay a segment input and read fist and last segment
        nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final RASegmentInputStream segmentInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                    new UncompressedInputStream(segFile, true), nestedInputStream.entryByteOffsetStart(),
                    nestedInputStream.entryByteOffsetEnd());

            segmentInputStream.include(0);
            segmentInputStream.include(1);
            segmentInputStream.include(9);

            final String full = "[" + StreamUtil.streamToString(segmentInputStream, true) + "]";

            final String expectedStart = "[B=" + b + ",S=1\nB=" + b + ",S=2\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();

        }
        nestedInputStream.close();

        final StringBuilder failed = new StringBuilder();
        final StringBuilder working = new StringBuilder();

        for (int b = 1; b <= 10; b++) {
            for (int s = 1; s <= 10; s++) {
                final String check = "B=" + b + ",S=" + s + "\n";

                try {
                    final CompoundInputStream compoundInputStream = new CompoundInputStream(
                            new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true),
                            new UncompressedInputStream(segFile, true));

                    final RASegmentInputStream seg = compoundInputStream.getNextInputStream(b - 1);
                    seg.include(s - 1);
                    final String actual = StreamUtil.streamToString(seg);
                    if (!check.equals(actual)) {
                        failed.append("Expected :" + check + "Actual   :" + actual);
                    } else {
                        working.append("Passed   :" + actual);
                    }
                    seg.close();
                    compoundInputStream.close();
                } catch (final Exception ex) {
                    failed.append("Exception :" + check);

                }
            }
        }

        Assert.assertTrue(working.toString() + failed.toString(), failed.length() == 0);
    }

    @Test
    public void testNextEntry() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    public void testNextEntrySkip() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        nestedInputStream.getNextEntry(3);
        nestedInputStream.closeEntry();
        for (int b = 5; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    public void testGetEntryForward() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getEntry(b - 1);

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    public void testGetEntryBackward() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final NestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        for (int b = 10; b >= 1; b--) {
            nestedInputStream.getEntry(b - 1);

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            Assert.assertTrue(full + " did not start with " + expectedStart, full.startsWith(expectedStart));
            Assert.assertTrue(full + " did not end with " + expectedEnd, full.endsWith(expectedEnd));

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }
}
