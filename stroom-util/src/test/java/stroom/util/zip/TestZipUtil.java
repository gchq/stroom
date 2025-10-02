package stroom.util.zip;

import stroom.test.common.TestUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestZipUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipUtil.class);

    @Test
    void testUnZip(@TempDir final Path dir) throws IOException {
        final Path subDir = dir.resolve("sub_dir");
        final Path zipFile = dir.resolve("my.zip");
        FileUtil.ensureDirExists(subDir);
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(zipFile, StandardOpenOption.CREATE_NEW)))) {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("file1.txt"));
            try {
                zipOutputStream.write("hello world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }

            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("dir/file2.txt"));
            try {
                zipOutputStream.write("goodbye world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }

            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("./dir2/file3.txt"));
            try {
                zipOutputStream.write("goodbye world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        LOGGER.debug("zip entries:\n{}", String.join("\n", ZipUtil.pathList(zipFile)));

        ZipUtil.unzip(zipFile, subDir);

        dumpDirContents(dir);

        assertThat(subDir.resolve("file1.txt"))
                .isRegularFile();
        assertThat(subDir.resolve("dir/file2.txt"))
                .isRegularFile();
        assertThat(subDir.resolve("dir2/file3.txt"))
                .isRegularFile();
    }

    @Test
    void testUnZip_badFile(@TempDir final Path dir) throws IOException {
        final Path subDir = dir.resolve("sub_dir");
        final Path zipFile = dir.resolve("my.zip");
        FileUtil.ensureDirExists(subDir);
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(zipFile, StandardOpenOption.CREATE_NEW)))) {
            // relative path outside our destination dir
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("../bad.txt"));
            try {
                zipOutputStream.write("hello world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        Assertions.assertThatThrownBy(
                        () -> {
                            ZipUtil.unzip(zipFile, subDir);
                        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("would extract outside target directory");

        dumpDirContents(dir);
    }

    @Test
    void testUnZip_badFile2(@TempDir final Path dir) throws IOException {
        final Path subDir = dir.resolve("sub_dir");
        final Path zipFile = dir.resolve("my.zip");
        FileUtil.ensureDirExists(subDir);
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(zipFile, StandardOpenOption.CREATE_NEW)))) {
            // relative path outside our destination dir
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("foo/../../bad.txt"));
            try {
                zipOutputStream.write("hello world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        Assertions.assertThatThrownBy(
                        () -> {
                            ZipUtil.unzip(zipFile, subDir);
                        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("would extract outside target directory");

        dumpDirContents(dir);
    }

    @Test
    void testUnZip_badFile3(@TempDir final Path dir) throws IOException {
        final Path subDir = dir.resolve("sub_dir");
        final Path zipFile = dir.resolve("my.zip");
        FileUtil.ensureDirExists(subDir);
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(zipFile, StandardOpenOption.CREATE_NEW)))) {
            // Absolute path outside our destination dir
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("/tmp/bad.txt"));
            try {
                zipOutputStream.write("hello world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        Assertions.assertThatThrownBy(
                        () -> {
                            ZipUtil.unzip(zipFile, subDir);
                        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("would extract outside target directory");

        dumpDirContents(dir);
    }

    @TestFactory
    Stream<DynamicTest> testIsSafeRelativePath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        ZipUtil.isSafeZipPath(Path.of(testCase.getInput())))
                .withSimpleEqualityAssertion()
                .addCase("/", false)
                .addCase("/tmp/foo.txt", false)
                .addCase("../foo.txt", false)
                .addCase("..", false)
                .addCase("../..", false)
                .addCase("../../../foo", false)
                .addCase("a/b/../../../foo.txt", false)
                .addCase("foo.txt", true)
                .addCase("a/b/foo.txt", true)
                .addCase("a/b/../foo.txt", true)
                .addCase("a/b/../../foo.txt", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsSafeRelativePath2() {
        final Path destDir = Path.of("/c/d");
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        ZipUtil.isSafeZipPath(Path.of(testCase.getInput()), destDir))
                .withSimpleEqualityAssertion()
                .addCase("/", false)
                .addCase("/tmp/foo.txt", false)
                .addCase("../foo.txt", false)
                .addCase("..", false)
                .addCase("../..", false)
                .addCase("../../../foo", false)
                .addCase("a/b/../../../foo.txt", false)
                .addCase("/c/foo.txt", false)
                .addCase("foo.txt", true)
                .addCase("a/b/foo.txt", true)
                .addCase("a/b/../foo.txt", true)
                .addCase("a/b/../../foo.txt", true)
                .addCase("/c/d/foo.txt", true)
                .addCase("/c/d/../d/foo.txt", true)
                .build();
    }

    private static void dumpDirContents(final Path dir) {
        // TODO Uncomment in later versions
//        final Snapshot snapshot = DirectorySnapshot.of(dir);
//        LOGGER.debug("snapshot: \n{}", snapshot);
    }
}
