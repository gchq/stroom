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

package stroom.util.io;

import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.test.common.TestUtil;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static stroom.test.common.DirectorySnapshot.PathFlag.DIRECTORY;
import static stroom.test.common.DirectorySnapshot.PathFlag.REGULAR_FILE;

class TestFileUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFileUtil.class);

    @TempDir
    static Path tempDir;

    @Test
    void testMkdirs() throws InterruptedException {
        final Path rootDir = tempDir.resolve("TestFileUtil_" + System.currentTimeMillis());

        final Path[] dirArray = new Path[10];
        for (int i = 0; i < dirArray.length; i++) {
            dirArray[i] = buildDir(rootDir);
        }
        final AtomicBoolean exception = new AtomicBoolean(false);

        final SimpleExecutor simpleExecutor = new SimpleExecutor(4);
        for (int i = 0; i < 200; i++) {
            final int count = i;
            simpleExecutor.execute(() -> {
                try {
                    final Path dir = dirArray[count % dirArray.length];
                    FileUtil.mkdirs(dir);
                } catch (final RuntimeException e) {
                    e.printStackTrace();
                    exception.set(true);
                }
            });
        }
        simpleExecutor.waitForComplete();
        simpleExecutor.stop(false);

        assertThat(exception.get()).isFalse();

        FileUtil.deleteDir(rootDir);
    }

    private Path buildDir(final Path rootDir) {
        Path path = rootDir;
        for (int i = 0; i < 10; i++) {
            path = path.resolve(String.valueOf(RandomUtils.nextInt(0, 10)));
        }
        return path;
    }

    @Test
    void testMkdirsUnableToCreate() {
        try {
            FileUtil.mkdirs(Paths.get("/dev/null"));
            fail("Not expecting that this directory can be created");
        } catch (final RuntimeException e) {
            // Ignore.
        }
    }

    @Test
    void testIsEmptyDirectory_empty(@TempDir final Path tempDir) throws IOException {

        final boolean isEmpty = FileUtil.isEmptyDirectory(tempDir);
        assertThat(isEmpty)
                .isTrue();
    }

    @Test
    void testIsEmptyDirectory_containsFile(@TempDir final Path tempDir) throws IOException {

        final Path file = tempDir.resolve("my_file");
        Files.createFile(file);

        assertThat(file)
                .isRegularFile();

        final boolean isEmpty = FileUtil.isEmptyDirectory(tempDir);
        assertThat(isEmpty)
                .isFalse();
    }

    @Test
    void testIsEmptyDirectory_containsDir(@TempDir final Path tempDir) throws IOException {

        final Path dir = tempDir.resolve("my_sub_dir");
        Files.createDirectories(dir);

        assertThat(dir)
                .isDirectory();

        final boolean isEmpty = FileUtil.isEmptyDirectory(tempDir);
        assertThat(isEmpty)
                .isFalse();
    }

    @Test
    void testDeepListContents(@TempDir final Path tempDir) throws IOException {
        final Path dirA = Files.createDirectories(tempDir.resolve("a1/a2/a3/a4"));
        Files.writeString(Files.createFile(dirA.resolve("aFile1")), "aFile1");
        Files.writeString(Files.createFile(dirA.resolve("aFile2")), "aFile2");
        final Path dirB = Files.createDirectories(tempDir.resolve("b1/b2/b3/b4"));
        Files.writeString(Files.createFile(dirB.resolve("bFile1")), "bFile1");
        Files.writeString(Files.createFile(dirB.resolve("bFile2")), "bFile2");
        final Path dirC = Files.createDirectories(tempDir.resolve("c1/c2/c3/c4"));
        Files.writeString(Files.createFile(dirC.resolve("cFile1")), "cFile1");
        Files.writeString(Files.createFile(dirC.resolve("cFile2")), "cFile2");

        LOGGER.info("contents of {}:\n{}", tempDir, FileUtil.deepListContents(tempDir, false)
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        final LongAdder totalSize = new LongAdder();

        final Predicate<PathWithAttributes> isFilePredicate = pathWithAttributes -> {
            final BasicFileAttributes fileAttributes = pathWithAttributes.attributes();
            final boolean isRegularFile = fileAttributes.isRegularFile();
            if (isRegularFile) {
                totalSize.add(fileAttributes.size());
            }
            return isRegularFile;
        };

        final long fileCount = FileUtil.deepListContents(tempDir, false, isFilePredicate)
                .size();
        assertThat(fileCount)
                .isEqualTo(6);
        assertThat(totalSize)
                .hasValue(6L * "XFileX".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void testDeleteEmptyDirs_alreadyEmpty(@TempDir final Path tempDir) throws IOException {
        final int cnt = FileUtil.deleteEmptyDirs(tempDir);
        // Not deleted
        assertThat(tempDir)
                .isDirectory()
                .exists();

        assertThat(cnt)
                .isZero();
    }

    @Test
    void testDeleteEmptyDirs_justFiles(@TempDir final Path tempDir) throws IOException {
        TestUtil.createFiles(
                tempDir.resolve("file1"),
                tempDir.resolve("file2"),
                tempDir.resolve("file3"),
                tempDir.resolve("file4"),
                tempDir.resolve("file5"));
        final int cnt = FileUtil.deleteEmptyDirs(tempDir);
        // Not deleted
        assertThat(tempDir)
                .isDirectory()
                .exists();

        assertThat(cnt)
                .isZero();
    }

    @Test
    void testDeleteEmptyDirs(@TempDir final Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("emptyDir1/emptySubDir1/emptySubSubDir1"));
        Files.createDirectories(tempDir.resolve("emptyDir1/emptySubDir1/emptySubSubDir2"));
        Files.createDirectories(tempDir.resolve("emptyDir1/emptySubDir2"));
        Files.createDirectories(tempDir.resolve("nonEmptyDir1/emptySubDir3"));
        Files.createDirectories(tempDir.resolve("nonEmptyDir1/nonEmptySubDir2/emptySubSubDir3"));
        TestUtil.createFiles(
                tempDir.resolve("nonEmptyDir1/nonEmptySubDir2/nonEmptySubSubDir2/file1"),
                tempDir.resolve("nonEmptyDir1/nonEmptySubDir2/nonEmptySubSubDir2/file2"),
                tempDir.resolve("nonEmptyDir1/nonEmptySubDir2/file3"),
                tempDir.resolve("nonEmptyDir1/file4"),
                tempDir.resolve("nonEmptyDir2/file5"),
                tempDir.resolve("nonEmptyDir3/nonEmptySubDir3/file6"));
        Files.createDirectories(tempDir.resolve("emptyDir2"));
        Files.createDirectories(tempDir.resolve("emptyDir3"));

        Snapshot snapshot = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot of {}\n{}", tempDir, snapshot);

        final int cnt = FileUtil.deleteEmptyDirs(tempDir);

        snapshot = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot of {}\n{}", tempDir, snapshot);

        // Not deleted
        assertThat(tempDir)
                .isDirectory()
                .exists();

        assertThat(cnt)
                .isEqualTo(9);

        assertThat(snapshot.stream()
                .filter(pathSnapshot ->
                        pathSnapshot.flags().contains(REGULAR_FILE))
                .count())
                .isEqualTo(6);

        assertThat(snapshot.stream()
                .filter(pathSnapshot ->
                        pathSnapshot.flags().contains(DIRECTORY))
                .count())
                .isEqualTo(6);
    }
}
