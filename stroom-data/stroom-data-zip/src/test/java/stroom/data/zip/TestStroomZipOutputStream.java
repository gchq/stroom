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
