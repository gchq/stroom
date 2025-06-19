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

import stroom.data.store.api.SegmentOutputStream;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestRASegmentStreamsNulls {

    @Test
    void testNoSegments(@TempDir final Path tempDir) throws IOException {
        final Path datFile = tempDir.resolve("test.bzg");
        final Path segFile = tempDir.resolve("test.seg.dat");

        final SegmentOutputStream segStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        segStream.write("test1".getBytes(StreamUtil.DEFAULT_CHARSET));

        segStream.close();

        assertThat(Files.isRegularFile(datFile)).isTrue();
        assertThat(Files.isRegularFile(segFile)).isFalse();

        RASegmentInputStream inputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true));

        assertThat(StreamUtil.streamToString(inputStream)).isEqualTo("test1");

        inputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true));

        inputStream.include(0);

        for (int i = 0; i < inputStream.count(); i++) {
            inputStream.include(i);
        }

        assertThat(StreamUtil.streamToString(inputStream)).isEqualTo("test1");

        Files.deleteIfExists(datFile);
        Files.deleteIfExists(segFile);
    }
}
