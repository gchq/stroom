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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.data.store.impl.fs.BlockGZIPInputFile;
import stroom.data.store.impl.fs.BlockGZIPOutputFile;
import stroom.data.store.impl.fs.UncompressedInputStream;
import stroom.data.store.impl.fs.serializable.RASegmentInputStream;
import stroom.data.store.impl.fs.serializable.RASegmentOutputStream;
import stroom.data.store.impl.fs.serializable.SegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRASegmentStreams extends StroomUnitTest {
    private static final String A = "A";
    private static final String B = "B";
    private static final String C = "C";
    private static final String D = "D";
    private static final int N1 = 1;
    private static final int N2 = 2;
    private static final int N3 = 3;
    private static final int N4 = 4;
    private static final int N5 = 5;
    private static final int N10 = 10;
    private static final int N1024 = 3;
    private Path dir;
    private RASegmentInputStream is;

    @Before
    public void setup() throws IOException {
        dir = getCurrentTestDir();
        try (OutputStream datStream = new BlockGZIPOutputFile(dir.resolve("test.dat"));
             final OutputStream idxStream = Files.newOutputStream(dir.resolve("test.idx"))) {
            try (SegmentOutputStream os = new RASegmentOutputStream(datStream, idxStream)) {
                os.write(A.getBytes(StreamUtil.DEFAULT_CHARSET));
                os.addSegment();
                os.write(B.getBytes(StreamUtil.DEFAULT_CHARSET));
                os.addSegment();
                os.write(C.getBytes(StreamUtil.DEFAULT_CHARSET));
                os.addSegment();
                os.write(D.getBytes(StreamUtil.DEFAULT_CHARSET));
            }
        }
    }

    @Test
    public void testBrokenBuffers() throws IOException {
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(
                Files.newOutputStream(dir.resolve("main.dat")), Files.newOutputStream(dir.resolve("main.idx")));

        for (int i = 0; i < 100; i++) {
            outputStream.write(("TEST STRING LINE " + i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
            outputStream.addSegment();
        }

        outputStream.close();

        final RASegmentInputStream inputStream = new RASegmentInputStream(
                new UncompressedInputStream(dir.resolve("main.dat"), true) {
                    @Override
                    public int read(final byte[] b, final int off, int len) throws IOException {
                        if (len > 3) {
                            len = 3;
                        }
                        return super.read(b, off, len);
                    }
                }, new UncompressedInputStream(dir.resolve("main.idx"), true));

        inputStream.include(98);
        inputStream.include(99);

        Assert.assertEquals("TEST STRING LINE 98\n" + "TEST STRING LINE 99\n", StreamUtil.streamToString(inputStream));
    }

    @Test
    public void testBrokenBuffers1() throws IOException {
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(
                Files.newOutputStream(dir.resolve("main.dat")), Files.newOutputStream(dir.resolve("main.idx")));

        for (int i = 0; i < 100; i++) {
            outputStream.write(("TEST STRING LINE " + i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
            outputStream.addSegment();
        }

        outputStream.close();

        final RASegmentInputStream inputStream = new RASegmentInputStream(
                new UncompressedInputStream(dir.resolve("main.dat"), true) {
                    @Override
                    public int read(final byte[] b, final int off, int len) throws IOException {
                        if (len > 3) {
                            len = 3;
                        }
                        return super.read(b, off, len);
                    }
                }, new UncompressedInputStream(dir.resolve("main.idx"), true));

        inputStream.exclude(98);
        inputStream.exclude(99);

        final String testStr = StreamUtil.streamToString(inputStream);
        Assert.assertTrue(testStr.endsWith("TEST STRING LINE 96\n" + "TEST STRING LINE 97\n"));
    }

    @Test
    public void testDelete() {
        FileUtil.deleteFile(dir.resolve("test.dat"));
        FileUtil.deleteFile(dir.resolve("test.idx"));
    }

    @Test
    public void testBits() throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        is.include(0);
        is.include(N1);
        is.include(N3);

        Assert.assertEquals(A + B + D, StreamUtil.streamToString(is));
    }

    @Test
    public void testEmptySegmentedStream() throws IOException {
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.addSegment();
            // 1
            os.addSegment();
            // 2
            os.addSegment();
            // 3
            os.write(A.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(B.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(C.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(D.getBytes(StreamUtil.DEFAULT_CHARSET));

            os.flush();
            os.close();
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(N4, is.count());
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(0);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(1);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(2);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(3);
        Assert.assertEquals(A + B + C + D, StreamUtil.streamToString(is, true));
    }

    @Test
    public void testEmptySegmentedStream2() throws IOException {
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.addSegment();
            // 1
            os.addSegment();
            // 2
            os.write(A.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(B.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(C.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(D.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            // 3
            os.addSegment();
            // 4
            os.flush();
            os.close();
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(N5, is.count());
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(0);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(1);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(2);
        Assert.assertEquals(A + B + C + D, StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(3);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(4);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));
    }

    @Test
    public void testNonSegmentedStream() throws IOException {
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            os.write(A.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(B.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(C.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(D.getBytes(StreamUtil.DEFAULT_CHARSET));

            os.flush();
            os.close();
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(N1, is.count());
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(A + B + C + D, streamToString(is, 0));
    }

    @Test
    public void testIncludeAll() throws IOException {
        testIncludeAll(N1024);
        testIncludeAll(N10);
        testIncludeAll(N1);
        testIncludeAll(0);
    }

    private void testIncludeAll(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(N4, is.count());
        is.includeAll();
        Assert.assertEquals(A + B + C + D, streamToString(is, bufferLength));
    }

    @Test
    public void testExcludeAll() throws IOException {
        testExcludeAll(N1024);
        testExcludeAll(N10);
        testExcludeAll(N1);
        testExcludeAll(0);
    }

    private void testExcludeAll(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(N4, is.count());
        is.excludeAll();
        Assert.assertEquals("", streamToString(is, bufferLength));
    }

    @Test
    public void testInclude() throws IOException {
        testInclude(N1024);
        testInclude(N10);
        testInclude(N1);
        testInclude(0);
    }

    private void testInclude(final int bufferLength) throws IOException {
        test(null, null, A + B + C + D, bufferLength);

        test(new long[]{0}, null, A, bufferLength);
        test(new long[]{N1}, null, B, bufferLength);
        test(new long[]{N2}, null, C, bufferLength);
        test(new long[]{N3}, null, D, bufferLength);
        test(new long[]{0, N1}, null, A + B, bufferLength);
        test(new long[]{N2, N3}, null, C + D, bufferLength);
        test(new long[]{0, N3}, null, A + D, bufferLength);
        test(new long[]{N1, N2}, null, B + C, bufferLength);
        test(new long[]{0, N2}, null, A + C, bufferLength);
        test(new long[]{1, N3}, null, B + D, bufferLength);
        test(new long[]{0, N1, N2, N3}, null, A + B + C + D, bufferLength);
    }

    @Test
    public void testExclude() throws IOException {
        testExclude(1024);
        testExclude(10);
        testExclude(1);
        testExclude(0);
    }

    private void testExclude(final int bufferLength) throws IOException {
        test(null, new long[]{0}, B + C + D, bufferLength);
        test(null, new long[]{1}, A + C + D, bufferLength);
        test(null, new long[]{2}, A + B + D, bufferLength);
        test(null, new long[]{3}, A + B + C, bufferLength);
        test(null, new long[]{0, 1}, C + D, bufferLength);
        test(null, new long[]{2, 3}, A + B, bufferLength);
        test(null, new long[]{0, 3}, B + C, bufferLength);
        test(null, new long[]{1, 2}, A + D, bufferLength);
        test(null, new long[]{0, 2}, B + D, bufferLength);
        test(null, new long[]{1, 3}, A + C, bufferLength);
        test(null, new long[]{0, 1, 2, 3}, "", bufferLength);
    }

    @Test
    public void testMixed() throws IOException {
        testMixed(1024);
        testMixed(10);
        testMixed(1);
        testMixed(0);
    }

    private void testMixed(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(4, is.count());
        is.exclude(1);
        is.include(2);
        Assert.assertEquals(C, streamToString(is, bufferLength));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(4, is.count());
        is.include(1);
        is.exclude(2);
        Assert.assertEquals(A + B + D, streamToString(is, bufferLength));
    }

    private void test(final long[] includes, final long[] excludes, final String expected, final int bufferLength)
            throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        Assert.assertEquals(4, is.count());

        if (includes != null) {
            for (int i = 0; i < includes.length; i++) {
                is.include(includes[i]);
            }
        }

        if (excludes != null) {
            for (int i = 0; i < excludes.length; i++) {
                is.exclude(excludes[i]);
            }
        }

        Assert.assertEquals(expected, streamToString(is, bufferLength));
    }

    private String streamToString(final InputStream inputStream, final int bufferLength) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len = 0;

        if (bufferLength == 0) {
            while ((len = inputStream.read()) != -1) {
                baos.write(len);
            }
        } else {
            final byte[] buffer = new byte[bufferLength];
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }

        inputStream.close();

        final String str = new String(baos.toByteArray(), StreamUtil.DEFAULT_CHARSET);
        return str;
    }
}
