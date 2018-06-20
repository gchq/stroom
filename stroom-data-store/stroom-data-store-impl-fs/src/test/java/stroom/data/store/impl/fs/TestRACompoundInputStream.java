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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.data.store.api.NestedOutputStream;
import stroom.data.store.api.SegmentInputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

class TestRACompoundInputStream {
    private Path datFile;
    private Path segFile;
    private Path bdyFile;

    @BeforeEach
    void setup() {
        final Path dir = FileUtil.getTempDir();
        datFile = dir.resolve("test.bzg");
        segFile = dir.resolve("test.seg.dat");
        bdyFile = dir.resolve("test.bdy.dat");
    }

    @AfterEach
    void clean() {
        FileUtil.deleteFile(datFile);
        FileUtil.deleteFile(segFile);
        FileUtil.deleteFile(bdyFile);
    }

    private void setup(final int bdyCount, final int segPerBdy) throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
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

        assertThat(Files.isRegularFile(datFile)).isTrue();
        if (segPerBdy > 1) {
            assertThat(Files.isRegularFile(segFile)).isTrue();
        } else {
            assertThat(Files.isRegularFile(segFile)).isFalse();
        }
        if (bdyCount > 1) {
            assertThat(Files.isRegularFile(bdyFile)).isTrue();
        } else {
            assertThat(Files.isRegularFile(bdyFile)).isFalse();
        }

    }

    // @Test BROKEN
    void testBlanks() throws IOException {
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

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("");

        seg = compoundInputStream.getNextInputStream(0);
        seg.include(1);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("");

        compoundInputStream.close();
    }

    @Test
    void testNoSegOrBdyData() throws IOException {
        setup(1, 1);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=1\n");

        compoundInputStream.close();

    }

    @Test
    void testNoSegData() throws IOException {
        setup(1, 0);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("");

        compoundInputStream.close();

    }

    @Test
    void testNoBdyOrSeg() throws IOException {
        setup(1, 1);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=1\n");

        compoundInputStream.close();
    }

    @Test
    void testSmall1() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=1\n");

        compoundInputStream.close();
    }

    @Test
    void testSmall2() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(1);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=2\n");

        compoundInputStream.close();
    }

    @Test
    void testSmall3() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(1);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=2,S=1\n");

        compoundInputStream.close();
    }

    @Test
    void testSmall4() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(1);
        seg.include(1);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=2,S=2\n");

        compoundInputStream.close();
    }

    @Test
    void testSmallIOError1() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        try {
            compoundInputStream.getNextInputStream(2);
            fail("Expecting IO Error");
        } catch (final IOException e) {
        }
        compoundInputStream.close();
    }

    @Test
    void testSmallIOError2() throws IOException {
        setup(2, 2);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        SegmentInputStream seg = null;

        try {
            seg = compoundInputStream.getNextInputStream(1);
            seg.include(2);
            fail("Expecting IO Error");
        } catch (final RuntimeException e) {
        }
        StreamUtil.close(seg);
        compoundInputStream.close();
    }

    @Test
    void testBdyWithNoSegs1() throws IOException {
        setup(100, 1);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=1\n");
        compoundInputStream.close();
    }

    @Test
    void testBdyWithNoSegs2() throws IOException {
        setup(100, 1);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(99);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=100,S=1\n");

        compoundInputStream.close();
    }

    @Test
    void testNoBdyWithSegs1() throws IOException {
        setup(1, 100);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        final SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(0);
        assertThat(StreamUtil.streamToString(seg)).isEqualTo("B=1,S=1\n");
        compoundInputStream.close();
    }

    @Test
    void testNoBdyWithSegs2WithReuse() throws IOException {
        setup(2, 100);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
        final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nestedInputStream, new UncompressedInputStream(segFile, true));

        SegmentInputStream seg = compoundInputStream.getNextInputStream(0);
        seg.include(99);
        assertThat(StreamUtil.streamToString(new IgnoreCloseInputStream(seg))).isEqualTo("B=1,S=100\n");

        seg = compoundInputStream.getNextInputStream(0);
        seg.include(99);
        assertThat(StreamUtil.streamToString(new IgnoreCloseInputStream(seg))).isEqualTo("B=2,S=100\n");

        compoundInputStream.close();
    }

    @Test
    void testBroken() throws IOException {
        setup(10, 10);

        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
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

        assertThat(StreamUtil.streamToString(segmentInputStream, true)).isEqualTo("B=2,S=1\nB=2,S=2\nB=2,S=10\n");
        nestedInputStream.closeEntry();
        nestedInputStream.close();

    }

    @Test
    void testSmallBigScan() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

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

            assertThat(segmentInputStream.count()).withFailMessage("Expecting 10 segments").isEqualTo(10);

            final String full = "[" + StreamUtil.streamToString(segmentInputStream, true) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

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

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

            nestedInputStream.closeEntry();

        }
        nestedInputStream.close();

        final StringBuilder failed = new StringBuilder();
        final StringBuilder working = new StringBuilder();

        for (int b = 1; b <= 10; b++) {
            for (int s = 1; s <= 10; s++) {
                final String check = "B=" + b + ",S=" + s + "\n";

                try {
                    final RANestedInputStream nis = new RANestedInputStream(new BlockGZIPInputFile(datFile), new UncompressedInputStream(bdyFile, true));
                    final RACompoundInputStream compoundInputStream = new RACompoundInputStream(nis, new UncompressedInputStream(segFile, true));

                    final SegmentInputStream seg = compoundInputStream.getNextInputStream(b - 1);
                    seg.include(s - 1);
                    final String actual = StreamUtil.streamToString(seg);
                    if (!check.equals(actual)) {
                        failed.append("Expected :").append(check).append("Actual   :").append(actual);
                    } else {
                        working.append("Passed   :").append(actual);
                    }
                    seg.close();
                    compoundInputStream.close();
                } catch (final RuntimeException e) {
                    failed.append("Exception :").append(check);

                }
            }
        }

        assertThat(failed.length()).withFailMessage(working.toString() + failed.toString()).isEqualTo(0);
    }

    @Test
    void testNextEntry() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));
        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    void testNextEntrySkip() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        nestedInputStream.getNextEntry(3);
        nestedInputStream.closeEntry();
        for (int b = 5; b <= 10; b++) {
            nestedInputStream.getNextEntry();

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    void testGetEntryForward() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        for (int b = 1; b <= 10; b++) {
            nestedInputStream.getEntry(b - 1);

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }

    @Test
    void testGetEntryBackward() throws IOException {
        setup(10, 10);

        // RAW Nest Stream Check
        final RANestedInputStream nestedInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        for (int b = 10; b >= 1; b--) {
            nestedInputStream.getEntry(b - 1);

            final String full = "[" + StreamUtil.streamToString(nestedInputStream, false) + "]";

            final String expectedStart = "[B=" + b + ",S=1\n";
            final String expectedEnd = "B=" + b + ",S=10\n]";

            assertThat(full).withFailMessage(full + " did not start with " + expectedStart).startsWith(expectedStart);
            assertThat(full).withFailMessage(full + " did not end with " + expectedEnd).endsWith(expectedEnd);

            nestedInputStream.closeEntry();
        }
        nestedInputStream.close();
    }
}
