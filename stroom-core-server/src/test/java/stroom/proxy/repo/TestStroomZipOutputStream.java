package stroom.proxy.repo;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomZipOutputStream {
    private final static int TEST_SIZE = 100;

    @Test
    void testBigFile() throws IOException {
        final Path testFile = Files.createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(testFile);
        try {
            String uuid;
            OutputStream stream;

            for (int i = 0; i < TEST_SIZE; i++) {
                uuid = UUID.randomUUID().toString();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Meta).getFullName());
                stream.write("Header".getBytes(CharsetConstants.DEFAULT_CHARSET));
                stream.close();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Context).getFullName());
                stream.write("Context".getBytes(CharsetConstants.DEFAULT_CHARSET));
                stream.close();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Data).getFullName());
                stream.write("Data".getBytes(CharsetConstants.DEFAULT_CHARSET));
                stream.close();
            }

            stroomZipOutputStream.close();

            final StroomZipFile stroomZipFile = new StroomZipFile(testFile);

            assertThat(stroomZipFile.getStroomZipNameSet().getBaseNameSet().size()).isEqualTo(TEST_SIZE);

            stroomZipFile.close();
        } finally {
            assertThat(Files.deleteIfExists(testFile)).isTrue();
        }
    }

    @Test
    void testBlankProducesNothing() throws IOException {
        final Path testFile = Files.createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(testFile);
        stroomZipOutputStream.close();
        assertThat(Files.isRegularFile(testFile)).as("Not expecting to write a file").isFalse();
    }
}