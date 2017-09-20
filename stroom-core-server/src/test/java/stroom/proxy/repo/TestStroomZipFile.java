package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.io.CloseableUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestStroomZipFile {
    @Test
    public void testRealZip1() throws IOException {
        Path testDir = TestUtil.getCurrentTestPath();
        Assert.assertTrue(Files.isDirectory(testDir));
        final Path uniqueTestDir = TestUtil.createUniqueTestDir(testDir);
        Assert.assertTrue(Files.isDirectory(uniqueTestDir));
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

            Assert.assertEquals(stroomZipFile.getStroomZipNameSet().getBaseNameSet(),
                    new HashSet<>(Collections.singleton("test/test.dat")));

            Assert.assertNotNull(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Data));
            Assert.assertNull(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Context));

        } finally {
            CloseableUtil.close(stroomZipFile);
            Files.delete(file);
        }
    }

    @Test
    public void testRealZip2() throws IOException {
        final Path file = Files.createTempFile(TestUtil.createUniqueTestDir(TestUtil.getCurrentTestPath()), "TestStroomZipFile", ".zip");
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

            Assert.assertEquals(stroomZipFile.getStroomZipNameSet().getBaseNameSet(),
                    new HashSet<>(Collections.singleton("request")));

            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Data));
            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Meta));
            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Context));

        } finally {
            CloseableUtil.close(stroomZipFile);
            Files.delete(file);
        }
    }
}
