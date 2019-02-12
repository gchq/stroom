package stroom.data.zip;


import org.junit.jupiter.api.Test;
import stroom.util.io.CloseableUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomZipFile {
    @Test
    void testRealZip1() throws IOException {
        Path uniqueTestDir = Files.createTempDirectory("stroom");
        assertThat(Files.isDirectory(uniqueTestDir)).isTrue();
        final Path file = Files.createTempFile(uniqueTestDir, "TestStroomZipFile", ".zip");
        System.out.println(file.toAbsolutePath().toString());
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(Files.newOutputStream(file));

            zipOutputStream.putNextEntry(new ZipEntry("test/test.dat"));
            zipOutputStream.write("data".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        } finally {
            CloseableUtil.close(zipOutputStream);
        }

        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            assertThat(new HashSet<>(Collections.singleton("test/test.dat"))).isEqualTo(stroomZipFile.getStroomZipNameSet().getBaseNameSet());

            assertThat(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Data)).isNotNull();
            assertThat(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Context)).isNull();

        } finally {
            CloseableUtil.close(stroomZipFile);
            Files.delete(file);
        }
    }

    @Test
    void testRealZip2() throws IOException {
        final Path file = Files.createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(Files.newOutputStream(file));

            zipOutputStream.putNextEntry(new ZipEntry("request.hdr"));
            zipOutputStream.write("header".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("request.dat"));
            zipOutputStream.write("data".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("request.ctx"));
            zipOutputStream.write("context".getBytes(CharsetConstants.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        } finally {
            CloseableUtil.close(zipOutputStream);
        }

        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            assertThat(new HashSet<>(Collections.singleton("request"))).isEqualTo(stroomZipFile.getStroomZipNameSet().getBaseNameSet());

            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Data)).isNotNull();
            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Meta)).isNotNull();
            assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.Context)).isNotNull();

        } finally {
            CloseableUtil.close(stroomZipFile);
            Files.delete(file);
        }
    }
}
