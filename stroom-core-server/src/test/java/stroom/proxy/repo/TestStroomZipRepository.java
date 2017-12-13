package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.MetaMap;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.stream.Stream;

public class TestStroomZipRepository {
    @Test
    public void testScan() throws IOException {
        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo1"));

        final StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, null, true, 100);

        final StroomZipOutputStream out1 = stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        out1.close();

        stroomZipRepository.setCount(10000000000L);

        final StroomZipOutputStream out2 = stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        out2.close();

        stroomZipRepository.finish();

        // Re open.
        final StroomZipRepository reopenStroomZipRepository = new StroomZipRepository(repoDir, null, false, 100);

        reopenStroomZipRepository.scanRepository((min, max) -> {
            Assert.assertTrue(1L == min);
            Assert.assertTrue(10000000001L == max);
        });

        final HashSet<Path> allZips = new HashSet<>();
        try (final Stream<Path> stream = reopenStroomZipRepository.walkZipFiles()) {
            stream.forEach(allZips::add);
        }

        Assert.assertEquals(2, allZips.size());
        try (final Stream<Path> stream = reopenStroomZipRepository.walkZipFiles()) {
            stream.forEach(p -> reopenStroomZipRepository.delete(new StroomZipFile(p)));
        }

        Assert.assertTrue(reopenStroomZipRepository.deleteIfEmpty());
        Assert.assertFalse("Deleted REPO", Files.isDirectory(Paths.get(repoDir)));
    }

    @Test
    public void testClean() throws IOException {
        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo2"));

        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, null, false, 10000);

        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        Assert.assertFalse(Files.isRegularFile(out1.getFile()));
        out1.close();
        Assert.assertTrue(Files.isRegularFile(out1.getFile()));

        final StroomZipOutputStreamImpl out2 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        Assert.assertFalse(Files.isRegularFile(out2.getFile()));
        Assert.assertTrue(Files.isRegularFile(out2.getLockFile()));

        // Leave open

        stroomZipRepository = new StroomZipRepository(repoDir, null, false, 1000);
        Assert.assertTrue("Expecting pucker file to be left", Files.isRegularFile(out1.getFile()));
        Assert.assertTrue("Expecting lock file to not be deleted", Files.isRegularFile(out2.getLockFile()));

        final StroomZipOutputStreamImpl out3 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out3, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        final Path lockFile3 = out3.getLockFile();
        Assert.assertTrue(Files.isRegularFile(lockFile3));

        stroomZipRepository.clean();
        Assert.assertTrue(Files.isRegularFile(lockFile3));

        try {
            Files.setLastModifiedTime(lockFile3, FileTime.fromMillis(System.currentTimeMillis() - (48 * 60 * 60 * 1000)));
        } catch (final Exception e) {
            Assert.fail("Unable to set LastModified");
        }
        stroomZipRepository.clean();
        Assert.assertFalse("Expecting old lock file to be deleted", Files.isRegularFile(lockFile3));
    }

    @Test
    public void testTemplatedFilename() throws IOException {
        // template should be case insensitive as far as key names go as the metamap is case insensitive
        final String repositoryFormat = "${id}_${FEED}_${key2}_${kEy1}_${Key3}";

        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo3"));
        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, repositoryFormat, false, 10000);

        MetaMap metaMap = new MetaMap();
        metaMap.put("feed", "myFeed");
        metaMap.put("key1", "myKey1");
        metaMap.put("key2", "myKey2");
        metaMap.put("key3", "myKey3");

        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream(metaMap);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        Assert.assertFalse(Files.isRegularFile(out1.getFile()));
        out1.close();
        Path zipFile = out1.getFile();
        Assert.assertTrue(Files.isRegularFile(zipFile));
        final String expectedFilename = "001_myFeed_myKey2_myKey1_myKey3.zip";
        Assert.assertEquals(expectedFilename, zipFile.getFileName().toString());

        stroomZipRepository.scanRepository((min, max) -> {
            Assert.assertTrue(1L == min);
            Assert.assertTrue(1L == max);
        });
    }

    @Test
    public void testInvalidDelimiter() throws IOException {
        final MetaMap metaMap = new MetaMap();
        metaMap.put("feed", "myFeed");
        metaMap.put("key1", "myKey1");

        final String repositoryFormat = "%{id}_${id}_${FEED}_${kEy1}";
        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo3"));

        final StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, repositoryFormat, false, 10000);
        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream(metaMap);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        Assert.assertFalse(Files.isRegularFile(out1.getFile()));
        out1.close();
        Path zipFile = out1.getFile();
        Assert.assertTrue(Files.isRegularFile(zipFile));
        final String expectedFilename = "__id__001_myFeed_myKey1.zip";
        Assert.assertEquals(expectedFilename, zipFile.getFileName().toString());
    }
}
