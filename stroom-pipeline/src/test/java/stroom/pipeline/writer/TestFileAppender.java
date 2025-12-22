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

package stroom.pipeline.writer;


import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileAppender extends StroomUnitTest {

    private static final byte[] header = "header".getBytes(StandardCharsets.UTF_8);
    private static final byte[] footer = "footer".getBytes(StandardCharsets.UTF_8);
    private static final byte[] data = "__data__".getBytes(StandardCharsets.UTF_8);

    @Test
    void testZip(@TempDir final Path tempDir) throws IOException {
        final FileAppender provider = createZipAppender(tempDir);

        provider.startProcessing();
        final OutputStream outputStream = provider
                .getOutputStream(header, footer);
        outputStream.write(data);
        final LockedOutput lockedOutput = (LockedOutput) provider.getOutput();
        provider.endProcessing();

        assertThat(Files.exists(lockedOutput.lockFile)).isFalse();
        assertThat(Files.exists(lockedOutput.outFile)).isTrue();

        try (final ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(Files.newInputStream(
                lockedOutput.outFile))) {
            final ZipArchiveEntry entry1 = zipArchiveInputStream.getNextZipEntry();
            assertThat(entry1).isNotNull();
            assertThat(entry1.getName()).isEqualTo("0000000001.dat");
            final byte[] bytes = zipArchiveInputStream.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("header__data__footer");

            final ZipArchiveEntry entry2 = zipArchiveInputStream.getNextZipEntry();
            assertThat(entry2).isNull();
        }
    }

    private FileAppender createZipAppender(final Path tempDir) {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.zip";
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final FileAppender provider = new FileAppender(null, null, pathCreator);
        provider.setUseCompression(true);
        provider.setCompressionMethod("zip");
        provider.setOutputPaths(FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t1" + name);
        return provider;
    }

    @Test
    void testGZip(@TempDir final Path tempDir) throws IOException, CompressorException {
        final FileAppender provider = createGZipAppender(tempDir);

        provider.startProcessing();
        final OutputStream outputStream = provider
                .getOutputStream(header, footer);
        outputStream.write(data);
        final LockedOutput lockedOutput = (LockedOutput) provider.getOutput();
        provider.endProcessing();

        assertThat(Files.exists(lockedOutput.lockFile)).isFalse();
        assertThat(Files.exists(lockedOutput.outFile)).isTrue();

        try (final InputStream inputStream =
                new CompressorStreamFactory().createCompressorInputStream("gz",
                        Files.newInputStream(lockedOutput.outFile))) {
            final byte[] bytes = inputStream.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("header__data__footer");
        }
    }

    private FileAppender createGZipAppender(final Path tempDir) {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.gz";
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final FileAppender provider = new FileAppender(null, null, pathCreator);
        provider.setUseCompression(true);
        provider.setCompressionMethod("gz");
        provider.setOutputPaths(FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t1" + name);
        return provider;
    }
}
