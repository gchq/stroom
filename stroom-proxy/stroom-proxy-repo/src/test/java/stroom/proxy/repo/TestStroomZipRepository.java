package stroom.proxy.repo;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.zip.CharsetConstants;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.data.zip.StroomZipOutputStreamUtil;
import stroom.meta.shared.AttributeMap;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestStroomZipRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomZipRepository.class);

    @Test
    void testScan() throws IOException {
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
            assertThat(1L == min).isTrue();
            assertThat(10000000001L == max).isTrue();
        });

        final List<Path> allZips = reopenStroomZipRepository.listAllZipFiles();
        assertThat(allZips.size()).isEqualTo(2);
        try (final Stream<Path> stream = allZips.stream()) {
            stream.forEach(p -> reopenStroomZipRepository.delete(new StroomZipFile(p)));
        }

        assertThat(reopenStroomZipRepository.deleteIfEmpty()).isTrue();
        assertThat(Files.isDirectory(Paths.get(repoDir))).as("Deleted REPO").isFalse();
    }

    @Test
    void testClean() throws IOException {
        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo2"));

        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, null, false, 10000);

        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out1.getFile())).isFalse();
        out1.close();
        assertThat(Files.isRegularFile(out1.getFile())).isTrue();

        final StroomZipOutputStreamImpl out2 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out2.getFile())).isFalse();
        assertThat(Files.isRegularFile(out2.getLockFile())).isTrue();

        // Leave open

        stroomZipRepository = new StroomZipRepository(repoDir, null, false, 1000);
        assertThat(Files.isRegularFile(out1.getFile())).as("Expecting pucker file to be left").isTrue();
        assertThat(Files.isRegularFile(out2.getLockFile())).as("Expecting lock file to not be deleted").isTrue();

        final StroomZipOutputStreamImpl out3 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out3, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        final Path lockFile3 = out3.getLockFile();
        assertThat(Files.isRegularFile(lockFile3)).isTrue();

        stroomZipRepository.clean();
        assertThat(Files.isRegularFile(lockFile3)).isTrue();

        try {
            Files.setLastModifiedTime(lockFile3, FileTime.fromMillis(System.currentTimeMillis() - (48 * 60 * 60 * 1000)));
        } catch (final RuntimeException e) {
            fail("Unable to set LastModified");
        }
        stroomZipRepository.clean();
        assertThat(Files.isRegularFile(lockFile3)).as("Expecting old lock file to be deleted").isFalse();
    }

    @Test
    void testTemplatedFilename() throws IOException {
        // template should be case insensitive as far as key names go as the attribute map is case insensitive
        final String repositoryFormat = "${id}_${FEED}_${key2}_${kEy1}_${Key3}";

        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo3"));
        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, repositoryFormat, false, 10000);

        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("key1", "myKey1");
        attributeMap.put("key2", "myKey2");
        attributeMap.put("key3", "myKey3");

        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream(attributeMap);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out1.getFile())).isFalse();
        out1.close();
        Path zipFile = out1.getFile();
        assertThat(Files.isRegularFile(zipFile)).isTrue();
        final String expectedFilename = "001_myFeed_myKey2_myKey1_myKey3.zip";
        assertThat(zipFile.getFileName().toString()).isEqualTo(expectedFilename);

        stroomZipRepository.scanRepository((min, max) -> {
            assertThat(1L == min).isTrue();
            assertThat(1L == max).isTrue();
        });
    }

    @Test
    void testTemplatedFilenameWithDate() throws IOException {
        // template should be case insensitive as far as key names go as the metamap is case insensitive
        final String repositoryFormat = "${year}-${month}-${day}/${feed}/${id}";
        final String FEED_NAME = "myFeed";

        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo3"));
        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, repositoryFormat, false, 10000);

        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", FEED_NAME);

        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream(attributeMap);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out1.getFile())).isFalse();
        out1.close();
        Path zipFile = out1.getFile();
        Path feedDir = zipFile.getParent();
        Path dateDir = feedDir.getParent();
        String dateDirStr = dateDir.getFileName().toString();

        assertThat(Files.isRegularFile(zipFile)).isTrue();

        assertThat(feedDir.getFileName().toString()).isEqualTo(FEED_NAME);

        String pattern = "yyyy-MM-dd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String nowStr = simpleDateFormat.format(new Date());
        assertThat(dateDirStr).isEqualTo(nowStr);
    }

    @Test
    void testInvalidDelimiter() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("key1", "myKey1");

        final String repositoryFormat = "%{id}_${id}_${FEED}_${kEy1}";
        final String repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom").resolve("repo3"));

        final StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, repositoryFormat, false, 10000);
        final StroomZipOutputStreamImpl out1 = (StroomZipOutputStreamImpl) stroomZipRepository.getStroomZipOutputStream(attributeMap);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out1.getFile())).isFalse();
        out1.close();
        Path zipFile = out1.getFile();
        assertThat(Files.isRegularFile(zipFile)).isTrue();
        final String expectedFilename = "__id__001_myFeed_myKey1.zip";
        assertThat(zipFile.getFileName().toString()).isEqualTo(expectedFilename);
    }
}
