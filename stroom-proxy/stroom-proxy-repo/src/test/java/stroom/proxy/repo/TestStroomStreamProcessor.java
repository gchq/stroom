package stroom.proxy.repo;


import org.junit.jupiter.api.Test;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamHandler;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.io.StreamUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

        final byte[] buffer = new byte[1000];

        final Path zipFile = Files.createTempFile("test", "zip");

        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
        final List<StroomStreamHandler> list = new ArrayList<>();
        list.add(StroomStreamHandlerUtil.createStroomStreamHandler(stroomZipOutputStream));
        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, list, buffer, "test");

        stroomStreamProcessor.process(byteArrayInputStream, "");

        stroomZipOutputStream.close();

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.Meta))).isEqualTo("StreamSize:11\nTEST:VALUE\n");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.Data))).isEqualTo("Sample Data");
        stroomZipFile.close();
    }

    @Test
    void testGZIPErrorSimple() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write("Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        gzipOutputStream.close();
        final byte[] fullData = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fullData, 0, fullData.length - 10);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "GZIP");

        final byte[] buffer = new byte[1000];

        final Path zipFile = Files.createTempFile("test", "zip");

        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
        final List<StroomStreamHandler> list = new ArrayList<>();
        list.add(StroomStreamHandlerUtil.createStroomStreamHandler(stroomZipOutputStream));
        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, list, buffer, "test");

        try {
            stroomStreamProcessor.process(byteArrayInputStream, "");
            stroomZipOutputStream.close();
            final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
            final String msg = StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.Meta));

            stroomZipFile.close();
            fail("expecting error but wrote - " + msg);
        } catch (final StroomStreamException ex) {
            assertThat(ex.getStroomStatusCode()).isEqualTo(StroomStatusCode.COMPRESSED_STREAM_INVALID);
        }
    }

    @Test
    void testZIPErrorSimple() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("001.hdr"));
        zipOutputStream.write("Feed:FEED".getBytes(StreamUtil.DEFAULT_CHARSET));
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry("001.dat"));
        zipOutputStream.write("Sample Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        final byte[] fullData = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fullData, 0, fullData.length / 2);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "ZIP");

        final byte[] buffer = new byte[1000];

        final Path zipFile = Files.createTempFile("test", "zip");

        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
        final List<StroomStreamHandler> list = new ArrayList<>();
        list.add(StroomStreamHandlerUtil.createStroomStreamHandler(stroomZipOutputStream));
        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, list, buffer, "test");

        try {
            stroomStreamProcessor.process(byteArrayInputStream, "");
            stroomZipOutputStream.close();

            final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
            final String msg = StreamUtil.streamToString(stroomZipFile.getInputStream("001", StroomZipFileType.Data));

            stroomZipFile.close();
            fail("expecting error but wrote - " + msg);
        } catch (final StroomStreamException ex) {
            assertThat(ex.getStroomStatusCode()).isEqualTo(StroomStatusCode.COMPRESSED_STREAM_INVALID);
        }
    }

    @Test
    void testZIPNoEntries() throws IOException {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("stroom/proxy/repo/BlankZip.zip");
        final byte[] fullData = StreamUtil.streamToBytes(inputStream);

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fullData, 0, fullData.length / 2);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "ZIP");

        final byte[] buffer = new byte[1000];

        final Path zipFile = Files.createTempFile("test", "zip");

        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
        final List<StroomStreamHandler> list = new ArrayList<>();
        list.add(StroomStreamHandlerUtil.createStroomStreamHandler(stroomZipOutputStream));
        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, list, buffer, "test");

        stroomStreamProcessor.process(byteArrayInputStream, "");
        stroomZipOutputStream.close();

        assertThat(Files.isRegularFile(zipFile)).as("Blank zips should get ignored").isFalse();
    }

    @Test
    void testOrder1() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (int i = 1; i <= 10; i++) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1.txt", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1.txt", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2.txt", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2.txt", "TEST:VALUE");

        assertMeta(stroomZipFile, "2.txt", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder2() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (int i = 1; i <= 10; i++) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();

            zipOutputStream.putNextEntry(new ZipEntry(i + ".meta"));
            zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1", "META:VALUE1");
        assertMeta(stroomZipFile, "1", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2", "META:VALUE2");
        assertMeta(stroomZipFile, "2", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder3() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (int i = 1; i <= 10; i++) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();

            zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
            zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1", "META:VALUE1");
        assertMeta(stroomZipFile, "1", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2", "META:VALUE2");
        assertMeta(stroomZipFile, "2", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder4() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (int i = 10; i > 0; i--) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
            zipOutputStream.write(("META:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();

            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1", "META:VALUE1");
        assertMeta(stroomZipFile, "1", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2", "META:VALUE2");
        assertMeta(stroomZipFile, "2", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder5_Pass() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
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
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");
        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1", "META:VALUE1");
        assertMeta(stroomZipFile, "1", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2", "META:VALUE2");
        assertMeta(stroomZipFile, "2", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder5_PassDueToHeaderBuffer() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
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
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");

        doCheckOrder(byteArrayOutputStream, zipFile);

        final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("1", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "1", "META:VALUE1");
        assertMeta(stroomZipFile, "1", "TEST:VALUE");
        assertThat(StreamUtil.streamToString(stroomZipFile.getInputStream("2", StroomZipFileType.Data))).isEqualTo("data");
        assertMeta(stroomZipFile, "2", "META:VALUE2");
        assertMeta(stroomZipFile, "2", "TEST:VALUE");

        stroomZipFile.close();
    }

    @Test
    void testOrder5_FailDueToHeaderBufferNotUsed() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (int i = 10; i > 0; i--) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".hdr"));
            zipOutputStream.write(("streamSize:1\nMETA:VALUE" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        }
        for (int i = 1; i <= 10; i++) {
            zipOutputStream.putNextEntry(new ZipEntry(i + ".txt"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();

        }
        zipOutputStream.close();

        final Path zipFile = Files.createTempFile("test", "zip");

        doCheckOrder(byteArrayOutputStream, zipFile, true);
    }

    void assertMeta(final StroomZipFile stroomZipFile, final String baseName, final String expectedMeta) throws IOException {
        final String fullMeta = StreamUtil.streamToString(stroomZipFile.getInputStream(baseName, StroomZipFileType.Meta));
        assertThat(fullMeta.contains(expectedMeta)).as("Expecting " + expectedMeta + " in " + fullMeta).isTrue();

    }

    private void doCheckOrder(final ByteArrayOutputStream byteArrayOutputStream, final Path zipFile)
            throws IOException {
        doCheckOrder(byteArrayOutputStream, zipFile, false);
    }

    private void doCheckOrder(final ByteArrayOutputStream byteArrayOutputStream, final Path zipFile, final boolean fail)
            throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("TEST", "VALUE");
        attributeMap.put("Compression", "ZIP");
        final byte[] buffer = new byte[1000];

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());

        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(zipFile);
        final List<StroomStreamHandler> list = new ArrayList<>();
        list.add(StroomStreamHandlerUtil.createStroomStreamHandler(stroomZipOutputStream));
        list.add(StroomStreamHandlerUtil.createStroomStreamOrderCheck());

        try {
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, list, buffer, "test");

            stroomStreamProcessor.process(byteArrayInputStream, "");
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
}
