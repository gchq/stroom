/*
 * Copyright 2024 Crown Copyright
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
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gzipOutputStream.write("Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            gzipOutputStream.close();
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
    void testZIPErrorSimple() throws IOException {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
                zipOutputStream.putNextEntry(new ZipEntry("001.hdr"));
                zipOutputStream.write("Feed:FEED".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
                zipOutputStream.putNextEntry(new ZipEntry("001.dat"));
                zipOutputStream.write("Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
            }
            final byte[] fullData = byteArrayOutputStream.toByteArray();

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

            try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
                final String msg = StreamUtil.streamToString(stroomZipFile.getInputStream(
                        "001", StroomZipFileType.DATA));

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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".dat"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry(i + ".meta"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
            }
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();

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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (int i = 10; i > 0; i--) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
                zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();
            }
            for (int i = 1; i <= 10; i++) {
                zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
                zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
                zipOutputStream.closeEntry();

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
    void testProcessRequestHeader_blankMap() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final String remoteAddr = "192.168.0.1";
        final String remoteHost = "remoteAddr1.domain";
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost);

        // Blank header map
        final AttributeMap attributeMap = new AttributeMap();
        final StroomStreamProcessor streamProcessor = new StroomStreamProcessor(
                attributeMap, null, null);

        final Instant now = Instant.now();
        streamProcessor.processRequestHeader(mockRequest, now);

        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isNotNull();
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(now));
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .isEqualTo(DateUtil.createNormalDateTimeString(now));
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_ADDRESS))
                .isEqualTo(remoteAddr);
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_HOST))
                .isEqualTo(remoteHost);
    }

    @Test
    void testProcessRequestHeader_populatedMap() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final String remoteAddr = "192.168.0.1";
        final String remoteHost = "remoteAddr1.domain";
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost);

        final Instant now = Instant.now();
        final Instant prevTime = now.minus(1, ChronoUnit.DAYS);
        final String guid = UUID.randomUUID().toString();

        // Blank header map
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.GUID, guid);
        attributeMap.putDateTime(StandardHeaderArguments.RECEIVED_TIME, prevTime);
        attributeMap.putDateTime(StandardHeaderArguments.RECEIVED_TIME_HISTORY, prevTime);

        final StroomStreamProcessor streamProcessor = new StroomStreamProcessor(
                attributeMap, null, null);

        streamProcessor.processRequestHeader(mockRequest, now);

        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isEqualTo(guid);
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(now));
        Assertions.assertThat(attributeMap.getValueAsList(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .containsExactly(
                        DateUtil.createNormalDateTimeString(prevTime),
                        DateUtil.createNormalDateTimeString(now));
        // Not set because a guid was already in the map
        Assertions.assertThat(attributeMap.containsKey(StandardHeaderArguments.REMOTE_ADDRESS))
                .isFalse();
        Assertions.assertThat(attributeMap.containsKey(StandardHeaderArguments.REMOTE_HOST))
                .isFalse();
    }

    @Test
    void testProcessRequestHeader_populatedMap2() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final String remoteAddr = "192.168.0.1";
        final String remoteHost = "remoteAddr1.domain";
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost);

        final Instant now = Instant.now();
        final Instant prevTime = now.minus(1, ChronoUnit.DAYS);
        final String guid = UUID.randomUUID().toString();

        // Blank header map
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.GUID, guid);
        attributeMap.putDateTime(StandardHeaderArguments.RECEIVED_TIME, prevTime);

        // Empty RECEIVED_TIME_HISTORY this time

        final StroomStreamProcessor streamProcessor = new StroomStreamProcessor(
                attributeMap, null, null);

        streamProcessor.processRequestHeader(mockRequest, now);

        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isEqualTo(guid);
        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(now));
        Assertions.assertThat(attributeMap.getValueAsList(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .containsExactly(
                        DateUtil.createNormalDateTimeString(prevTime),
                        DateUtil.createNormalDateTimeString(now));
        Assertions.assertThat(attributeMap.containsKey(StandardHeaderArguments.REMOTE_ADDRESS))
                .isFalse();
        Assertions.assertThat(attributeMap.containsKey(StandardHeaderArguments.REMOTE_HOST))
                .isFalse();
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
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
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
        stroomZipOutputStream.close();
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
