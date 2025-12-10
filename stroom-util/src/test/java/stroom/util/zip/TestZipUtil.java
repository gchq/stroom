/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestZipUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipUtil.class);

    @Test
    void testUnZip(@TempDir final Path dir) throws IOException {
        final Path subDir = dir.resolve("sub_dir");
        final Path zipFile = dir.resolve("my.zip");
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

            // Empty dir
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("dir3/"));
            try {
                zipOutputStream.write("goodbye world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        LOGGER.debug("zip entries:\n{}", String.join("\n", ZipUtil.pathList(zipFile)));

        // It will create the target dir and all child dirs
        Assertions.assertThat(subDir)
                        .doesNotExist();
        ZipUtil.unzip(zipFile, subDir);

        dumpDirContents(dir);

        assertThat(subDir.resolve("file1.txt"))
                .isRegularFile();
        assertThat(subDir.resolve("dir/file2.txt"))
                .isRegularFile();
        assertThat(subDir.resolve("dir2/file3.txt"))
                .isRegularFile();
        assertThat(subDir.resolve("dir3"))
                .isDirectory()
                .isEmptyDirectory();
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
                .hasMessageContaining("Zip slip");

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
                .hasMessageContaining("Zip slip");

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
                .hasMessageContaining("Zip slip");

        dumpDirContents(dir);
    }

    @Test
    void testForEachEntry(@TempDir final Path dir) throws IOException {
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
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("dir3/"));
            try {
                zipOutputStream.write("goodbye world".getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        }
        assertThat(zipFile)
                .isRegularFile();

        LOGGER.debug("zip entries:\n{}", String.join("\n", ZipUtil.pathList(zipFile)));

        final List<String> paths = new ArrayList<>();
        ZipUtil.forEachEntry(zipFile, (zipArchive, entry) -> {
            paths.add(entry.getName());
        });

        assertThat(paths)
                .containsExactly(
                        "file1.txt",
                        "dir/file2.txt",
                        "./dir2/file3.txt",
                        "dir3/");
    }

    @TestFactory
    Stream<DynamicTest> testIsSafeRelativePath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        ZipUtil.isSafeZipPath(Path.of(testCase.getInput())))
                .withSimpleEqualityAssertion()
                // Unsafe
                .addCase("/", false)
                .addCase("/foo", false)
                .addCase("/foo/", false)
                .addCase("/tmp/foo.txt", false)
                .addCase("../foo.txt", false)
                .addCase("..", false)
                .addCase("../..", false)
                .addCase("../../../foo", false)
                .addCase("a/b/../../../foo.txt", false)
                // Safe
                .addCase("foo.txt", true)
                .addCase("foo/", true)
                .addCase("a/b/foo/", true)
                .addCase("a/b/foo.txt", true)
                .addCase("a/b/../foo.txt", true)
                .addCase("a/b/../../foo.txt", true)
                .addCase("./a/b/../../foo.txt", true)
                .addCase("./././a/b/../../foo.txt", true)
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
                // Unsafe
                .addCase("/", false)
                .addCase("/tmp/foo.txt", false)
                .addCase("../foo.txt", false)
                .addCase("..", false)
                .addCase("../..", false)
                .addCase("../../../../../../../foo", false)
                .addCase("../../../../../../../foo/", false)
                .addCase("a/b/../../../foo.txt", false)
                .addCase("/c/foo.txt", false)
                // Safe
                .addCase("foo.txt", true)
                .addCase("a/b/foo.txt", true)
                .addCase("a/b/../foo.txt", true)
                .addCase("a/b/../../foo.txt", true)
                .addCase("/c/d/foo.txt", true)
                .addCase("/c/d/../d/foo.txt", true)
                .addCase("../../c/d/../d/a/b/foo.txt", true)
                .build();
    }

    private static void dumpDirContents(final Path dir) {
        // TODO Uncomment in later versions
//        final Snapshot snapshot = DirectorySnapshot.of(dir);
//        LOGGER.debug("snapshot: \n{}", snapshot);
    }
}
