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

package stroom.data.zip;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomZipOutputStream {

    private static final int TEST_SIZE = 100;

    @Test
    void testBigFile() throws IOException {
        final Path testFile = Files.createTempFile(
                Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        try {
            try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(testFile)) {
                String uuid;
                OutputStream stream;

                for (int i = 0; i < TEST_SIZE; i++) {
                    uuid = UUID.randomUUID().toString();
                    stream = stroomZipOutputStream.addEntry(
                            StroomZipEntry.createFromBaseName(uuid, StroomZipFileType.META).getFullName());
                    stream.write("Header".getBytes(CharsetConstants.DEFAULT_CHARSET));
                    stream.close();
                    stream = stroomZipOutputStream.addEntry(
                            StroomZipEntry.createFromBaseName(uuid, StroomZipFileType.CONTEXT).getFullName());
                    stream.write("Context".getBytes(CharsetConstants.DEFAULT_CHARSET));
                    stream.close();
                    stream = stroomZipOutputStream.addEntry(
                            StroomZipEntry.createFromBaseName(uuid, StroomZipFileType.DATA).getFullName());
                    stream.write("Data".getBytes(CharsetConstants.DEFAULT_CHARSET));
                    stream.close();
                }
            }

            try (final StroomZipFile stroomZipFile = new StroomZipFile(testFile)) {
                assertThat(stroomZipFile.getBaseNames().size())
                        .isEqualTo(TEST_SIZE);
            }
        } finally {
            assertThat(Files.deleteIfExists(testFile))
                    .isTrue();
        }
    }

    @Test
    void testBlankProducesNothing() throws IOException {
        final Path testFile = Files.createTempFile(
                Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(testFile)) {
            // Do nothing.
        }
        assertThat(Files.isRegularFile(testFile))
                .as("Not expecting to write a file")
                .isFalse();
    }
}
