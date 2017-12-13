package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class TestStroomZipOutputStream {
    private final static int TEST_SIZE = 100;

    @Test
    public void testBigFile() throws Exception {
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

            Assert.assertEquals(TEST_SIZE, stroomZipFile.getStroomZipNameSet().getBaseNameSet().size());

            stroomZipFile.close();
        } finally {
            Assert.assertTrue(Files.deleteIfExists(testFile));
        }
    }

    @Test
    public void testBlankProducesNothing() throws Exception {
        final Path testFile = Files.createTempFile(Files.createTempDirectory("stroom"), "TestStroomZipFile", ".zip");
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(testFile);
        stroomZipOutputStream.close();
        Assert.assertFalse("Not expecting to write a file", Files.isRegularFile(testFile));
    }
}