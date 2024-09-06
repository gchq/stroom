package stroom.data.zip;


import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomZipFile {

    @Test
    void testRealZip1() throws IOException {
        Path uniqueTestDir = Files.createTempDirectory("stroom");
        assertThat(Files.isDirectory(uniqueTestDir))
                .isTrue();
        final Path file = Files.createTempFile(uniqueTestDir, "TestStroomZipFile", ".zip");
        System.out.println(file.toAbsolutePath());
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("test/test.dat"));
            zipOutputStream.write("data".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeArchiveEntry();
        }

        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
            assertThat(new HashSet<>(Collections.singleton("test/test.dat")))
                    .isEqualTo(stroomZipFile.getStroomZipNameSet().getBaseNameSet());

            assertThat(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Data)).isNotNull();
            assertThat(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Context)).isNull();
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testRealZip2() throws IOException {
        final Path file = Files.createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        try (final ZipArchiveOutputStream zipOutputStream = ZipUtil.createOutputStream(Files.newOutputStream(file))) {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("request.hdr"));
            zipOutputStream.write("header".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeArchiveEntry();
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("request.dat"));
            zipOutputStream.write("data".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeArchiveEntry();
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("request.ctx"));
            zipOutputStream.write("context".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeArchiveEntry();
        }

        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
            assertThat(new HashSet<>(Collections.singleton("request")))
                    .isEqualTo(stroomZipFile.getStroomZipNameSet().getBaseNameSet());

            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Data)).isNotNull();
            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Meta)).isNotNull();
            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Context)).isNotNull();
        } finally {
            Files.delete(file);
        }
    }
}
