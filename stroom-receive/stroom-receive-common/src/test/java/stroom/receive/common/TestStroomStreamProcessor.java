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

package stroom.receive.common;


import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.util.io.StreamUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestStroomStreamProcessor {

    @Test
    void testSimple() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                "Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");

        final Path zipFile = Files.createTempFile("test", "zip");

        try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile)) {
            final StreamHandler handler = createStroomStreamHandler(stroomZipOutputStream);
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                    attributeMap,
                    handler,
                    new ProgressHandler("Test"));

            stroomStreamProcessor.processInputStream(byteArrayInputStream, "");
        }

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.META)))
                    .isEqualTo("StreamSize:11\nTEST:VALUE\n");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.DATA)))
                    .isEqualTo("Sample Data");
        }
    }

    @Test
    void testGZIPErrorSimple() throws IOException {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (final GzipCompressorOutputStream gzipOutputStream =
                    new GzipCompressorOutputStream(byteArrayOutputStream)) {
                gzipOutputStream.write("Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            final byte[] fullData = byteArrayOutputStream.toByteArray();

            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                    fullData, 0, fullData.length - 10);

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put("TEST", "VALUE");
            attributeMap.put("Compression", "GZIP");

            final Path zipFile = Files.createTempFile("test", "zip");

            try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile)) {
                final StreamHandler handler = createStroomStreamHandler(stroomZipOutputStream);
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap,
                        handler,
                        new ProgressHandler("Test"));

                stroomStreamProcessor.processInputStream(byteArrayInputStream, "");
            }

            try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
                final String msg = StreamUtil.streamToString(stroomZipFile.getInputStream(
                        "001", StroomZipFileType.META));
                fail("expecting error but wrote - " + msg);
            }
        } catch (final StroomStreamException e) {
            final StroomStreamStatus status = e.getStroomStreamStatus();
            assertThat(status.getStroomStatusCode())
                    .isEqualTo(StroomStatusCode.COMPRESSED_STREAM_INVALID);
        }
    }

    @Test
    void testZIPNoEntries() throws IOException {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "stroom/proxy/repo/BlankZip.zip");
        final byte[] fullData = StreamUtil.streamToBytes(inputStream);

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                fullData, 0, fullData.length / 2);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "ZIP");

        final Path zipFile = Files.createTempFile("test", "zip");

        try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile)) {
            final StreamHandler handler = createStroomStreamHandler(stroomZipOutputStream);
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                    attributeMap,
                    handler,
                    new ProgressHandler("Test"));

            stroomStreamProcessor.processInputStream(byteArrayInputStream, "");
        }

        assertThat(Files.isRegularFile(zipFile))
                .as("Blank zips should get ignored")
                .isFalse();
    }

    @Test
    void testOrder1() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            // txt is not a standard ext so we don't know if it is an extension or just a filename
            // with a dot in it. Meta files become 1.txt.meta
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1.txt", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1.txt", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2.txt", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2.txt", "TEST:VALUE");

            assertMeta(stroomZipFile, "2.txt", "TEST:VALUE");
        }
    }

    @Test
    void testOrder1b() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".dat"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");

            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    @Test
    void testOrder2() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();

                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".meta"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "META:VALUE1");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "META:VALUE2");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    @Test
    void testOrder3() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();

                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "META:VALUE1");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "META:VALUE2");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    @Test
    void testOrder4() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();

                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "META:VALUE1");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "META:VALUE2");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    @Test
    void testOrder5_Pass() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();

            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "META:VALUE1");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "META:VALUE2");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    @Test
    void testOrder5_PassDueToHeaderBuffer() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();

            }
        }

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "1", "META:VALUE1");
            assertMeta(stroomZipFile, "1", "TEST:VALUE");
            assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.DATA)))
                    .isEqualTo("data");
            assertMeta(stroomZipFile, "2", "META:VALUE2");
            assertMeta(stroomZipFile, "2", "TEST:VALUE");
        }
    }

    //    @Test
//    void testOrder5_FailDueToHeaderBufferNotUsed() throws IOException {
//        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
//        for (int i = 10; i > 0; i--) {
//            zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
//            zipOutputStream.write(("streamSize:1\nMETA:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
//            zipOutputStream.closeEntry();
//        }
//        for (int i = 1; i <= 10; i++) {
//            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
//            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
//            zipOutputStream.closeEntry();
//
//        }
//        zipOutputStream.close();
//
//        final Path zipFile = Files.createTempFile("test", "zip");
//
//        doCheckOrder(byteArrayOutputStream, zipFile, true);
//    }

    void assertMeta(final StroomZipFile stroomZipFile, final String baseName, final String expectedMeta)
            throws IOException {
        final String fullMeta = StreamUtil.streamToString(stroomZipFile.getInputStream(baseName,
                StroomZipFileType.META));
        assertThat(fullMeta.contains(expectedMeta))
                .as("Expecting " + expectedMeta + " in " + fullMeta)
                .isTrue();

    }

    private void doCheckOrder(final ByteArrayOutputStream byteArrayOutputStream, final Path zipFile)
            throws IOException {
        doCheckOrder(byteArrayOutputStream, zipFile, false);
    }

    private void doCheckOrder(final ByteArrayOutputStream byteArrayOutputStream,
                              final Path zipFile,
                              final boolean fail)
            throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "ZIP");

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile)) {
            final StreamHandler handler = createStroomStreamHandler(stroomZipOutputStream);

            try {
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap,
                        handler,
                        new ProgressHandler("Test"));

                stroomStreamProcessor.processInputStream(byteArrayInputStream, "");
                if (fail) {
                    fail("Expecting a fail");
                }
            } catch (final RuntimeException e) {
                if (!fail) {
                    throw e;
                }
            }
        }
    }

    public static StreamHandler createStroomStreamHandler(final StroomZipOutputStream stroomZipOutputStream) {
        return (entry, inputStream, progressHandler) -> {
            try (final OutputStream outputStream = stroomZipOutputStream.addEntry(entry)) {
                return StreamUtil.streamToStream(
                        inputStream,
                        outputStream,
                        new byte[StreamUtil.BUFFER_SIZE],
                        progressHandler);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
