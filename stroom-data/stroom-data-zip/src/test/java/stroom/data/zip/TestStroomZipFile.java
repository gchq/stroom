package stroom.data.zip;


import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomZipFile {

    @Test
    void testRealZip1() throws IOException {
        final Path uniqueTestDir = Files.createTempDirectory("stroom");
        assertThat(Files.isDirectory(uniqueTestDir))
                .isTrue();
        final Path file = Files.createTempFile(uniqueTestDir, "TestStroomZipFile", ".zip");
        try {
            System.out.println(file.toAbsolutePath());

            try (final ZipArchiveOutputStream zipOutputStream =
                    ZipUtil.createOutputStream(Files.newOutputStream(file))) {
                zipOutputStream.putArchiveEntry(new ZipArchiveEntry("test/test.dat"));
                zipOutputStream.write("data".getBytes(CharsetConstants.DEFAULT_CHARSET));
                zipOutputStream.closeArchiveEntry();
            }

            try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
                final Collection<StroomZipEntryGroup> groups = stroomZipFile.getGroups();
                assertThat(groups.size()).isEqualTo(1);
                final StroomZipEntryGroup group = groups.stream().findFirst().orElseThrow();
                final StroomZipEntry entry = group.getByType(StroomZipFileType.DATA).orElseThrow();
                assertThat("test/test.dat").isEqualTo(entry.getFullName());

                assertThat(stroomZipFile.getInputStream("test/test", StroomZipFileType.DATA)).isNotNull();
                assertThat(stroomZipFile.getInputStream("test/test", StroomZipFileType.CONTEXT)).isNull();
            }
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void testRealZip2() throws IOException {
        final Path file = Files
                .createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        try {
            try (final ZipArchiveOutputStream zipOutputStream =
                    ZipUtil.createOutputStream(Files.newOutputStream(file))) {
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
                final List<String> baseNames = stroomZipFile.getBaseNames();
                assertThat(List.of("request")).isEqualTo(baseNames);

                assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.DATA)).isNotNull();
                assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.META)).isNotNull();
                assertThat(stroomZipFile.getInputStream("request", StroomZipFileType.CONTEXT)).isNotNull();
            }
        } finally {
            Files.delete(file);
        }
    }
}
