package stroom.proxy.repo;

import stroom.data.zip.CharsetConstants;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.io.FileUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
class TestProxyRepo {

    @Inject
    private RepoSources proxyRepoSources;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @Test
    void testScan() throws IOException {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir,
                null,
                proxyRepoSources,
                100,
                0);

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, "test", null);
        try (final StroomZipOutputStream out1 = proxyRepo.getStroomZipOutputStream(attributeMap)) {
            StroomZipOutputStreamUtil.addSimpleEntry(
                    out1,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        }

        proxyRepo.setCount(10_000_000_000L);

        try (final StroomZipOutputStream out2 = proxyRepo.getStroomZipOutputStream(attributeMap)) {
            StroomZipOutputStreamUtil.addSimpleEntry(
                    out2,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        }

        // Re open.
        final ProxyRepo reopenProxyRepo = new ProxyRepo(() -> repoDir,
                null,
                null,
                100L,
                0L);

        reopenProxyRepo.scanRepository((min, max) -> {
            assertThat(1L == min)
                    .isTrue();
            assertThat(10_000_000_001L == max)
                    .isTrue();
        });

        final List<Path> allZips = reopenProxyRepo.listAllZipFiles();
        assertThat(allZips.size())
                .isEqualTo(2);
        try (final Stream<Path> stream = allZips.stream()) {
            stream.forEach(ErrorReceiverImpl::deleteFileAndErrors);
        }

        assertThat(reopenProxyRepo.deleteIfEmpty())
                .isTrue();
        assertThat(Files.isDirectory(repoDir))
                .as("Deleted REPO")
                .isFalse();
    }

    @Test
    void testClean() throws IOException {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo2");

        ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir, null, proxyRepoSources, 10_000, 0);

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, "test", null);
        StroomZipOutputStreamImpl out1;
        try (final StroomZipOutputStreamImpl out =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap)) {

            StroomZipOutputStreamUtil.addSimpleEntry(
                    out,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            assertThat(Files.isRegularFile(out.getFile()))
                    .isFalse();
            out1 = out;
        }
        assertThat(Files.isRegularFile(out1.getFile()))
                .isTrue();

        final StroomZipOutputStreamImpl out2 =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap);

        StroomZipOutputStreamUtil.addSimpleEntry(
                out2,
                StroomZipEntry.create("file", StroomZipFileType.DATA),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        assertThat(Files.isRegularFile(out2.getFile()))
                .isFalse();
        assertThat(Files.isRegularFile(out2.getLockFile()))
                .isTrue();

        // Leave open

        proxyRepo = new ProxyRepo(
                () -> repoDir, null, proxyRepoSources, 1_000, 0);
        assertThat(Files.isRegularFile(out1.getFile()))
                .as("Expecting pucker file to be left")
                .isTrue();
        assertThat(Files.isRegularFile(out2.getLockFile()))
                .as("Expecting lock file to not be deleted")
                .isTrue();

        final StroomZipOutputStreamImpl out3 =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap);
        StroomZipOutputStreamUtil.addSimpleEntry(
                out3,
                StroomZipEntry.create("file", StroomZipFileType.DATA),
                "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
        final Path lockFile3 = out3.getLockFile();
        assertThat(Files.isRegularFile(lockFile3))
                .isTrue();

        proxyRepo.clean(true);
        assertThat(Files.isRegularFile(lockFile3))
                .isTrue();

        try {
            Files.setLastModifiedTime(lockFile3,
                    FileTime.fromMillis(System.currentTimeMillis() - (48 * 60 * 60 * 1_000)));
        } catch (final RuntimeException e) {
            fail("Unable to set LastModified");
        }
        proxyRepo.clean(true);
        assertThat(Files.isRegularFile(lockFile3))
                .as("Expecting old lock file to be deleted")
                .isFalse();
    }

    @Test
    void testClean_emptyRepo() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo2");

        ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir, null, proxyRepoSources, 10_000, 0);

        assertThat(repoDir).exists();

        proxyRepo.clean(false);
        assertThat(repoDir).exists();

        proxyRepo.clean(true);
        assertThat(repoDir).doesNotExist();
    }

    @Test
    void testClean_tooNew() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo2");

        // big delay to prevent deletion
        int cleanDelayMs = (int) Duration.ofHours(1).toMillis();

        ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir, null, proxyRepoSources, 10_000, cleanDelayMs);

        assertThat(repoDir).exists();

        proxyRepo.clean(false);
        assertThat(repoDir).exists();

        // Dir is within the cleanDelayMs so it won't be deleted
        proxyRepo.clean(true);
        assertThat(repoDir).exists();
    }

    @Test
    void testTemplatedFilename() throws IOException {
        // template should be case insensitive as far as key names go as the attribute map is case insensitive
        final String repositoryFormat = "${id}_${FEED}_${key2}_${kEy1}_${Key3}";

        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo3");
        ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir,
                repositoryFormat,
                proxyRepoSources,
                10000,
                0);

        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("key1", "myKey1");
        attributeMap.put("key2", "myKey2");
        attributeMap.put("key3", "myKey3");

        StroomZipOutputStreamImpl out1;
        try (final StroomZipOutputStreamImpl out =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap)) {

            StroomZipOutputStreamUtil.addSimpleEntry(
                    out,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            assertThat(Files.isRegularFile(out.getFile()))
                    .isFalse();
            out1 = out;
        }
        Path zipFile = out1.getFile();
        assertThat(Files.isRegularFile(zipFile))
                .isTrue();
        final String expectedFilename = "001_myFeed_myKey2_myKey1_myKey3.zip";
        assertThat(zipFile.getFileName().toString())
                .isEqualTo(expectedFilename);

        proxyRepo.scanRepository((min, max) -> {
            assertThat(1L == min)
                    .isTrue();
            assertThat(1L == max)
                    .isTrue();
        });
    }

    @Test
    void testTemplatedFilenameWithDate() throws IOException {
        // template should be case insensitive as far as key names go as the metamap is case insensitive
        final String repositoryFormat = "${year}-${month}-${day}/${feed}/${id}";
        final String FEED_NAME = "myFeed";

        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo3");
        ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir,
                repositoryFormat,
                proxyRepoSources,
                10000,
                0);

        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", FEED_NAME);

        StroomZipOutputStreamImpl out1;
        try (final StroomZipOutputStreamImpl out =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap)) {

            StroomZipOutputStreamUtil.addSimpleEntry(
                    out,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            assertThat(Files.isRegularFile(out.getFile()))
                    .isFalse();
            out1 = out;
        }
        Path zipFile = out1.getFile();
        Path feedDir = zipFile.getParent();
        Path dateDir = feedDir.getParent();
        final String dateDirStr = dateDir.getFileName().toString();

        assertThat(Files.isRegularFile(zipFile))
                .isTrue();

        assertThat(feedDir.getFileName().toString())
                .isEqualTo(FEED_NAME);

        String pattern = "yyyy-MM-dd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String nowStr = simpleDateFormat.format(new Date());
        assertThat(dateDirStr)
                .isEqualTo(nowStr);
    }

    @Test
    void testInvalidDelimiter() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("key1", "myKey1");

        final String repositoryFormat = "%{id}_${id}_${FEED}_${kEy1}";
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo3");

        final ProxyRepo proxyRepo = new ProxyRepo(
                () -> repoDir,
                repositoryFormat,
                proxyRepoSources,
                10000,
                0);
        StroomZipOutputStreamImpl out1;
        try (final StroomZipOutputStreamImpl out =
                (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap)) {
            StroomZipOutputStreamUtil.addSimpleEntry(
                    out,
                    StroomZipEntry.create("file", StroomZipFileType.DATA),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            assertThat(Files.isRegularFile(out.getFile()))
                    .isFalse();
            out1 = out;
        }
        Path zipFile = out1.getFile();
        assertThat(Files.isRegularFile(zipFile))
                .isTrue();
        final String expectedFilename = "__id__001_myFeed_myKey1.zip";
        assertThat(zipFile.getFileName().toString())
                .isEqualTo(expectedFilename);

    }
}
