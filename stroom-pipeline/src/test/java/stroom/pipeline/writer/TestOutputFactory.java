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

import stroom.pipeline.state.MetaDataHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

class TestOutputFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestOutputFactory.class);

    @Test
    void testGzipCompression(@TempDir final Path tempDir) throws IOException {
        final String content = "Hello world";
        final Path filePath = tempDir.resolve("file.gz");
        LOGGER.info("Using filePath: {}", filePath.toAbsolutePath().normalize());

        final OutputFactory outputFactory = new OutputFactory(new MetaDataHolder());
        outputFactory.setUseCompression(true);
        outputFactory.setCompressionMethod(CompressorStreamFactory.GZIP);

        try (final OutputStream outputStream = outputFactory.create(
                Files.newOutputStream(filePath)).getOutputStream()) {

            final PrintWriter printWriter = new PrintWriter(outputStream);
            printWriter.println(content);
            printWriter.flush();
            outputStream.flush();
        }

        final String text;
        try (final BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new GzipCompressorInputStream(Files.newInputStream(filePath))))) {

            text = bufferedReader.readLine();
            assertThat(text)
                    .isEqualTo(content);
        }

        LOGGER.info("done");
    }

    @Test
    void testZipCompression(@TempDir final Path tempDir) throws IOException {
        final String content1 = "Hello";
        final String content2 = "world";
        final Path filePath = tempDir.resolve("file.zip");
        LOGGER.info("Using filePath: {}", filePath.toAbsolutePath().normalize());

        final OutputFactory outputFactory = new OutputFactory(new MetaDataHolder());
        outputFactory.setUseCompression(true);
        outputFactory.setCompressionMethod(OutputFactory.COMPRESSION_ZIP);

        final Output output = outputFactory.create(Files.newOutputStream(filePath));
        try (final OutputStream outputStream = output.getOutputStream()) {
            final PrintWriter printWriter = new PrintWriter(outputStream);

            output.startZipEntry();
            printWriter.println(content1);
            printWriter.flush();
            outputStream.flush();
            output.endZipEntry();

            output.startZipEntry();
            printWriter.println(content2);
            printWriter.flush();
            outputStream.flush();
            output.endZipEntry();
        }

        final ZipFile zipFile = ZipFile.builder()
                .setFile(filePath.toFile())
                .get();
        final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

        ZipArchiveEntry zipArchiveEntry = entries.nextElement();
        InputStream inputStream = zipFile.getInputStream(zipArchiveEntry);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String text = bufferedReader.readLine();
        assertThat(text)
                .isEqualTo(content1);

        zipArchiveEntry = entries.nextElement();
        inputStream = zipFile.getInputStream(zipArchiveEntry);
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        text = bufferedReader.readLine();
        assertThat(text)
                .isEqualTo(content2);

        LOGGER.info("done");
    }

    @Test
    void testNoCompression(@TempDir final Path tempDir) throws IOException {
        final String content = "Hello world";
        final Path filePath = tempDir.resolve("file.txt");
        LOGGER.info("Using filePath: {}", filePath.toAbsolutePath().normalize());

        final OutputFactory outputFactory = new OutputFactory(new MetaDataHolder());
        outputFactory.setUseCompression(false);

        try (final OutputStream outputStream = outputFactory.create(
                Files.newOutputStream(filePath)).getOutputStream()) {

            final PrintWriter printWriter = new PrintWriter(outputStream);
            printWriter.println(content);
            printWriter.flush();
            outputStream.flush();
        }

        final String text;
        try (final BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(filePath)))) {
            text = bufferedReader.readLine();
            assertThat(text)
                    .isEqualTo(content);
        }
        LOGGER.info("done");
    }
}
