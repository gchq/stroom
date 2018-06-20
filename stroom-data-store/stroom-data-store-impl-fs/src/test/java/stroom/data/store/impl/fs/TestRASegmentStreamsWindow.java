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
import stroom.data.store.api.SegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestRASegmentStreamsWindow {
    @Test
    void testFullWindowNoDataSegments() throws IOException {
        final Path dir = FileUtil.getTempDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        assertThat(is.count()).isEqualTo(6);

        assertThat(is.segmentAtByteOffset(0, true)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(0, false)).isEqualTo(5);

        is.include(3);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        assertThat(is.count()).isEqualTo(6);
        is.include(5);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");
    }

    @Test
    void testFullWindowNoDataSegments1() throws IOException {
        final Path dir = FileUtil.getTempDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        assertThat(is.count()).isEqualTo(6);
        // Start Segments
        assertThat(is.segmentAtByteOffset(0, true)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(1, true)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(3, true)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(4, true)).isEqualTo(1);
        assertThat(is.segmentAtByteOffset(5, true)).isEqualTo(5);
        assertThat(is.segmentAtByteOffset(8, true)).isEqualTo(5);

        // End Segments
        assertThat(is.segmentAtByteOffset(0, false)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(1, false)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(3, false)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(4, false)).isEqualTo(5);
        assertThat(is.segmentAtByteOffset(5, false)).isEqualTo(5);
        assertThat(is.segmentAtByteOffset(8, false)).isEqualTo(5);

        is.include(3);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        assertThat(is.count()).isEqualTo(6);
        is.include(5);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TEST");
    }

    @Test
    void testPartailWindowSegments1() throws IOException {
        final Path dir = FileUtil.getTempDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        // 0
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
        os.addSegment();
        // 1
        os.addSegment();
        // 2
        os.addSegment();
        // 3
        os.addSegment();
        // 4
        os.addSegment();
        // 5
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 3);

        assertThat(is.count()).isEqualTo(1);
        is.include(0);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TES");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

        assertThat(is.count()).isEqualTo(6);
        is.include(4);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

        assertThat(is.count()).isEqualTo(6);
        is.include(5);
        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 5);

        assertThat(is.count()).isEqualTo(6);
        is.include(5);

        assertThat(StreamUtil.streamToString(is, true)).isEqualTo("T");
    }

    @Test
    void testPartailWindowSegments2() throws IOException {
        final Path dir = FileUtil.getTempDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);

            assertThat(is.count()).isEqualTo(6);
            is.include(3);

            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("");

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);
            assertThat(is.count()).isEqualTo(6);
            is.include(0);
            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("ST");

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);
            assertThat(is.count()).isEqualTo(6);
            is.include(5);
            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TE");
        }
    }

    @Test
    void testFullWindowNoSegments() throws IOException {
        final Path dir = FileUtil.getTempDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

            assertThat(is.count()).isEqualTo(1);
            is.include(0);

            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TEST");

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true));

            assertThat(is.count()).isEqualTo(1);
            is.include(0);

            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TEST");
        }
    }

    @Test
    void testPartailWindowNoSegments() throws IOException {
        final Path dir = FileUtil.getTempDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 2);
            assertThat(is.count()).isEqualTo(1);
            is.include(0);
            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("TE");

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 1, 3);
            assertThat(is.count()).isEqualTo(1);
            is.include(0);
            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("ES");

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 4);
            assertThat(is.count()).isEqualTo(1);
            is.include(0);
            assertThat(StreamUtil.streamToString(is, true)).isEqualTo("ST");
        }
    }
}
