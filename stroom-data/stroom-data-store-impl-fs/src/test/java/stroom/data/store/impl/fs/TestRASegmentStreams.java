/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.store.api.SegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestRASegmentStreams {

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

    @BeforeEach
    void setup(@TempDir final Path tempDir) throws IOException {
        dir = tempDir;

        try (final OutputStream datStream = new BlockGZIPOutputFile(dir.resolve("test.dat"))) {
            try (final SegmentOutputStream os = new RASegmentOutputStream(datStream, () ->
                    Files.newOutputStream(dir.resolve("test.idx")))) {
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

    @AfterEach
    void teardown() {
        if (dir != null) {
            FileUtil.deleteContents(dir);
            FileUtil.delete(dir);
        }
    }

    @Test
    void testBrokenBuffers() throws IOException {
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(
                Files.newOutputStream(dir.resolve("main.dat")), () -> Files.newOutputStream(dir.resolve("main.idx")));

        for (int i = 0; i < 100; i++) {
            outputStream.write(("TEST STRING LINE " + i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
            outputStream.addSegment();
        }

        outputStream.close();

        final RASegmentInputStream inputStream = new RASegmentInputStream(
                new UncompressedInputStream(dir.resolve("main.dat"), true) {
                    @Override
                    public int read(@NotNull final byte[] b, final int off, int len) throws IOException {
                        if (len > 3) {
                            len = 3;
                        }
                        return super.read(b, off, len);
                    }
                }, new UncompressedInputStream(dir.resolve("main.idx"), true));

        inputStream.include(98);
        inputStream.include(99);

        assertThat(StreamUtil.streamToString(inputStream)).isEqualTo("TEST STRING LINE 98\n" + "TEST STRING LINE 99\n");
    }

    @Test
    void testBrokenBuffers1() throws IOException {
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(
                Files.newOutputStream(dir.resolve("main.dat")), () -> Files.newOutputStream(dir.resolve("main.idx")));

        for (int i = 0; i < 100; i++) {
            outputStream.write(("TEST STRING LINE " + i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
            outputStream.addSegment();
        }

        outputStream.close();

        final RASegmentInputStream inputStream = new RASegmentInputStream(
                new UncompressedInputStream(dir.resolve("main.dat"), true) {
                    @Override
                    public int read(@NotNull final byte[] b, final int off, int len) throws IOException {
                        if (len > 3) {
                            len = 3;
                        }
                        return super.read(b, off, len);
                    }
                }, new UncompressedInputStream(dir.resolve("main.idx"), true));

        inputStream.exclude(98);
        inputStream.exclude(99);

        final String testStr = StreamUtil.streamToString(inputStream);
        assertThat(testStr.endsWith("TEST STRING LINE 96\n" + "TEST STRING LINE 97\n")).isTrue();
    }

    @Test
    void testDelete() {
        FileUtil.deleteFile(dir.resolve("test.dat"));
        FileUtil.deleteFile(dir.resolve("test.idx"));
    }

    @Test
    void testBits() throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        is.include(0);
        is.include(N1);
        is.include(N3);

        assertThat(StreamUtil.streamToString(is)).isEqualTo(A + B + D);
    }

    @Test
    void testEmptySegmentedStream() throws IOException {
        try (final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                () -> Files.newOutputStream(dir.resolve("test.idx")))) {
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
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(N4);
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(0);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(1);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(2);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(3);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo(A + B + C + D);
    }

    @Test
    void testEmptySegmentedStream2() throws IOException {
        try (final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                () -> Files.newOutputStream(dir.resolve("test.idx")))) {
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
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(N5);
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(0);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(1);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(2);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo(A + B + C + D);

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(3);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        is.include(4);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");
    }

    @Test
    void testNonSegmentedStream() throws IOException {
        FileUtil.deleteContents(dir);

        try (final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                () -> Files.newOutputStream(dir.resolve("test.idx")))) {
            os.write(A.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(B.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(C.getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(D.getBytes(StreamUtil.DEFAULT_CHARSET));

            os.flush();
        }

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(N1);
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(streamToString(is, 0)).isEqualTo(A + B + C + D);
    }

    @Test
    void testIncludeAll() throws IOException {
        testIncludeAll(N1024);
        testIncludeAll(N10);
        testIncludeAll(N1);
        testIncludeAll(0);
    }

    private void testIncludeAll(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(N4);
        is.includeAll();
        assertThat(streamToString(is, bufferLength)).isEqualTo(A + B + C + D);
    }

    @Test
    void testExcludeAll() throws IOException {
        testExcludeAll(N1024);
        testExcludeAll(N10);
        testExcludeAll(N1);
        testExcludeAll(0);
    }

    private void testExcludeAll(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(N4);
        is.excludeAll();
        assertThat(streamToString(is, bufferLength)).isEqualTo("");
    }

    @Test
    void testInclude() throws IOException {
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
    void testExclude() throws IOException {
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
    void testMixed() throws IOException {
        testMixed(1024);
        testMixed(10);
        testMixed(1);
        testMixed(0);
    }

    private void testMixed(final int bufferLength) throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(4);
        is.exclude(1);
        is.include(2);
        assertThat(streamToString(is, bufferLength)).isEqualTo(C);

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(4);
        is.include(1);
        is.exclude(2);
        assertThat(streamToString(is, bufferLength)).isEqualTo(A + B + D);
    }

    private void test(final long[] includes, final long[] excludes, final String expected, final int bufferLength)
            throws IOException {
        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));
        assertThat(is.count()).isEqualTo(4);

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

        assertThat(streamToString(is, bufferLength)).isEqualTo(expected);
    }

    private String streamToString(final InputStream inputStream, final int bufferLength) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len;

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

        return new String(baos.toByteArray(), StreamUtil.DEFAULT_CHARSET);
    }
}
