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
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestRASegmentStreamsByteSeeking {

    @Test
    void testByteSeeking(@TempDir final Path tempDir) throws IOException {
        assertThat(tempDir).isNotNull();
        try (final SegmentOutputStream os = new RASegmentOutputStream(
                new BlockGZIPOutputFile(tempDir.resolve("test.dat")),
                () -> Files.newOutputStream(tempDir.resolve("test.idx")))) {

            os.write("LINE ONE\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE TWO\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE THREE\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE FOUR\n".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.flush();
        }

        final UncompressedInputStream debug = new UncompressedInputStream(
                tempDir.resolve("test.idx"), true);

        RASegmentInputStream is = new RASegmentInputStream(
                new BlockGZIPInputFile(tempDir.resolve("test.dat")),
                new UncompressedInputStream(tempDir.resolve("test.idx"), true));

        assertThat(is.count()).isEqualTo(4);

        assertThat(is.byteOffset(0)).isEqualTo(0);
        assertThat(is.byteOffset(1)).isEqualTo(9);
        assertThat(is.byteOffset(2)).isEqualTo(18);
        assertThat(is.byteOffset(3)).isEqualTo(29);

        assertThat(is.segmentAtByteOffset(0)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(1)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(8)).isEqualTo(0);
        assertThat(is.segmentAtByteOffset(9)).isEqualTo(1);
        assertThat(is.segmentAtByteOffset(17)).isEqualTo(1);
        assertThat(is.segmentAtByteOffset(18)).isEqualTo(2);
        assertThat(is.segmentAtByteOffset(28)).isEqualTo(2);
        assertThat(is.segmentAtByteOffset(29)).isEqualTo(3);
        assertThat(is.segmentAtByteOffset(38)).isEqualTo(3);
        assertThat(is.segmentAtByteOffset(39)).isEqualTo(3);
        assertThat(is.segmentAtByteOffset(40)).isEqualTo(-1);
        assertThat(is.segmentAtByteOffset(9999)).isEqualTo(-1);

        debug.close();
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(tempDir.resolve("test.dat")),
                new UncompressedInputStream(tempDir.resolve("test.idx"), true), 5, 8);

        assertThat(StreamUtil.streamToString(is)).isEqualTo("ONE");
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(tempDir.resolve("test.dat")),
                new UncompressedInputStream(tempDir.resolve("test.idx"), true), 5, 39);

        is.include(0);
        is.include(2);
        assertThat(StreamUtil.streamToString(is)).isEqualTo("ONE\nLINE THREE\n");
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(tempDir.resolve("test.dat")),
                new UncompressedInputStream(tempDir.resolve("test.idx"), true), 0, 13);

        is.include(0);
        is.include(1);
        assertThat(StreamUtil.streamToString(is)).isEqualTo("LINE ONE\nLINE");
        is.close();
    }
}
