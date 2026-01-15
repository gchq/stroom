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

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestBlockGZIPStreams {
    @Test
    void testSimple(@TempDir final Path tempDir) throws IOException {
        final Path testFile = Files.createTempFile(tempDir, "test", ".bgz");
        FileUtil.deleteFile(testFile);
        final OutputStream os = new BufferedOutputStream(new BlockGZIPOutputFile(testFile, 100));

        for (int i = 0; i < 1000; i++) {
            os.write((i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        os.close();

        try (final BlockGZIPInputStream bgzi = new BlockGZIPInputStream(Files.newInputStream(testFile));
             final LineNumberReader in = new LineNumberReader(
                     new InputStreamReader(bgzi, StreamUtil.DEFAULT_CHARSET))) {
            String line;
            int expected = 0;
            while ((line = in.readLine()) != null) {
                assertThat(Integer.parseInt(line)).isEqualTo(expected);
                expected++;
            }
            assertThat(expected).isEqualTo(1000);

            bgzi.close();
            FileUtil.deleteFile(testFile);

            assertThat(bgzi.getBlockCount()).withFailMessage("must have been at least 5 blocks read").isGreaterThan(5);
        }
    }
}
